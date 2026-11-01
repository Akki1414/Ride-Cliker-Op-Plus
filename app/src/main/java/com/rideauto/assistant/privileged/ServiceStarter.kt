package com.rideauto.assistant.privileged

import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

object ServiceStarter {

    const val SERVICE_PORT = 55778
    private const val TAG = "ServiceStarter"
    private val running = AtomicBoolean(true)

    @JvmStatic
    fun main(args: Array<String>) {
        Log.i(TAG, "Starting privileged service process...")
        try {
            Looper.prepare()
        } catch (ignored: Exception) {}

        val service = PrivilegedService(null)
        startSocketServer(service)
        Log.i(TAG, "Privileged service ready on port $SERVICE_PORT")
        Looper.loop()
    }

    private fun startSocketServer(service: PrivilegedService) {
        val thread = Thread {
            var serverSocket: ServerSocket? = null
            try {
                serverSocket = ServerSocket(SERVICE_PORT).apply {
                    reuseAddress = true
                }
                Log.i(TAG, "Socket server listening on port $SERVICE_PORT")
                while (running.get()) {
                    try {
                        val client = serverSocket.accept()
                        Thread {
                            handleClient(client, service)
                        }.start()
                    } catch (e: Exception) {
                        if (running.get()) {
                            Log.e(TAG, "Client accept error", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Socket server fatal error", e)
            } finally {
                try {
                    serverSocket?.close()
                } catch (ignored: Exception) {}
            }
        }
        thread.isDaemon = false
        thread.name = "PrivilegedSocketServer"
        thread.start()
    }

    private fun handleClient(client: Socket, service: PrivilegedService) {
        try {
            val inputStream = client.getInputStream()
            val outputStream = client.getOutputStream()
            val bufferedInput = BufferedInputStream(inputStream)
            bufferedInput.mark(8)
            val firstByte = bufferedInput.read()
            bufferedInput.reset()

            if (firstByte == -1) {
                client.close()
                return
            }

            // Plain text protocol (TELNET / Simple Line)
            if (firstByte in 32..126) {
                val reader = BufferedReader(InputStreamReader(bufferedInput, StandardCharsets.UTF_8))
                val writer = PrintWriter(outputStream, true)
                var line = reader.readLine()
                while (line != null) {
                    val trimmed = line.trim()
                    when {
                        trimmed == "PING" -> writer.println("PONG")
                        trimmed.startsWith("TAP ") -> {
                            val parts = trimmed.split(" ")
                            val x = parts.getOrNull(1)?.toIntOrNull() ?: 0
                            val y = parts.getOrNull(2)?.toIntOrNull() ?: 0
                            service.injectTap(x, y)
                            writer.println("OK")
                        }
                        trimmed.startsWith("SWIPE ") -> {
                            val parts = trimmed.split(" ")
                            val x1 = parts.getOrNull(1)?.toIntOrNull() ?: 0
                            val y1 = parts.getOrNull(2)?.toIntOrNull() ?: 0
                            val x2 = parts.getOrNull(3)?.toIntOrNull() ?: 0
                            val y2 = parts.getOrNull(4)?.toIntOrNull() ?: 0
                            val dur = parts.getOrNull(5)?.toIntOrNull() ?: 350
                            service.injectSwipe(x1, y1, x2, y2, dur)
                            writer.println("OK")
                        }
                        trimmed.startsWith("EXEC ") -> {
                            val cmd = trimmed.substring(5).trim()
                            writer.println(service.executeShellCommandWithOutput(cmd))
                        }
                        trimmed.startsWith("{") -> {
                            writer.println(dispatchRequest(service, trimmed))
                        }
                        else -> writer.println("OK")
                    }
                    if (!client.isClosed) {
                        line = try { reader.readLine() } catch (e: Exception) { null }
                    } else break
                }
            } else {
                // Binary length-prefixed JSON Protocol
                val dataIn = DataInputStream(bufferedInput)
                val dataOut = DataOutputStream(outputStream)
                while (running.get() && !client.isClosed) {
                    try {
                        val length = dataIn.readInt()
                        if (length in 1..16777216) {
                            val buffer = ByteArray(length)
                            dataIn.readFully(buffer)
                            val response = dispatchRequest(service, String(buffer, StandardCharsets.UTF_8))
                            val respBytes = response.toByteArray(StandardCharsets.UTF_8)
                            dataOut.writeInt(respBytes.size)
                            dataOut.write(respBytes)
                            dataOut.flush()
                        } else {
                            break
                        }
                    } catch (e: EOFException) {
                        break
                    } catch (e: Exception) {
                        Log.e(TAG, "Request handling error", e)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client session error", e)
        } finally {
            try { client.close() } catch (ignored: Exception) {}
        }
    }

    private fun dispatchRequest(service: PrivilegedService, requestStr: String): String {
        return try {
            val json = JSONObject(requestStr)
            val id = json.optInt("id", 0)
            val method = json.optString("method", "")
            val args = json.optJSONArray("args") ?: JSONArray()
            val result = dispatchMethod(service, method, args)

            val resp = JSONObject()
            resp.put("id", id)
            resp.put("result", result ?: JSONObject.NULL)
            resp.toString()
        } catch (e: Exception) {
            val errResp = JSONObject()
            errResp.put("error", e.message ?: "Unknown error")
            errResp.toString()
        }
    }

    private fun dispatchMethod(service: PrivilegedService, method: String, args: JSONArray): Any? {
        return when (method) {
            "ping" -> service.ping()
            "getVersion" -> service.version
            "injectTap" -> {
                service.injectTap(args.getInt(0), args.getInt(1))
                true
            }
            "injectSwipe" -> {
                service.injectSwipe(args.getInt(0), args.getInt(1), args.getInt(2), args.getInt(3), args.getInt(4))
                true
            }
            "injectKeyEvent" -> {
                service.injectKeyEvent(args.getInt(0))
                true
            }
            "executeShellCommand" -> service.executeShellCommand(args.getString(0))
            "executeShellCommandWithOutput" -> service.executeShellCommandWithOutput(args.getString(0))
            "grantAccessibilityPermission" -> {
                service.grantAccessibilityPermission(args.getString(0))
                true
            }
            "enableAccessibilityService" -> {
                service.enableAccessibilityService(args.getString(0))
                true
            }
            "disableBatteryOptimization" -> {
                service.disableBatteryOptimization(args.getString(0))
                true
            }
            "whitelistAppFromDoze" -> {
                service.whitelistAppFromDoze(args.getString(0))
                true
            }
            "keepAwake" -> {
                service.keepAwake(args.getBoolean(0))
                true
            }
            "putSecureSetting" -> {
                service.putSecureSetting(args.getString(0), args.getString(1))
                true
            }
            "putGlobalSetting" -> {
                service.putGlobalSetting(args.getString(0), args.getString(1))
                true
            }
            "putSystemSetting" -> {
                service.putSystemSetting(args.getString(0), args.getString(1))
                true
            }
            "bringAppToFront" -> {
                service.bringAppToFront(args.getString(0))
                true
            }
            "simulateBackButton" -> {
                service.simulateBackButton()
                true
            }
            "simulateHomeButton" -> {
                service.simulateHomeButton()
                true
            }
            "openRecentApps" -> {
                service.openRecentApps()
                true
            }
            "shutdown" -> {
                running.set(false)
                true
            }
            else -> null
        }
    }
}
