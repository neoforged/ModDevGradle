package net.neoforged.moddevgradle.internal.utils;

import org.gradle.api.GradleException;

public enum OperatingSystem {
    LINUX,
    MACOS,
    WINDOWS;

    public static OperatingSystem current() {
        var osName = System.getProperty("os.name");
        // The following matches the logic in Apache Commons Lang 3 SystemUtils
        if (osName.startsWith("Linux") || osName.startsWith("LINUX")) {
            return LINUX;
        } else if (osName.startsWith("Mac OS X")) {
            return MACOS;
        } else if (osName.startsWith("Windows")) {
            return WINDOWS;
        } else {
            throw new GradleException("Unsupported operating system: " + osName);
        }
    }
}
