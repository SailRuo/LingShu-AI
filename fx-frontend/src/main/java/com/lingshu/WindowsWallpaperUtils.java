package com.lingshu;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.WNDENUMPROC;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.W32APIOptions;

import java.util.concurrent.atomic.AtomicReference;

public final class WindowsWallpaperUtils {

    private WindowsWallpaperUtils() {
    }

    public interface User32Extended extends User32 {
        User32Extended INSTANCE = Native.load("user32", User32Extended.class, W32APIOptions.DEFAULT_OPTIONS);

        int SendMessageTimeout(
                HWND hWnd,
                int Msg,
                WPARAM wParam,
                LPARAM lParam,
                int fuFlags,
                int uTimeout,
                IntByReference lpdwResult
        );
    }

    private static final int PROGMAN_SPAWN_WORKER = 0x052C;
    private static final int SMTO_NORMAL = 0x0000;

    private static final int WS_CHILD = 0x40000000;
    private static final int WS_POPUP = 0x80000000;
    private static final int WS_CAPTION = 0x00C00000;
    private static final int WS_THICKFRAME = 0x00040000;
    private static final int WS_MINIMIZEBOX = 0x00020000;
    private static final int WS_MAXIMIZEBOX = 0x00010000;

    private static final int WS_EX_APPWINDOW = 0x00040000;

    private static final HWND HWND_BOTTOM = new HWND(Pointer.createConstant(1));
    private static final int SWP_SHOWWINDOW = 0x0040;
    private static final int SWP_FRAMECHANGED = 0x0020;

    public static boolean attachToWallpaper(HWND childHwnd, int width, int height) {
        if (childHwnd == null) {
            return false;
        }

        HWND workerW = findWallpaperHost();
        if (workerW == null) {
            return false;
        }

        int style = User32.INSTANCE.GetWindowLong(childHwnd, WinUser.GWL_STYLE);
        style &= ~(WS_POPUP | WS_CAPTION | WS_THICKFRAME | WS_MINIMIZEBOX | WS_MAXIMIZEBOX);
        style |= WS_CHILD;
        User32.INSTANCE.SetWindowLong(childHwnd, WinUser.GWL_STYLE, style);

        int exStyle = User32.INSTANCE.GetWindowLong(childHwnd, WinUser.GWL_EXSTYLE);
        exStyle &= ~WS_EX_APPWINDOW;
        User32.INSTANCE.SetWindowLong(childHwnd, WinUser.GWL_EXSTYLE, exStyle);

        User32.INSTANCE.SetParent(childHwnd, workerW);
        HWND currentParent = User32.INSTANCE.GetParent(childHwnd);
        if (currentParent == null || !currentParent.equals(workerW)) {
            return false;
        }

        boolean positioned = User32.INSTANCE.SetWindowPos(
                childHwnd,
                HWND_BOTTOM,
                0,
                0,
                width,
                height,
                SWP_SHOWWINDOW | SWP_FRAMECHANGED
        );

        return positioned;
    }

    private static HWND findWallpaperHost() {
        HWND progman = User32.INSTANCE.FindWindow("Progman", null);
        if (progman == null) {
            return null;
        }

        IntByReference result = new IntByReference();
        User32Extended.INSTANCE.SendMessageTimeout(
                progman,
                PROGMAN_SPAWN_WORKER,
                new WPARAM(0xD),
                new LPARAM(0),
                SMTO_NORMAL,
                1000,
                result
        );
        User32Extended.INSTANCE.SendMessageTimeout(
                progman,
                PROGMAN_SPAWN_WORKER,
                new WPARAM(0),
                new LPARAM(0),
                SMTO_NORMAL,
                1000,
                result
        );

        AtomicReference<HWND> workerWRef = new AtomicReference<>();
        WNDENUMPROC enumProc = (topHwnd, data) -> {
            HWND shellView = User32.INSTANCE.FindWindowEx(topHwnd, null, "SHELLDLL_DefView", null);
            if (shellView != null) {
                HWND workerW = User32.INSTANCE.FindWindowEx(null, topHwnd, "WorkerW", null);
                if (workerW != null) {
                    workerWRef.set(workerW);
                    return false;
                }
            }
            return true;
        };

        User32.INSTANCE.EnumWindows(enumProc, null);
        return workerWRef.get();
    }
}
