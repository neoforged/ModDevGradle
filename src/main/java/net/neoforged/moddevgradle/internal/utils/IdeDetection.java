package net.neoforged.moddevgradle.internal.utils;

import org.gradle.api.Project;
import org.jetbrains.annotations.Nullable;

import java.io.File;

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

    /**
     * @return true if running under Visual Studio Code (Applies to task execution and sync)
     */
    public static boolean isVsCode() {
        var vsCodePidString = System.getenv("VSCODE_PID");
        if (vsCodePidString == null) {
            return false;
        }

        long vsCodePid;
        try {
            vsCodePid = Long.parseUnsignedLong(vsCodePidString);
        } catch (NumberFormatException e) {
            return false;
        }

        // One of our parent processes should be the same process mentioned in VSCODE_PID environment variable.
        var ourProcess = ProcessHandle.current();
        var maybeParent = ourProcess.parent();
        while (maybeParent.isPresent()) {
            var parent = maybeParent.get();
            if (parent.pid() == vsCodePid) {
                return true;
            }

            maybeParent = parent.parent();
        }

        return false;
    }

    /**
     * Try to find the IntelliJ project directory that belongs to this Gradle project.
     * There are scenarios where this is impossible, since IntelliJ allows adding
     * Gradle builds to IntelliJ projects in a completely different directory.
     */
    @Nullable
    public static File getIntellijProjectDir(Project project) {
        // Always try the root of a composite build first, since it has the highest chance
        var root = project.getGradle().getParent();
        if (root != null) {
            while (root.getParent() != null) {
                root = root.getParent();
            }

            return getIntellijProjectDir(root.getRootProject().getProjectDir());
        }

        // As a fallback or in case of not using composite builds, try the root project folder
        return getIntellijProjectDir(project.getRootDir());
    }

    @Nullable
    private static File getIntellijProjectDir(File gradleProjectDir) {
        var ideaDir = new File(gradleProjectDir, ".idea");
        return ideaDir.exists() ? ideaDir : null;
    }

}
