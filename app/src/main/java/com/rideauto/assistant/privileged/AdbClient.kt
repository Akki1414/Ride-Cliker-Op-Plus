package com.rideauto.assistant.privileged

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

object AdbClient {

    private const val TAG = "AdbClient"
    private const val A_CNXN = 0x4e584e43
    private const val A_OPEN = 0x4e45504f
    private const val A_OKAY = 0x59414b4f
    private const val A_CLSE = 0x45534c43
    private const val A_WRTE = 0x45545257
    private const val A_AUTH = 0x48545541
    private const val A_VERSION = 0x01000000
    private const val MAX_PAYLOAD = 4096

    data class AdbMessage(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val dataLength: Int,
        val checksum: Int,
        val magic: Int,
        val payload: ByteArray = ByteArray(0)
    )

    fun executeCommand(host: String = "127.0.0.1", port: Int, command: String, timeoutMs: Int = 5000): String {
        return try {
            val socket = Socket().apply {
                connect(InetSocketAddress(host, port), timeoutMs)
                soTimeout = timeoutMs
            }
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            // 1. Send CNXN
            val banner = "host::\u0000".toByteArray(StandardCharsets.UTF_8)
            sendMessage(output, A_CNXN, A_VERSION, MAX_PAYLOAD, banner)

            // 2. Read Response
            val response = readMessage(input)
            if (response.command != A_CNXN && response.command != A_AUTH) {
                socket.close()
                return "Connection failed: command=${response.command}"
            }

            // 3. Open shell stream
            val localId = 1
            val destination = "shell:$command\u0000".toByteArray(StandardCharsets.UTF_8)
            sendMessage(output, A_OPEN, localId, 0, destination)

            val openResponse = readMessage(input)
            if (openResponse.command != A_OKAY) {
                socket.close()
                return "Open shell failed: command=${openResponse.command}"
            }

            val remoteId = openResponse.arg0
            val sb = StringBuilder()

            // 4. Read output until stream closes
            while (!socket.isClosed) {
                val msg = readMessage(input)
                when (msg.command) {
                    A_WRTE -> {
                        sb.append(String(msg.payload, StandardCharsets.UTF_8))
                        sendMessage(output, A_OKAY, localId, remoteId, ByteArray(0))
                    }
                    A_CLSE -> {
                        sendMessage(output, A_CLSE, localId, remoteId, ByteArray(0))
                        break
                    }
                }
            }
            socket.close()
            sb.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "ADB command error: ${e.message}")
            "Error: ${e.message}"
        }
    }

    private fun sendMessage(output: OutputStream, command: Int, arg0: Int, arg1: Int, payload: ByteArray) {
        val header = ByteBuffer.allocate(24).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            putInt(command)
            putInt(arg0)
            putInt(arg1)
            putInt(payload.size)
            putInt(calculateChecksum(payload))
            putInt(command xor -0x1)
        }
        output.write(header.array())
        if (payload.isNotEmpty()) {
            output.write(payload)
        }
        output.flush()
    }

    private fun readMessage(input: InputStream): AdbMessage {
        val headerBuffer = ByteArray(24)
        var read = 0
        while (read < 24) {
            val r = input.read(headerBuffer, read, 24 - read)
            if (r == -1) break
            read += r
        }
        val buffer = ByteBuffer.wrap(headerBuffer).order(ByteOrder.LITTLE_ENDIAN)
        val command = buffer.int
        val arg0 = buffer.int
        val arg1 = buffer.int
        val dataLength = buffer.int
        val checksum = buffer.int
        val magic = buffer.int

        val payload = if (dataLength in 1..MAX_PAYLOAD) {
            val p = ByteArray(dataLength)
            var pRead = 0
            while (pRead < dataLength) {
                val r = input.read(p, pRead, dataLength - pRead)
                if (r == -1) break
                pRead += r
            }
            p
        } else {
            ByteArray(0)
        }

        return AdbMessage(command, arg0, arg1, dataLength, checksum, magic, payload)
    }

    private fun calculateChecksum(data: ByteArray): Int {
        var sum = 0
        for (b in data) {
            sum += b.toInt() and 0xFF
        }
        return sum
    }
}
