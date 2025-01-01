package net.neoforged.moddevgradle.internal.jarjar;

import net.neoforged.moddevgradle.internal.Branding;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import net.neoforged.moddevgradle.tasks.JarJar;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class JarJarPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        var sourceSets = ExtensionUtils.getSourceSets(project);
        sourceSets.all(sourceSet -> {
            var jarJarTask = JarJar.registerWithConfiguration(project, sourceSet.getTaskName(null, "jarJar"));
            jarJarTask.configure(task -> task.setGroup(Branding.MDG.internalTaskGroup()));

            // The target jar task for this source set might not exist, and #named(String) requires the task to exist
            var jarTaskName = sourceSet.getJarTaskName();
            project.getTasks().withType(AbstractArchiveTask.class).named(name -> name.equals(jarTaskName)).configureEach(task -> {
                task.from(jarJarTask);
            });
        });
    }
}
