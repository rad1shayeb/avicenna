package org.hospital.avicennaarchive.accessbridge;

import java.io.File;

/**
 * Ensures the Java Access Bridge native DLLs are discoverable before JNA tries
 * to load them. WindowsAccessBridge-{32,64}.dll ships in the running JRE's bin
 * folder (and, for an OS-wide install, in System32/SysWOW64), neither of which
 * JNA searches by default. Call {@link #ensureAccessBridgeOnPath()} as the very
 * first thing in main(), before any class that triggers the native load, so the
 * augmented jna.library.path is in effect when the DLL is loaded.
 */
public final class NativeLibs {

    private NativeLibs() {
    }

    public static void ensureAccessBridgeOnPath() {
        String sep = File.pathSeparator;
        StringBuilder dirs = new StringBuilder();

        String javaHome = System.getProperty("java.home", "");
        if (!javaHome.isEmpty()) {
            append(dirs, sep, javaHome + File.separator + "bin");
        }
        String windir = System.getenv("WINDIR");
        if (windir != null && !windir.isEmpty()) {
            // WindowsAccessBridge-64.dll -> System32, -32 -> SysWOW64 on 64-bit Windows.
            append(dirs, sep, windir + File.separator + "System32");
            append(dirs, sep, windir + File.separator + "SysWOW64");
        }

        String existing = System.getProperty("jna.library.path");
        String combined = (existing == null || existing.isEmpty())
            ? dirs.toString()
            : dirs + sep + existing;
        System.setProperty("jna.library.path", combined);
    }

    private static void append(StringBuilder sb, String sep, String dir) {
        if (sb.length() > 0) {
            sb.append(sep);
        }
        sb.append(dir);
    }
}
