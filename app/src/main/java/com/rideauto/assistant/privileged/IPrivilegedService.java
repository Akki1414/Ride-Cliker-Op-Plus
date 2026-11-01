package com.rideauto.assistant.privileged;

import android.app.ActivityManager;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

public interface IPrivilegedService extends IInterface {
    String DESCRIPTOR = "com.rideauto.assistant.privileged.IPrivilegedService";

    int getVersion() throws RemoteException;
    boolean ping() throws RemoteException;
    void grantAccessibilityPermission(String packageName) throws RemoteException;
    void enableAccessibilityService(String componentName) throws RemoteException;
    List<AccessibilityNodeInfo> getWindowNodes(String packageName) throws RemoteException;
    void forceStopApp(String packageName) throws RemoteException;
    void startAppActivity(String packageName, String activityName) throws RemoteException;
    void clearAppData(String packageName) throws RemoteException;
    List<ActivityManager.RunningAppProcessInfo> getRunningProcesses() throws RemoteException;
    void injectTap(int x, int y) throws RemoteException;
    void injectSwipe(int x1, int y1, int x2, int y2, int duration) throws RemoteException;
    void injectKeyEvent(int keyCode) throws RemoteException;
    void postSystemNotification(String title, String message) throws RemoteException;
    void clearNotification(String packageName, int id) throws RemoteException;
    void disableBatteryOptimization(String packageName) throws RemoteException;
    void whitelistAppFromDoze(String packageName) throws RemoteException;
    void keepAwake(boolean enable) throws RemoteException;
    void putSecureSetting(String key, String value) throws RemoteException;
    void putGlobalSetting(String key, String value) throws RemoteException;
    void putSystemSetting(String key, String value) throws RemoteException;
    void grantPermission(String packageName, String permission) throws RemoteException;
    void revokePermission(String packageName, String permission) throws RemoteException;
    void setComponentEnabled(String componentName, boolean enabled) throws RemoteException;
    Bitmap captureScreen() throws RemoteException;
    Bitmap capturePackageWindow(String packageName) throws RemoteException;
    String executeShellCommand(String command) throws RemoteException;
    String executeShellCommandWithOutput(String command) throws RemoteException;
    void bringAppToFront(String packageName) throws RemoteException;
    void simulateBackButton() throws RemoteException;
    void simulateHomeButton() throws RemoteException;
    void openRecentApps() throws RemoteException;
    void shutdown() throws RemoteException;

    abstract class Stub extends Binder implements IPrivilegedService {
        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IPrivilegedService asInterface(IBinder obj) {
            if (obj == null) return null;
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin instanceof IPrivilegedService) {
                return (IPrivilegedService) iin;
            }
            return new Proxy(obj);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        private static class Proxy implements IPrivilegedService {
            private final IBinder mRemote;

            Proxy(IBinder remote) {
                mRemote = remote;
            }

            @Override
            public IBinder asBinder() {
                return mRemote;
            }

            @Override public int getVersion() { return 1; }
            @Override public boolean ping() { return true; }
            @Override public void grantAccessibilityPermission(String packageName) {}
            @Override public void enableAccessibilityService(String componentName) {}
            @Override public List<AccessibilityNodeInfo> getWindowNodes(String packageName) { return new ArrayList<>(); }
            @Override public void forceStopApp(String packageName) {}
            @Override public void startAppActivity(String packageName, String activityName) {}
            @Override public void clearAppData(String packageName) {}
            @Override public List<ActivityManager.RunningAppProcessInfo> getRunningProcesses() { return new ArrayList<>(); }
            @Override public void injectTap(int x, int y) {}
            @Override public void injectSwipe(int x1, int y1, int x2, int y2, int duration) {}
            @Override public void injectKeyEvent(int keyCode) {}
            @Override public void postSystemNotification(String title, String message) {}
            @Override public void clearNotification(String packageName, int id) {}
            @Override public void disableBatteryOptimization(String packageName) {}
            @Override public void whitelistAppFromDoze(String packageName) {}
            @Override public void keepAwake(boolean enable) {}
            @Override public void putSecureSetting(String key, String value) {}
            @Override public void putGlobalSetting(String key, String value) {}
            @Override public void putSystemSetting(String key, String value) {}
            @Override public void grantPermission(String packageName, String permission) {}
            @Override public void revokePermission(String packageName, String permission) {}
            @Override public void setComponentEnabled(String componentName, boolean enabled) {}
            @Override public Bitmap captureScreen() { return null; }
            @Override public Bitmap capturePackageWindow(String packageName) { return null; }
            @Override public String executeShellCommand(String command) { return ""; }
            @Override public String executeShellCommandWithOutput(String command) { return ""; }
            @Override public void bringAppToFront(String packageName) {}
            @Override public void simulateBackButton() {}
            @Override public void simulateHomeButton() {}
            @Override public void openRecentApps() {}
            @Override public void shutdown() {}
        }
    }
}
