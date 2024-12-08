package net.neoforged.moddevgradle.legacyforge.dsl;

import net.neoforged.moddevgradle.legacyforge.internal.LegacyForgeModDevPlugin;
import net.neoforged.moddevgradle.legacyforge.tasks.RemapJar;
import net.neoforged.moddevgradle.legacyforge.tasks.RemapOperation;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.jetbrains.annotations.ApiStatus;

import javax.inject.Inject;
import java.util.List;

public abstract class Obfuscation {
    private final Project project;
    private final Provider<RegularFile> officialToSrg;
    private final Provider<RegularFile> mappingsCsv;
    private final Configuration autoRenamingToolRuntime;
    private final Configuration installerToolsRuntime;
    private final FileCollection extraMixinMappings;

    @Inject
    public Obfuscation(Project project,
                       Provider<RegularFile> officialToSrg,
                       Provider<RegularFile> mappingsCsv,
                       Configuration autoRenamingToolRuntime,
                       Configuration installerToolsRuntime,
                       FileCollection extraMixinMappings) {
        this.project = project;
        this.officialToSrg = officialToSrg;
        this.mappingsCsv = mappingsCsv;
        this.autoRenamingToolRuntime = autoRenamingToolRuntime;
        this.installerToolsRuntime = installerToolsRuntime;
        this.extraMixinMappings = extraMixinMappings;
    }

    @ApiStatus.Internal
    public void configureNamedToSrgOperation(RemapOperation operation) {
        operation.getToolType().set(RemapOperation.ToolType.ART);
        operation.getToolClasspath().from(autoRenamingToolRuntime);
        operation.getMappings().from(officialToSrg);
    }

    @ApiStatus.Internal
    public void configureSrgToNamedOperation(RemapOperation operation) {
        operation.getToolType().set(RemapOperation.ToolType.INSTALLER_TOOLS);
        operation.getToolClasspath().from(installerToolsRuntime);
        operation.getMappings().from(mappingsCsv);
    }

    /**
     * Create a reobfuscation task.
     *
     * @param jar           the jar task to reobfuscate
     * @param sourceSet     the source set whose classpath to use when remapping inherited methods
     * @return a provider of the created task
     */
    public TaskProvider<RemapJar> reobfuscate(TaskProvider<? extends AbstractArchiveTask> jar, SourceSet sourceSet) {
        return reobfuscate(jar, sourceSet, ignored -> {});
    }

    /**
     * Create and configure a reobfuscation task.
     *
     * @param jar           the jar task to reobfuscate
     * @param sourceSet     the source set whose classpath to use when remapping inherited methods
     * @param configuration an action used to configure the rebfuscation task
     * @return a provider of the created task
     */
    public TaskProvider<RemapJar> reobfuscate(TaskProvider<? extends AbstractArchiveTask> jar,
                                              SourceSet sourceSet,
                                              Action<RemapJar> configuration) {

        var reobf = project.getTasks().register("reobf" + StringUtils.capitalize(jar.getName()), RemapJar.class, task -> {
            task.getInput().set(jar.flatMap(AbstractArchiveTask::getArchiveFile));
            task.getDestinationDirectory().convention(task.getProject().getLayout().getBuildDirectory().dir("libs"));
            task.getArchiveBaseName().convention(jar.flatMap(AbstractArchiveTask::getArchiveBaseName));
            task.getArchiveVersion().convention(jar.flatMap(AbstractArchiveTask::getArchiveVersion));
            task.getArchiveClassifier().convention(jar.flatMap(AbstractArchiveTask::getArchiveClassifier));
            task.getArchiveAppendix().convention(jar.flatMap(AbstractArchiveTask::getArchiveAppendix));
            task.getLibraries().from(sourceSet.getCompileClasspath());
            configureNamedToSrgOperation(task.getRemapOperation());
            task.getRemapOperation().getMappings().from(extraMixinMappings);
            configuration.execute(task);
        });

        jar.configure(task -> {
            task.finalizedBy(reobf);
            // Move plain jars into a subdirectory to be able to maintain the same classifier for the reobfuscated version
            task.getDestinationDirectory().set(task.getProject().getLayout().getBuildDirectory().dir("devlibs"));
        });

        // Replace the publication of the jar task with the reobfuscated jar
        var java = (AdhocComponentWithVariants) project.getComponents().getByName("java");
        for (var configurationName : List.of(JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME, JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME)) {
            project.getArtifacts().add(configurationName, reobf);
            java.withVariantsFromConfiguration(project.getConfigurations().getByName(configurationName), variant -> {
                variant.getConfigurationVariant().getArtifacts().removeIf(artifact -> artifact.getFile().equals(jar.get().getArchiveFile().get().getAsFile()));
            });
        }

        return reobf;
    }

    /**
     * Creates a configuration that will remap its dependencies, and adds it as a children of the provided {@code parent}.
     * The created configuration uses the name of its parent capitalized and prefixed by "mod".
     */
    public Configuration createRemappingConfiguration(Configuration parent) {
        var remappingConfig = project.getConfigurations().create("mod" + StringUtils.capitalize(parent.getName()), spec -> {
            spec.setDescription("Configuration for dependencies of " + parent.getName() + " that needs to be remapped");
            spec.attributes(attributeContainer -> {
                attributeContainer.attribute(LegacyForgeModDevPlugin.REMAPPED, true);
            });
            spec.setCanBeConsumed(false);
            spec.setCanBeResolved(false);
            spec.setTransitive(false);

            // Unfortunately, if we simply try to make the parent extend this config, transformations will not run because the parent doesn't request remapped deps
            // If the parent were to request remapped deps, we'd be remapping everything in it.
            // Therefore, we use a slight "hack" that imposes a constraint over all dependencies in this configuration: to be remapped.
            // Additionally, we force dependencies to be non-transitive since we cannot apply the attribute hack to transitive dependencies.
            spec.withDependencies(dependencies -> dependencies.forEach(dep -> {
                if (dep instanceof ExternalModuleDependency externalModuleDependency) {
                    project.getDependencies().constraints(constraints -> {
                        constraints.add(parent.getName(), externalModuleDependency.getGroup() + ":" + externalModuleDependency.getName() + ":" + externalModuleDependency.getVersion(), c -> {
                            c.attributes(a -> a.attribute(LegacyForgeModDevPlugin.REMAPPED, true));
                        });
                    });
                    externalModuleDependency.setTransitive(false);
                } else if (dep instanceof FileCollectionDependency fileCollectionDependency) {
                    project.getDependencies().constraints(constraints -> {
                        constraints.add(parent.getName(), fileCollectionDependency.getFiles(), c -> {
                            c.attributes(a -> a.attribute(LegacyForgeModDevPlugin.REMAPPED, true));
                        });
                    });
                } else if (dep instanceof ProjectDependency projectDependency) {
                    project.getDependencies().constraints(constraints -> {
                        constraints.add(parent.getName(), projectDependency.getDependencyProject(), c -> {
                            c.attributes(a -> a.attribute(LegacyForgeModDevPlugin.REMAPPED, true));
                        });
                    });
                    projectDependency.setTransitive(false);
                }
            }));
        });
        parent.extendsFrom(remappingConfig);
        return remappingConfig;
    }
}
