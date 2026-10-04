package com.zhousl.aether.agentmode;

import android.os.ParcelFileDescriptor;
import android.view.Surface;

interface IAetherAgentModeService {
    int createDisplay(String name, int width, int height, int density, in Surface surface) = 1;
    int createOwnedDisplay(String name, int width, int height, int density) = 2;
    void attachPreviewSurface(int displayId, in Surface surface) = 3;
    void detachPreviewSurface(int displayId) = 4;
    void releaseDisplay(int displayId) = 5;
    void launchPackage(String packageName, int displayId) = 6;
    void runInputCommand(String command) = 7;
    void tap(int displayId, int x, int y) = 8;
    void swipe(int displayId, int x1, int y1, int x2, int y2, int durationMs) = 9;
    void key(int displayId, String keyCode) = 10;
    String text(int displayId, String text) = 11;
    void captureImageToFd(int displayId, in ParcelFileDescriptor output, int maxEdge, int quality) = 12;
    String listDisplaysJson() = 13;
    String listInstalledAppsJson() = 14;
    String focusedWindowJson(int displayId) = 15;
    // The service process exits when [client] dies, so it never outlives the Aether process.
    void linkClient(IBinder client) = 16;
    void destroy() = 16777114;
}
