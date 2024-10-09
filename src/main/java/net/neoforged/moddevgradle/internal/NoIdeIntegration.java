package net.neoforged.moddevgradle.internal;

import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

/**
 * This implementation of {@link IdeIntegration} is used when no IDE was detected to host Gradle.
 */
final class NoIdeIntegration extends IdeIntegration {
    public NoIdeIntegration(Project project) {
        super(project);
    }

    @Override
    protected void registerProjectSyncTask(TaskProvider<?> task) {
        // No IDE
    }
}
