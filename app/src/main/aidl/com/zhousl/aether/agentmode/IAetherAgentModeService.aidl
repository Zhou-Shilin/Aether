package com.zhousl.aether.agentmode;

import android.os.ParcelFileDescriptor;
import android.view.Surface;

/**
 * Agent Mode service that runs as shell (Shizuku) or root, never in Aether's own user. Every call
 * that acts on "the user" (resolving and launching packages, writing the clipboard) must carry the
 * user id of the Aether process that is being served, because the service process itself belongs to
 * user 0 and Shizuku may even reuse one user service for every user.
 */
interface IAetherAgentModeService {
    int createDisplay(String name, int width, int height, int density, in Surface surface) = 1;
    int createOwnedDisplay(String name, int width, int height, int density) = 2;
    void attachPreviewSurface(int displayId, in Surface surface) = 3;
    void detachPreviewSurface(int displayId) = 4;
    void releaseDisplay(int displayId) = 5;
    void launchPackage(String packageName, int displayId, int userId) = 6;
    void runInputCommand(String command) = 7;
    void tap(int displayId, int x, int y) = 8;
    void swipe(int displayId, int x1, int y1, int x2, int y2, int durationMs) = 9;
    void key(int displayId, String keyCode) = 10;
    String text(int displayId, String text, int userId) = 11;
    String captureImageToFd(int displayId, in ParcelFileDescriptor output, int maxEdge, int quality) = 12;
    String listDisplaysJson() = 13;
    String listInstalledAppsJson(int userId) = 14;
    String focusedWindowJson(int displayId) = 15;
    void destroy() = 16777114;
}
