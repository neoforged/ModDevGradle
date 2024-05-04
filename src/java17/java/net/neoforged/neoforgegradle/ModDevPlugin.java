package net.neoforged.neoforgegradle;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.util.GradleVersion;

/**
 * This is just a trampoline to perform the Gradle version check.
 * See the class of the same name in the "main" source-set for the Java version check.
 * The actual plugin implementation is in {@link ModDevPluginImpl} to deal with IntelliJ getting confused
 * by the two classes of the same name.
 */
public class ModDevPlugin implements Plugin<Project> {
    private static final GradleVersion MIN_VERSION = GradleVersion.version("8.7");

    @Override
    public void apply(Project project) {
        if (GradleVersion.current().compareTo(MIN_VERSION) < 0) {
            throw new GradleException("To use the NeoForge plugin, please use at least " + MIN_VERSION + ". You are currently using " + GradleVersion.current() + ".");
        }

        new ModDevPluginImpl().apply(project);
    }
}
