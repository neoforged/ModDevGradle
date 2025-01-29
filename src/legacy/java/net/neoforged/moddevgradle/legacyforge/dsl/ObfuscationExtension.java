package net.neoforged.moddevgradle.legacyforge.dsl;

import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import net.neoforged.moddevgradle.legacyforge.internal.MinecraftMappings;
import net.neoforged.moddevgradle.legacyforge.internal.SrgMappingsRule;
import net.neoforged.moddevgradle.legacyforge.tasks.RemapJar;
import net.neoforged.moddevgradle.legacyforge.tasks.RemapOperation;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.*;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.ConfigurationVariantDetails;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.JavaPlugin;
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
    private final MinecraftMappings srgMappings;

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
        this.srgMappings = project.getObjects().named(MinecraftMappings.class, MinecraftMappings.SRG);
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
        return reobf;
    }

    /**
     * Replace the publication of the jar task with the reobfuscated jar
     *
     * @param reobf the remapJar task to replace
     */
    public void publishReobfuscated(TaskProvider<RemapJar> reobf) {
        var configurations = project.getConfigurations();
        var java = (AdhocComponentWithVariants) project.getComponents().getByName("java");
        for (var configurationName : List.of(JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME, JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME)) {
            var config = configurations.getByName(configurationName);
            // Mark the original configuration as NAMED to be able to disambiguate between it and the reobfuscated jar,
            // this is used for example by the JarJar configuration.
            config.getAttributes().attribute(MinecraftMappings.ATTRIBUTE, namedMappings);

            // Now create a reobf configuration
            var reobfConfig = configurations.maybeCreate("reobf" + StringUtils.capitalize(configurationName));
            reobfConfig.setDescription("The artifacts remapped to intermediate (SRG) Minecraft names for use in a production environment");
            reobfConfig.getArtifacts().clear(); // If this is called multiple times...
            for (var attribute : config.getAttributes().keySet()) {
                copyAttribute(project, attribute, config, reobfConfig);
            }
            reobfConfig.getAttributes().attribute(MinecraftMappings.ATTRIBUTE, srgMappings);
            project.getArtifacts().add(reobfConfig.getName(), reobf);

            // Publish the reobf configuration instead of the original one to Maven
            java.withVariantsFromConfiguration(config, ConfigurationVariantDetails::skip);
            java.addVariantsFromConfiguration(reobfConfig, spec -> {});
        }
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
            spec.setCanBeResolved(true);
            spec.setTransitive(false);

            // Unfortunately, if we simply try to make the parent extend this config, transformations will not run because the parent doesn't request remapped deps
            // If the parent were to request remapped deps, we'd be remapping everything in it.
            // Therefore, we use a slight "hack" that imposes a constraint over all dependencies in this configuration: to be remapped.
            // Additionally, we force dependencies to be non-transitive since we cannot apply the attribute hack to transitive dependencies.
            spec.withDependencies(dependencies -> dependencies.forEach(dep -> {
                if (dep instanceof ExternalModuleDependency externalModuleDependency) {
                    externalModuleDependency.setTransitive(false);

                    // This rule ensures that this external module will be enriched with the attribute MAPPINGS=SRG
                    project.getDependencies().getComponents().withModule(
                            dep.getGroup() + ":" + dep.getName(), SrgMappingsRule.class, cfg -> {
                                cfg.params(srgMappings);
                            });
                } else if (dep instanceof FileCollectionDependency fileCollectionDependency) {
                    project.getDependencies().constraints(constraints -> {
                        constraints.add(parent.getName(), fileCollectionDependency.getFiles(), c -> {
                            c.attributes(a -> a.attribute(MinecraftMappings.ATTRIBUTE, namedMappings));
                        });
                    });
                } else if (dep instanceof ProjectDependency projectDependency) {
                    project.getDependencies().constraints(constraints -> {
                        constraints.add(parent.getName(), projectDependency.getDependencyProject(), c -> {
                            c.attributes(a -> a.attribute(MinecraftMappings.ATTRIBUTE, namedMappings));
                        });
                    });
                    projectDependency.setTransitive(false);
                }
            }));
        });

        var remappedDep = project.getDependencyFactory().create(
                remappingConfig.getIncoming().artifactView(view -> {
                    view.attributes(a -> a.attribute(MinecraftMappings.ATTRIBUTE, namedMappings));
                }).getFiles());
        remappedDep.because("Remapped mods from " + remappingConfig.getName());

        parent.getDependencies().add(
                remappedDep);

        return remappingConfig;
    }
}
