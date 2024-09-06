package net.neoforged.moddevgradle.legacy;

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import javax.inject.Inject;
import java.util.List;

public abstract class Reobfuscation {
    private final Project project;
    private final Provider<RegularFile> officialToSrg;
    private final Configuration autoRenamingToolRuntime;

    @Inject
    public Reobfuscation(Project project, Provider<RegularFile> officialToSrg, Configuration autoRenamingToolRuntime) {
        this.project = project;
        this.officialToSrg = officialToSrg;
        this.autoRenamingToolRuntime = autoRenamingToolRuntime;
    }

    public TaskProvider<RemapJarTask> reobfuscate(TaskProvider<? extends AbstractArchiveTask> jar, SourceSet sourceSet, Action<RemapJarTask> configuration) {
        var reobf = project.getTasks().register("reobf" + StringUtils.capitalize(jar.getName()), RemapJarTask.class, task -> {
            task.getInput().set(jar.flatMap(AbstractArchiveTask::getArchiveFile));
            task.getMappings().set(officialToSrg);
            task.getDestinationDirectory().convention(jar.flatMap(AbstractArchiveTask::getDestinationDirectory));
            task.getArchiveBaseName().convention(jar.flatMap(AbstractArchiveTask::getArchiveBaseName));
            task.getArchiveVersion().convention(jar.flatMap(AbstractArchiveTask::getArchiveVersion));
            task.getArchiveClassifier().convention(jar.flatMap(AbstractArchiveTask::getArchiveClassifier).map(c -> c + "-reobf"));
            task.getLibraries().from(sourceSet.getCompileClasspath());
            task.getToolClasspath().from(autoRenamingToolRuntime);
            configuration.execute(task);
        });

        jar.configure(jarTask -> jarTask.finalizedBy(reobf));

        var java = (AdhocComponentWithVariants) project.getComponents().getByName("java");
        for (var configurationNames : List.of(JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME, JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME)) {
            project.getArtifacts().add(configurationNames, reobf, artifact -> artifact.builtBy(reobf));

            java.withVariantsFromConfiguration(project.getConfigurations().getByName(configurationNames), variant -> {
                variant.getConfigurationVariant().getArtifacts().removeIf(artifact -> artifact.getFile().equals(jar.get().getArchiveFile().get().getAsFile()));
            });
        }

        return reobf;
    }
}
