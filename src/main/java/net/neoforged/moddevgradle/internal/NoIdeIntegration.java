package net.neoforged.moddevgradle.internal;

import org.gradle.api.Project;

/**
 * This implementation of {@link IdeIntegration} is used when no IDE was detected to host Gradle.
 */
final class NoIdeIntegration extends IdeIntegration {
    public NoIdeIntegration(Project project, Branding branding) {
        super(project, branding);
    }
}
