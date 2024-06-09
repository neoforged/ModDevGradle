package net.neoforged.moddevgradle.internal.utils;

/**
 * Utilities for trying to detect in which IDE Gradle is running.
 */
public final class IdeDetection {
    private IdeDetection() {
    }

    /**
     * @return true if IntelliJ is running Gradle. This is true both during sync and execution of other Gradle tasks.
     */
    public static boolean isIntelliJ() {
        return Boolean.getBoolean("idea.active");
    }

    /**
     * @return true if IntelliJ is syncing its project model with Gradle.
     */
    public static boolean isIntelliJSync() {
        return Boolean.getBoolean("idea.sync.active");
    }

    /**
     * @return true if running under Eclipse (either Task execution or otherwise)
     */
    public static boolean isEclipse() {
        return System.getProperty("eclipse.application") != null;
    }
}
