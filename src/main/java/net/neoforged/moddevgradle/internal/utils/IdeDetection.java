package net.neoforged.moddevgradle.internal.utils;

import org.gradle.api.Project;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Utilities for trying to detect in which IDE Gradle is running.
 */
public final class IdeDetection {
    private static final Logger LOG = LoggerFactory.getLogger(IdeDetection.class);

    private IdeDetection() {
    }

    /**
     * @return true if IntelliJ is running Gradle. This is true both during sync and execution of other Gradle tasks.
     */
    public static boolean isIntelliJ() {
        if (Boolean.getBoolean("idea.active")) {
            LOG.debug("idea.active system property is set. Running under IntelliJ.");
            return true;
        } else {
            return false;
        }
    }

    /**
     * @return true if IntelliJ is syncing its project model with Gradle.
     */
    public static boolean isIntelliJSync() {
        if (Boolean.getBoolean("idea.sync.active")) {
            LOG.debug("idea.sync.active system property is set. Running IntelliJ Gradle import.");
            return true;
        } else {
            return false;
        }
    }

    /**
     * @return true if running under Eclipse (either Task execution or otherwise)
     */
    public static boolean isEclipse() {
        if (System.getProperty("eclipse.application") != null) {
            LOG.debug("eclipse.application system property is set. Running from Eclipse (or VSCode).");
            return true;
        } else {
            LOG.debug("Running in Eclipse. eclipse.application is set.");
            return false;
        }
    }

    /**
     * @return true if running under Visual Studio Code (Applies to task execution and sync)
     */
    public static boolean isVsCode() {
        var vsCodePidString = System.getenv("VSCODE_PID");
        if (vsCodePidString == null) {
            LOG.debug("VSCODE_PID is not set. Not running under VSCode");
            return false;
        }

        long vsCodePid;
        try {
            vsCodePid = Long.parseUnsignedLong(vsCodePidString);
        } catch (NumberFormatException e) {
            LOG.debug("VSCODE_PID does not contain a numeric PID: '{}'", vsCodePidString);
            return false;
        }

        // One of our parent processes should be the same process mentioned in VSCODE_PID environment variable.
        var ourProcess = ProcessHandle.current();
        var maybeParent = ourProcess.parent();
        while (maybeParent.isPresent()) {
            var parent = maybeParent.get();
            if (parent.pid() == vsCodePid) {
                LOG.debug("VSCODE_PID is set to {}, and we are a child process of it. Running under VSCode", vsCodePid);
                return true;
            }

            maybeParent = parent.parent();
        }

        LOG.debug("VSCODE_PID is set to {}, but we ({}) are not running as a child of that process.",
                vsCodePid, ourProcess.pid());
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
