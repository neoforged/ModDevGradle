package net.neoforged.moddevgradle;

import net.neoforged.moddevgradle.internal.JUnitPluginImpl;
import net.neoforged.moddevgradle.internal.ModDevPluginImpl;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.util.GradleVersion;

/**
 * This is just a trampoline to perform the Gradle version check.
 * See the class of the same name in the "main" source-set for the Java version check.
 * The actual plugin implementation is in {@link JUnitPluginImpl} to deal with IntelliJ getting confused
 * by the two classes of the same name.
 */
public class JUnitPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        if (GradleVersion.current().compareTo(ModDevPlugin.MIN_VERSION) < 0) {
            throw new GradleException("To use the NeoForge plugin, please use at least " + ModDevPlugin.MIN_VERSION
                                      + ". You are currently using " + GradleVersion.current() + ".");
        }

        new JUnitPluginImpl().apply(project);
    }
}
