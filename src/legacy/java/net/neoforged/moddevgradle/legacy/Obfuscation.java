package net.neoforged.moddevgradle.legacy;

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import javax.inject.Inject;
import java.util.List;

public abstract class Obfuscation {
    private final Project project;
    final Provider<RegularFile> officialToSrg, mappingsCsv;
    final Configuration autoRenamingToolRuntime;
    final Configuration installerToolsRuntime;

    @Inject
    public Obfuscation(Project project, Provider<RegularFile> officialToSrg, Provider<RegularFile> mappingsCsv, Configuration autoRenamingToolRuntime, Configuration installerToolsRuntime) {
        this.project = project;
        this.officialToSrg = officialToSrg;
        this.mappingsCsv = mappingsCsv;
        this.autoRenamingToolRuntime = autoRenamingToolRuntime;
        this.installerToolsRuntime = installerToolsRuntime;
    }

    /**
     * Create a configure a reobfuscation task.
     *
     * @param jar           the jar task to reobfuscate
     * @param sourceSet     the source set whose classpath to use when remapping inherited methods
     * @param configuration an action used to configure the rebfuscation task
     * @return a provider of the created task
     */
    public TaskProvider<RemapJarTask> reobfuscate(TaskProvider<? extends AbstractArchiveTask> jar, SourceSet sourceSet, Action<RemapJarTask> configuration) {
        var extraMappings = project.getExtensions().getByType(MixinExtension.class).getExtraMappingFiles();
        var reobf = project.getTasks().register("reobf" + StringUtils.capitalize(jar.getName()), RemapJarTask.class, task -> {
            task.getInput().set(jar.flatMap(AbstractArchiveTask::getArchiveFile));
            task.getDestinationDirectory().convention(jar.flatMap(AbstractArchiveTask::getDestinationDirectory));
            task.getArchiveBaseName().convention(jar.flatMap(AbstractArchiveTask::getArchiveBaseName));
            task.getArchiveVersion().convention(jar.flatMap(AbstractArchiveTask::getArchiveVersion));
            task.getArchiveClassifier().convention(jar.flatMap(AbstractArchiveTask::getArchiveClassifier).map(c -> c + "-reobf"));
            task.getArchiveAppendix().convention(jar.flatMap(AbstractArchiveTask::getArchiveAppendix));
            task.getLibraries().from(sourceSet.getCompileClasspath());
            task.getParameters().from(this, RemapParameters.ToolType.ART);
            task.getParameters().getMappings().from(extraMappings);
            configuration.execute(task);
        });

        jar.configure(jarTask -> jarTask.finalizedBy(reobf));

        var java = (AdhocComponentWithVariants) project.getComponents().getByName("java");
        for (var configurationNames : List.of(JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME, JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME)) {
            project.getArtifacts().add(configurationNames, reobf);

            java.withVariantsFromConfiguration(project.getConfigurations().getByName(configurationNames), variant -> {
                variant.getConfigurationVariant().getArtifacts().removeIf(artifact -> artifact.getFile().equals(jar.get().getArchiveFile().get().getAsFile()));
            });
        }

        return reobf;
    }

    /**
     * Creates a configuration that will remap its dependencies, and adds it as a children of the provided {@code parent}.
     */
    public Configuration createRemappingConfiguration(Configuration parent) {
        var remappingConfig = project.getConfigurations().create("mod" + StringUtils.capitalize(parent.getName()), spec -> {
            spec.setDescription("Configuration for dependencies of " + parent.getName() + " that needs to be remapped");
            spec.attributes(attributeContainer -> {
                attributeContainer.attribute(LegacyModDevPlugin.REMAPPED, true);
            });

            // Unfortunately, if we simply try to make the parent extend this config, transformations will not run because the parent doesn't request remapped deps
            // If the parent were to request remapped deps, we'd be remapping everything in it.
            // Therefore we use a slight "hack" that imposes a constraint over all dependencies in this configuration: to be remapped
            spec.withDependencies(dependencies -> dependencies.forEach(dep -> {
                if (dep instanceof ExternalModuleDependency externalModuleDependency) {
                    project.getDependencies().constraints(constraints -> {
                        constraints.add(parent.getName(), externalModuleDependency.getGroup() + ":" + externalModuleDependency.getName() + ":" + externalModuleDependency.getVersion(), c -> {
                            c.attributes(a -> a.attribute(LegacyModDevPlugin.REMAPPED, true));
                        });
                    });
                } else if (dep instanceof FileCollectionDependency fileCollectionDependency) {
                    project.getDependencies().constraints(constraints -> {
                        constraints.add(parent.getName(), fileCollectionDependency.getFiles(), c -> {
                            c.attributes(a -> a.attribute(LegacyModDevPlugin.REMAPPED, true));
                        });
                    });
                } else if (dep instanceof ProjectDependency projectDependency) {
                    project.getDependencies().constraints(constraints -> {
                        constraints.add(parent.getName(), project.project(projectDependency.getPath()), c -> {
                            c.attributes(a -> a.attribute(LegacyModDevPlugin.REMAPPED, true));
                        });
                    });
                }
            }));
        });
        parent.extendsFrom(remappingConfig);
        return remappingConfig;
    }
}
