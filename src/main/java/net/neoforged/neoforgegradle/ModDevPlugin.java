package net.neoforged.neoforgegradle;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * This class is used via multi-release jar for versions of Java that are lower than the minimum we support.
 */
public class ModDevPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        throw new GradleException("To use the NeoForge plugin, please run Gradle with Java 17 or newer. You are currently running on Java " + System.getProperty("java.version") + ".");
    }
}
