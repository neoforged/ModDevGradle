package net.neoforged.moddevgradle.internal.utils;

import org.gradle.api.Project;
import org.gradle.api.initialization.IncludedBuild;
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
     * Try to find the IntelliJ project directory that belongs to this Gradle project.
     * There are scenarios where this is impossible, since IntelliJ allows adding
     * Gradle builds to IntelliJ projects in a completely different directory.
     */
    @Nullable
    public static File getIntellijProjectDir(Project project) {
        // Always try the root directory first, since it has the highest chance
        var intellijProjectDir = getIntellijProjectDir(project.getRootDir());
        if (intellijProjectDir != null) {
            return intellijProjectDir;
        }

        // Try every included build
        for (var includedBuild : project.getGradle().getIncludedBuilds()) {
            if (!includedBuild.getProjectDir().equals(project.getRootDir())) {
                intellijProjectDir = getIntellijProjectDir(includedBuild.getProjectDir());
                if (intellijProjectDir != null) {
                    return intellijProjectDir;
                }
            }
        }

        return null;
    }

    private static File getIntellijProjectDir(File gradleProjectDir) {
        // Search the .idea folder belonging to this Gradle project
        // In includeBuild scenarios, it might be above the project. It's also possible
        // that it is completely separate.
        var ideaDir = new File(gradleProjectDir, ".idea");
        while (!ideaDir.exists()) {
            gradleProjectDir = gradleProjectDir.getParentFile();
            if (gradleProjectDir == null) {
                return null;
            }
            ideaDir = new File(gradleProjectDir, ".idea");
        }

        return ideaDir;
    }

}
