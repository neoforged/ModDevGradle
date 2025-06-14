package net.neoforged.moddevgradle.legacyforge.dsl;

import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import net.neoforged.moddevgradle.legacyforge.internal.MinecraftMappings;
import net.neoforged.moddevgradle.legacyforge.tasks.RemapJar;
import net.neoforged.moddevgradle.legacyforge.tasks.RemapOperation;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.ConfigurationVariantDetails;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.jetbrains.annotations.ApiStatus;

public abstract class ObfuscationExtension {
    private final Project project;
    private final Configuration autoRenamingToolRuntime;
    private final Configuration installerToolsRuntime;
    private final FileCollection extraMixinMappings;

    private final MinecraftMappings namedMappings;

    @Inject
    public ObfuscationExtension(Project project,
            Configuration autoRenamingToolRuntime,
            Configuration installerToolsRuntime,
            FileCollection extraMixinMappings) {
        this.project = project;
        this.autoRenamingToolRuntime = autoRenamingToolRuntime;
        this.installerToolsRuntime = installerToolsRuntime;
        this.extraMixinMappings = extraMixinMappings;

        this.namedMappings = project.getObjects().named(MinecraftMappings.class, MinecraftMappings.NAMED);
    }

    private <T> Provider<T> assertConfigured(Provider<T> provider) {
        return provider.orElse(project.provider(() -> {
            throw new InvalidUserCodeException("Please enable modding by setting legacyForge.version or calling legacyForge.enable()");
        }));
    }

    /**
     * Format is TSRG.
     */
    @ApiStatus.Internal
    public abstract RegularFileProperty getNamedToSrgMappings();

    /**
     * Format is a ZIP file containing CSV files with mapping data.
     */
    @ApiStatus.Internal
    public abstract RegularFileProperty getSrgToNamedMappings();

    @ApiStatus.Internal
    public void configureNamedToSrgOperation(RemapOperation operation) {
        operation.getToolType().set(RemapOperation.ToolType.ART);
        operation.getToolClasspath().from(autoRenamingToolRuntime);
        operation.getMappings().from(assertConfigured(getNamedToSrgMappings()));
    }

    @ApiStatus.Internal
    public void configureSrgToNamedOperation(RemapOperation operation) {
        operation.getToolType().set(RemapOperation.ToolType.INSTALLER_TOOLS);
        operation.getToolClasspath().from(installerToolsRuntime);
        operation.getMappings().from(assertConfigured(getSrgToNamedMappings()));
    }

    /**
     * Create a reobfuscation task.
     *
     * @param jar       the jar task to reobfuscate
     * @param sourceSet the source set whose classpath to use when remapping inherited methods
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
     * @param configureTask an action used to configure the rebfuscation task
     * @return a provider of the created task
     */
    public TaskProvider<RemapJar> reobfuscate(TaskProvider<? extends AbstractArchiveTask> jar,
            SourceSet sourceSet,
            Action<RemapJar> configureTask) {
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
            configureTask.execute(task);
        });

        jar.configure(task -> {
            task.finalizedBy(reobf);
            // Move plain jars into a subdirectory to be able to maintain the same classifier for the reobfuscated version
            task.getDestinationDirectory().set(task.getProject().getLayout().getBuildDirectory().dir("devlibs"));
        });

        // Replace the publication of the jar task with the reobfuscated jar
        var configurations = project.getConfigurations();
        var java = (AdhocComponentWithVariants) project.getComponents().getByName("java");
        for (var configurationName : List.of(sourceSet.getRuntimeElementsConfigurationName(), sourceSet.getApiElementsConfigurationName())) {
            var config = configurations.getByName(configurationName);
            // Mark the original configuration as NAMED to be able to disambiguate between it and the reobfuscated jar,
            // this is used for example by the JarJar configuration.
            config.getAttributes().attribute(MinecraftMappings.ATTRIBUTE, namedMappings);

            // Now create a reobf configuration
            var reobfConfig = configurations.maybeCreate("reobf" + StringUtils.capitalize(configurationName));
            reobfConfig.setDescription("The artifacts remapped to intermediate (SRG) Minecraft names for use in a production environment");
            reobfConfig.getArtifacts().clear(); // If this is called multiple times...
            for (var attribute : config.getAttributes().keySet()) {
                // Don't copy the mappings attribute because we don't want to leak it in the published metadata
                // and there is no way to unset it later.
                if (attribute != MinecraftMappings.ATTRIBUTE) {
                    copyAttribute(project, attribute, config, reobfConfig);
                }
            }
            project.getArtifacts().add(reobfConfig.getName(), reobf);

            // Publish the reobf configuration instead of the original one to Maven
            java.withVariantsFromConfiguration(config, ConfigurationVariantDetails::skip);
            java.addVariantsFromConfiguration(reobfConfig, spec -> {});
        }

        return reobf;
    }

    private static <T> void copyAttribute(Project project, Attribute<T> attribute, Configuration fromConfig, Configuration toConfig) {
        toConfig.getAttributes().attributeProvider(attribute, project.provider(() -> {
            return Objects.requireNonNull(fromConfig.getAttributes().getAttribute(attribute));
        }));
    }

    /**
     * Creates a configuration that will remap its dependencies, and adds it as a children of the provided {@code parent}.
     * The created configuration uses the name of its parent capitalized and prefixed by "mod".
     */
    public Configuration createRemappingConfiguration(Configuration parent) {
        var remappingConfig = project.getConfigurations().create("mod" + StringUtils.capitalize(parent.getName()), spec -> {
            spec.setDescription("Configuration for dependencies of " + parent.getName() + " that needs to be remapped");
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
                            c.attributes(a -> a.attribute(MinecraftMappings.ATTRIBUTE, namedMappings));
                        });
                    });
                    externalModuleDependency.setTransitive(false);
                } else if (dep instanceof FileCollectionDependency fileCollectionDependency) {
                    project.getDependencies().constraints(constraints -> {
                        constraints.add(parent.getName(), fileCollectionDependency.getFiles(), c -> {
                            c.attributes(a -> a.attribute(MinecraftMappings.ATTRIBUTE, namedMappings));
                        });
                    });
                } else if (dep instanceof ProjectDependency projectDependency) {
                    project.getDependencies().constraints(constraints -> {
                        constraints.add(parent.getName(), getProjectDependencyProject(project, projectDependency), c -> {
                            c.attributes(a -> a.attribute(MinecraftMappings.ATTRIBUTE, namedMappings));
                        });
                    });
                    projectDependency.setTransitive(false);
                }
            }));
        });
        parent.extendsFrom(remappingConfig);

        return remappingConfig;
    }

    private static Project getProjectDependencyProject(Project project, ProjectDependency projectDependency) {
        // Gradle 9 requires using getPath(), but it was only added in 8.11, and we currently target 8.9
        try {
            var clazz = ProjectDependency.class;
            try {
                var getPathMethod = clazz.getMethod("getPath");
                var path = (String) getPathMethod.invoke(projectDependency);
                return project.project(path);
            } catch (NoSuchMethodException ignored) {
                var getDependencyProjectMethod = clazz.getMethod("getDependencyProject");
                return (Project) getDependencyProjectMethod.invoke(projectDependency);
            }
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Failed to access project of ProjectDependency", exception);
        }
    }
}
