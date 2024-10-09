package net.neoforged.moddevgradle.internal;

import net.neoforged.elc.configs.GradleLaunchConfig;
import net.neoforged.elc.configs.JavaApplicationLaunchConfig;
import net.neoforged.elc.configs.LaunchGroup;
import net.neoforged.minecraftdependencies.MinecraftDependenciesPlugin;
import net.neoforged.minecraftdependencies.MinecraftDistribution;
import net.neoforged.moddevgradle.dsl.DataFileCollection;
import net.neoforged.moddevgradle.dsl.InternalModelHelper;
import net.neoforged.moddevgradle.dsl.NeoForgeExtension;
import net.neoforged.moddevgradle.dsl.RunModel;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import net.neoforged.moddevgradle.internal.utils.FileUtils;
import net.neoforged.moddevgradle.internal.utils.IdeDetection;
import net.neoforged.moddevgradle.internal.utils.StringUtils;
import net.neoforged.moddevgradle.tasks.JarJar;
import net.neoforged.nfrtgradle.CreateMinecraftArtifacts;
import net.neoforged.nfrtgradle.DownloadAssets;
import net.neoforged.nfrtgradle.NeoFormRuntimePlugin;
import net.neoforged.vsclc.BatchedLaunchWriter;
import net.neoforged.vsclc.attribute.ConsoleType;
import net.neoforged.vsclc.attribute.PathLike;
import net.neoforged.vsclc.attribute.ShortCmdBehaviour;
import net.neoforged.vsclc.writer.WritingMode;
import org.gradle.api.GradleException;
import org.gradle.api.Named;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.Usage;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.DefaultTaskExecutionRequest;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.Library;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.idea.model.IdeaProject;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.gradle.ext.Application;
import org.jetbrains.gradle.ext.BeforeRunTask;
import org.jetbrains.gradle.ext.IdeaExtPlugin;
import org.jetbrains.gradle.ext.JUnit;
import org.jetbrains.gradle.ext.ProjectSettings;
import org.jetbrains.gradle.ext.RunConfigurationContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The main plugin class.
 */
public class ModDevPlugin implements Plugin<Project> {
    private static final Logger LOG = LoggerFactory.getLogger(ModDevPlugin.class);

    /**
     * This must be relative to the project directory since we can only set this to the same project-relative
     * directory across all subprojects due to IntelliJ limitations.
     */
    private static final String JUNIT_GAME_DIR = "build/minecraft-junit";

    private static final String TASK_GROUP = "mod development";
    private static final String INTERNAL_TASK_GROUP = "mod development/internal";

    /**
     * Name of the configuration in which we place the required dependencies to develop mods for use in the runtime-classpath.
     * We cannot use "runtimeOnly", since the contents of that are published.
     */
    public static final String CONFIGURATION_RUNTIME_DEPENDENCIES = "neoForgeRuntimeDependencies";

    /**
     * Name of the configuration in which we place the required dependencies to develop mods for use in the compile-classpath.
     * While compile only is not published, we also use a configuration here to be consistent.
     */
    public static final String CONFIGURATION_COMPILE_DEPENDENCIES = "neoForgeCompileDependencies";

    private final ObjectFactory objectFactory;

    private Runnable configureTesting = null;

    @Inject
    public ModDevPlugin(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
    }

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(JavaLibraryPlugin.class);
        project.getPlugins().apply(NeoFormRuntimePlugin.class);
        project.getPlugins().apply(MinecraftDependenciesPlugin.class);

        // Do not apply the repositories automatically if they have been applied at the settings-level.
        // It's still possible to apply them manually, though.
        if (!project.getGradle().getPlugins().hasPlugin(RepositoriesPlugin.class)) {
            project.getPlugins().apply(RepositoriesPlugin.class);
        } else {
            LOG.info("Not enabling NeoForged repositories since they were applied at the settings level");
        }
        var javaExtension = ExtensionUtils.getExtension(project, "java", JavaPluginExtension.class);

        var configurations = project.getConfigurations();
        var layout = project.getLayout();
        var tasks = project.getTasks();

        // We use this directory to store intermediate files used during moddev
        var modDevBuildDir = layout.getBuildDirectory().dir("moddev");

        // Create an access transformer configuration
        var accessTransformers = dataFileConfiguration(
                project,
                "accessTransformers",
                "AccessTransformers to widen visibility of Minecraft classes/fields/methods",
                "accesstransformer"
        );
        accessTransformers.extension.getFiles().convention(project.provider(() -> {
            var collection = project.getObjects().fileCollection();

            // Only return this when it actually exists
            var mainSourceSet = ExtensionUtils.getSourceSets(project).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            for (var resources : mainSourceSet.getResources().getSrcDirs()) {
                var defaultPath = new File(resources, "META-INF/accesstransformer.cfg");
                if (project.file(defaultPath).exists()) {
                    return collection.from(defaultPath.getAbsolutePath());
                }
            }

            return collection;
        }));

        // Create a configuration for grabbing interface injection data
        var interfaceInjectionData = dataFileConfiguration(
                project,
                "interfaceInjectionData",
                "Interface injection data adds extend/implements clauses for interfaces to Minecraft code at development time",
                "interfaceinjection"
        );

        var extension = project.getExtensions().create(
                NeoForgeExtension.NAME,
                NeoForgeExtension.class,
                accessTransformers.extension,
                interfaceInjectionData.extension
        );
        var dependencyFactory = project.getDependencyFactory();

        // When a NeoForge version is specified, we use the dependencies published by that, and otherwise
        // we fall back to a potentially specified NeoForm version, which allows us to run in "Vanilla" mode.
        var neoForgeModDevLibrariesDependency = extension.getVersion().map(version -> {
            return dependencyFactory.create("net.neoforged:neoforge:" + version)
                    .capabilities(caps -> {
                        caps.requireCapability("net.neoforged:neoforge-dependencies");
                    });
        }).orElse(extension.getNeoFormVersion().map(version -> {
            return dependencyFactory.create("net.neoforged:neoform:" + version)
                    .capabilities(caps -> {
                        caps.requireCapability("net.neoforged:neoform-dependencies");
                    });
        }));

        var createManifestConfigurations = configureArtifactManifestConfigurations(project, extension);

        // Add a filtered parchment repository automatically if enabled
        var parchment = extension.getParchment();
        var parchmentData = configurations.create("parchmentData", spec -> {
            spec.setDescription("Data used to add parameter names and javadoc to Minecraft sources");
            spec.setCanBeResolved(true);
            spec.setCanBeConsumed(false);
            spec.setTransitive(false); // Expect a single result
            spec.getDependencies().addLater(parchment.getParchmentArtifact().map(project.getDependencyFactory()::create));
        });

        // it has to contain client-extra to be loaded by FML, and it must be added to the legacy CP
        var createArtifacts = tasks.register("createMinecraftArtifacts", CreateMinecraftArtifacts.class, task -> {
            task.setGroup(INTERNAL_TASK_GROUP);
            task.setDescription("Creates the NeoForge and Minecraft artifacts by invoking NFRT.");
            for (var configuration : createManifestConfigurations) {
                task.addArtifactsToManifest(configuration);
            }

            task.getAccessTransformers().from(accessTransformers.configuration);
            task.getInterfaceInjectionData().from(interfaceInjectionData.configuration);
            task.getValidateAccessTransformers().set(extension.getValidateAccessTransformers());
            task.getParchmentData().from(parchmentData);
            task.getParchmentEnabled().set(parchment.getEnabled());
            task.getParchmentConflictResolutionPrefix().set(parchment.getConflictResolutionPrefix());

            var minecraftArtifactsDir = modDevBuildDir.map(dir -> dir.dir("artifacts"));
            Function<String, Provider<RegularFile>> jarPathFactory = suffix -> {
                return minecraftArtifactsDir.zip(
                        // It's helpful to be able to differentiate the Vanilla jar and the NeoForge jar in classic multiloader setups.
                        extension.getVersion().map(v -> "neoforge-" + v).orElse(extension.getNeoFormVersion().map(v -> "vanilla-" + v)),
                        (dir, prefix) -> dir.file(prefix + "-minecraft" + suffix + ".jar"));
            };
            task.getCompiledArtifact().set(jarPathFactory.apply(""));
            task.getCompiledWithSourcesArtifact().set(jarPathFactory.apply("-merged"));
            task.getSourcesArtifact().set(jarPathFactory.apply("-sources"));
            task.getResourcesArtifact().set(jarPathFactory.apply("-resources-aka-client-extra"));

            task.getNeoForgeArtifact().set(getNeoForgeUserDevDependencyNotation(extension));
            task.getNeoFormArtifact().set(getNeoFormDataDependencyNotation(extension));
            task.getAdditionalResults().putAll(extension.getAdditionalMinecraftArtifacts());
        });

        var downloadAssets = tasks.register("downloadAssets", DownloadAssets.class, task -> {
            // Not in the internal group in case someone wants to "preload" the asset before they go offline
            task.setGroup(TASK_GROUP);
            task.setDescription("Downloads the Minecraft assets and asset index needed to run a Minecraft client or generate client-side resources.");
            task.getAssetPropertiesFile().set(modDevBuildDir.map(dir -> dir.file("minecraft_assets.properties")));
            task.getNeoForgeArtifact().set(getNeoForgeUserDevDependencyNotation(extension));
            task.getNeoFormArtifact().set(getNeoFormDataDependencyNotation(extension));
        });

        // For IntelliJ, we attach a combined sources+classes artifact which enables an "Attach Sources..." link for IJ users
        // Otherwise, attaching sources is a pain for IJ users.
        Provider<ConfigurableFileCollection> minecraftClassesArtifact;
        if (shouldUseCombinedSourcesAndClassesArtifact()) {
            minecraftClassesArtifact = createArtifacts.map(task -> project.files(task.getCompiledWithSourcesArtifact()));
        } else {
            minecraftClassesArtifact = createArtifacts.map(task -> project.files(task.getCompiledArtifact()));
        }

        var runtimeDependenciesConfig = configurations.create(CONFIGURATION_RUNTIME_DEPENDENCIES, config -> {
            config.setDescription("The runtime dependencies to develop a mod for NeoForge, including Minecraft classes.");
            config.setCanBeResolved(false);
            config.setCanBeConsumed(false);

            config.getDependencies().addLater(minecraftClassesArtifact.map(dependencyFactory::create));
            config.getDependencies().addLater(createArtifacts.map(task -> project.files(task.getResourcesArtifact())).map(dependencyFactory::create));
            // Technically the Minecraft dependencies do not strictly need to be on the classpath because they are pulled from the legacy class path.
            // However, we do it anyway because this matches production environments, and allows launch proxies such as DevLogin to use Minecraft's libraries.
            config.getDependencies().addLater(neoForgeModDevLibrariesDependency);
            config.getDependencies().add(dependencyFactory.create(RunUtils.DEV_LAUNCH_GAV));
        });

        configurations.create(CONFIGURATION_COMPILE_DEPENDENCIES, config -> {
            config.setDescription("The compile-time dependencies to develop a mod for NeoForge, including Minecraft classes.");
            config.setCanBeResolved(false);
            config.setCanBeConsumed(false);
            config.getDependencies().addLater(minecraftClassesArtifact.map(dependencyFactory::create));
            config.getDependencies().addLater(neoForgeModDevLibrariesDependency);
        });

        var sourceSets = ExtensionUtils.getSourceSets(project);
        extension.addModdingDependenciesTo(sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME));

        // Try to give people at least a fighting chance to run on the correct java version
        project.afterEvaluate(ignored -> {
            var toolchainSpec = javaExtension.getToolchain();
            try {
                toolchainSpec.getLanguageVersion().convention(JavaLanguageVersion.of(21));
            } catch (IllegalStateException e) {
                // We tried our best
            }
        });

        // Let's try to get the userdev JSON out of the universal jar
        // I don't like having to use a configuration for this...
        var userDevConfigOnly = project.getConfigurations().create("neoForgeConfigOnly", spec -> {
            spec.setDescription("Resolves exclusively the NeoForge userdev JSON for configuring runs");
            spec.setCanBeResolved(true);
            spec.setCanBeConsumed(false);
            spec.setTransitive(false);
            spec.getDependencies().addLater(extension.getVersion().map(version -> {
                return dependencyFactory.create("net.neoforged:neoforge:" + version)
                        .capabilities(caps -> {
                            caps.requireCapability("net.neoforged:neoforge-moddev-config");
                        });
            }));
        });

        var ideSyncTask = tasks.register("neoForgeIdeSync", task -> {
            task.setGroup(INTERNAL_TASK_GROUP);
            task.setDescription("A utility task that is used to create necessary files when the Gradle project is synchronized with the IDE project.");
            task.dependsOn(createArtifacts);
            task.dependsOn(extension.getIdeSyncTasks());
        });

        var additionalClasspath = configurations.create("additionalRuntimeClasspath", spec -> {
            spec.setDescription("Contains dependencies of every run, that should not be considered boot classpath modules.");
            spec.setCanBeResolved(true);
            spec.setCanBeConsumed(false);
        });

        Map<RunModel, TaskProvider<PrepareRun>> prepareRunTasks = new IdentityHashMap<>();
        extension.getRuns().all(run -> {
            var type = RunUtils.getRequiredType(project, run);

            var runtimeClasspathConfig = run.getSourceSet().map(SourceSet::getRuntimeClasspathConfigurationName)
                    .map(configurations::getByName);

            var neoForgeModDevModules = project.getConfigurations().create(InternalModelHelper.nameOfRun(run, "", "modulesOnly"), spec -> {
                spec.setDescription("Libraries that should be placed on the JVMs boot module path for run " + run.getName() + ".");
                spec.setCanBeResolved(true);
                spec.setCanBeConsumed(false);
                spec.shouldResolveConsistentlyWith(runtimeClasspathConfig.get());
                // NOTE: When running in vanilla mode, this configuration is simply empty
                spec.getDependencies().addLater(extension.getVersion().map(version -> {
                    return dependencyFactory.create("net.neoforged:neoforge:" + version)
                            .capabilities(caps -> {
                                caps.requireCapability("net.neoforged:neoforge-moddev-module-path");
                            })
                            // TODO: this is ugly; maybe make the configuration transitive in neoforge, or fix the SJH dep.
                            .exclude(Map.of("group", "org.jetbrains", "module", "annotations"));
                }));
                spec.getDependencies().add(dependencyFactory.create(RunUtils.DEV_LAUNCH_GAV));
            });

            var legacyClasspathConfiguration = configurations.create(InternalModelHelper.nameOfRun(run, "", "legacyClasspath"), spec -> {
                spec.setDescription("Contains all dependencies of the " + run.getName() + " run that should not be considered boot classpath modules.");
                spec.setCanBeResolved(true);
                spec.setCanBeConsumed(false);
                spec.shouldResolveConsistentlyWith(runtimeClasspathConfig.get());
                spec.attributes(attributes -> {
                    attributes.attributeProvider(MinecraftDistribution.ATTRIBUTE, type.map(t -> {
                        var name = t.equals("client") || t.equals("data") ? MinecraftDistribution.CLIENT : MinecraftDistribution.SERVER;
                        return project.getObjects().named(MinecraftDistribution.class, name);
                    }));
                    attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
                });
                spec.getDependencies().addLater(neoForgeModDevLibrariesDependency);
                spec.extendsFrom(run.getAdditionalRuntimeClasspathConfiguration(), additionalClasspath);
            });

            var writeLcpTask = tasks.register(InternalModelHelper.nameOfRun(run, "write", "legacyClasspath"), WriteLegacyClasspath.class, writeLcp -> {
                writeLcp.setGroup(INTERNAL_TASK_GROUP);
                writeLcp.setDescription("Writes the legacyClasspath file for the " + run.getName() + " Minecraft run, containing all dependencies that shouldn't be considered boot modules.");
                writeLcp.getLegacyClasspathFile().convention(modDevBuildDir.map(dir -> dir.file(InternalModelHelper.nameOfRun(run, "", "legacyClasspath") + ".txt")));
                writeLcp.addEntries(legacyClasspathConfiguration, createArtifacts.get().getResourcesArtifact());
            });

            var prepareRunTask = tasks.register(InternalModelHelper.nameOfRun(run, "prepare", "run"), PrepareRun.class, task -> {
                task.setGroup(INTERNAL_TASK_GROUP);
                task.setDescription("Prepares all files needed to launch the " + run.getName() + " Minecraft run.");

                task.getGameDirectory().set(run.getGameDirectory());
                task.getVmArgsFile().set(RunUtils.getArgFile(modDevBuildDir, run, RunUtils.RunArgFile.VMARGS));
                task.getProgramArgsFile().set(RunUtils.getArgFile(modDevBuildDir, run, RunUtils.RunArgFile.PROGRAMARGS));
                task.getLog4jConfigFile().set(RunUtils.getArgFile(modDevBuildDir, run, RunUtils.RunArgFile.LOG4J_CONFIG));
                task.getRunType().set(run.getType());
                task.getNeoForgeModDevConfig().from(userDevConfigOnly);
                task.getModules().from(neoForgeModDevModules);
                task.getLegacyClasspathFile().set(writeLcpTask.get().getLegacyClasspathFile());
                task.getAssetProperties().set(downloadAssets.flatMap(DownloadAssets::getAssetPropertiesFile));
                task.getSystemProperties().set(run.getSystemProperties().map(props -> {
                    props = new HashMap<>(props);
                    return props;
                }));
                task.getMainClass().set(run.getMainClass());
                task.getProgramArguments().set(run.getProgramArguments());
                task.getJvmArguments().set(run.getJvmArguments());
                task.getGameLogLevel().set(run.getLogLevel());
            });
            prepareRunTasks.put(run, prepareRunTask);
            ideSyncTask.configure(task -> task.dependsOn(prepareRunTask));

            var createLaunchScriptTask = tasks.register(InternalModelHelper.nameOfRun(run, "create", "launchScript"), CreateLaunchScriptTask.class, task -> {
                task.setGroup(INTERNAL_TASK_GROUP);
                task.setDescription("Creates a bash/shell-script to launch the " + run.getName() + " Minecraft run from outside Gradle or your IDE.");

                task.getWorkingDirectory().set(run.getGameDirectory().map(d -> d.getAsFile().getAbsolutePath()));
                // Use a provider indirection to NOT capture a task dependency on the runtimeClasspath.
                // Resolving the classpath could require compiling some code depending on the runtimeClasspath setup.
                // We don't want to do that on IDE sync!
                // In that case, we can't use an @InputFiles ConfigurableFileCollection or Gradle might complain:
                //   Reason: Task ':createClient2LaunchScript' uses this output of task ':compileApiJava' without
                //   declaring an explicit or implicit dependency. This can lead to incorrect results being produced,
                //   depending on what order the tasks are executed.
                // So we pass the absolute paths directly...
                task.getRuntimeClasspath().set(project.provider(() -> {
                    return runtimeClasspathConfig.get().getFiles().stream()
                            .map(File::getAbsolutePath)
                            .toList();
                }));
                task.getLaunchScript().set(RunUtils.getLaunchScript(modDevBuildDir, run));
                task.getClasspathArgsFile().set(RunUtils.getArgFile(modDevBuildDir, run, RunUtils.RunArgFile.CLASSPATH));
                task.getVmArgsFile().set(prepareRunTask.get().getVmArgsFile().map(d -> d.getAsFile().getAbsolutePath()));
                task.getProgramArgsFile().set(prepareRunTask.get().getProgramArgsFile().map(d -> d.getAsFile().getAbsolutePath()));
                task.getEnvironment().set(run.getEnvironment());
                task.getModFolders().set(RunUtils.getGradleModFoldersProvider(project, run.getLoadedMods(), false));
            });
            ideSyncTask.configure(task -> task.dependsOn(createLaunchScriptTask));

            tasks.register(InternalModelHelper.nameOfRun(run, "run", ""), RunGameTask.class, task -> {
                task.setGroup(TASK_GROUP);
                task.setDescription("Runs the " + run.getName() + " Minecraft run configuration.");

                // Launch with the Java version used in the project
                var toolchainService = ExtensionUtils.findExtension(project, "javaToolchains", JavaToolchainService.class);
                task.getJavaLauncher().set(toolchainService.launcherFor(spec -> spec.getLanguageVersion().set(javaExtension.getToolchain().getLanguageVersion())));
                // Note: this contains both the runtimeClasspath configuration and the sourceset's outputs.
                // This records a dependency on compiling and processing the resources of the source set.
                task.getClasspathProvider().from(run.getSourceSet().map(SourceSet::getRuntimeClasspath));
                task.getGameDirectory().set(run.getGameDirectory());

                task.getEnvironmentProperty().set(run.getEnvironment());
                task.jvmArgs(RunUtils.getArgFileParameter(prepareRunTask.get().getVmArgsFile().get()).replace("\\", "\\\\"));
                task.getMainClass().set(RunUtils.DEV_LAUNCH_MAIN_CLASS);
                task.args(RunUtils.getArgFileParameter(prepareRunTask.get().getProgramArgsFile().get()).replace("\\", "\\\\"));
                // Of course we need the arg files to be up-to-date ;)
                task.dependsOn(prepareRunTask);
                task.dependsOn(run.getTasksBefore());

                task.getJvmArgumentProviders().add(RunUtils.getGradleModFoldersProvider(project, run.getLoadedMods(), false));
            });
        });

        setupJarJar(project);

        configureTesting = () -> setupTesting(
                project,
                modDevBuildDir,
                userDevConfigOnly,
                downloadAssets,
                ideSyncTask,
                createArtifacts,
                neoForgeModDevLibrariesDependency,
                minecraftClassesArtifact
        );

        configureIntelliJModel(project, ideSyncTask, extension, prepareRunTasks);

        configureEclipseModel(project, ideSyncTask, createArtifacts, extension, prepareRunTasks);
    }

    private static Provider<String> getNeoFormDataDependencyNotation(NeoForgeExtension extension) {
        return extension.getNeoFormVersion().map(version -> "net.neoforged:neoform:" + version + "@zip");
    }

    private static Provider<String> getNeoForgeUserDevDependencyNotation(NeoForgeExtension extension) {
        return extension.getVersion().map(version -> "net.neoforged:neoforge:" + version + ":userdev");
    }

    /**
     * Collects all dependencies needed by the NeoFormRuntime
     */
    private List<Configuration> configureArtifactManifestConfigurations(Project project, NeoForgeExtension extension) {
        var configurations = project.getConfigurations();
        var dependencyFactory = project.getDependencyFactory();

        var configurationPrefix = "neoFormRuntimeDependencies";

        Provider<ExternalModuleDependency> neoForgeDependency = extension.getVersion().map(version -> dependencyFactory.create("net.neoforged:neoforge:" + version));
        Provider<ExternalModuleDependency> neoFormDependency = extension.getNeoFormVersion().map(version -> dependencyFactory.create("net.neoforged:neoform:" + version));

        // Gradle prevents us from having dependencies with "incompatible attributes" in the same configuration.
        // What constitutes incompatible cannot be overridden on a per-configuration basis.
        var neoForgeClassesAndData = configurations.create(configurationPrefix + "NeoForgeClasses", spec -> {
            spec.setDescription("Dependencies needed for running NeoFormRuntime for the selected NeoForge/NeoForm version (NeoForge classes)");
            spec.setCanBeConsumed(false);
            spec.setCanBeResolved(true);
            spec.getDependencies().addLater(neoForgeDependency.map(dependency -> dependency.copy()
                    .capabilities(caps -> {
                        caps.requireCapability("net.neoforged:neoforge-moddev-bundle");
                    })));

            // This dependency is used when the NeoForm version is overridden or when we run in Vanilla-only mode
            spec.getDependencies().addLater(neoFormDependency.map(dependency -> dependency.copy()
                    .capabilities(caps -> {
                        caps.requireCapability("net.neoforged:neoform");
                    })));
        });

        // This configuration is empty when running in Vanilla-mode.
        var neoForgeSources = configurations.create(configurationPrefix + "NeoForgeSources", spec -> {
            spec.setDescription("Dependencies needed for running NeoFormRuntime for the selected NeoForge/NeoForm version (NeoForge sources)");
            spec.setCanBeConsumed(false);
            spec.setCanBeResolved(true);
            spec.getDependencies().addLater(neoForgeDependency);
            spec.attributes(attributes -> {
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.DOCUMENTATION));
                attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, project.getObjects().named(DocsType.class, DocsType.SOURCES));
            });
        });

        // Compile-time dependencies used by NeoForm, NeoForge and Minecraft.
        // Also includes any classes referenced by compiled Minecraft code (used by decompilers, renamers, etc.)
        var compileClasspath = configurations.create(configurationPrefix + "CompileClasspath", spec -> {
            spec.setDescription("Dependencies needed for running NeoFormRuntime for the selected NeoForge/NeoForm version (Classpath)");
            spec.setCanBeConsumed(false);
            spec.setCanBeResolved(true);
            spec.getDependencies().addLater(neoForgeDependency.map(dependency -> dependency.copy()
                    .capabilities(caps -> {
                        caps.requireCapability("net.neoforged:neoforge-dependencies");
                    })));
            // This dependency is used when the NeoForm version is overridden or when we run in Vanilla-only mode
            spec.getDependencies().addLater(neoFormDependency.map(dependency -> dependency.copy()
                    .capabilities(caps -> {
                        caps.requireCapability("net.neoforged:neoform-dependencies");
                    })));
            spec.attributes(attributes -> {
                setNamedAttribute(attributes, Usage.USAGE_ATTRIBUTE, Usage.JAVA_API);
                setNamedAttribute(attributes, MinecraftDistribution.ATTRIBUTE, MinecraftDistribution.CLIENT);
            });
        });

        // Runtime-time dependencies used by NeoForm, NeoForge and Minecraft.
        var runtimeClasspath = configurations.create(configurationPrefix + "RuntimeClasspath", spec -> {
            spec.setDescription("Dependencies needed for running NeoFormRuntime for the selected NeoForge/NeoForm version (Classpath)");
            spec.setCanBeConsumed(false);
            spec.setCanBeResolved(true);
            spec.getDependencies().addLater(neoForgeDependency); // Universal Jar
            spec.getDependencies().addLater(neoForgeDependency.map(dependency -> dependency.copy()
                    .capabilities(caps -> {
                        caps.requireCapability("net.neoforged:neoforge-dependencies");
                    })));
            // This dependency is used when the NeoForm version is overridden or when we run in Vanilla-only mode
            spec.getDependencies().addLater(neoFormDependency.map(dependency -> dependency.copy()
                    .capabilities(caps -> {
                        caps.requireCapability("net.neoforged:neoform-dependencies");
                    })));
            spec.attributes(attributes -> {
                setNamedAttribute(attributes, Usage.USAGE_ATTRIBUTE, Usage.JAVA_RUNTIME);
                setNamedAttribute(attributes, MinecraftDistribution.ATTRIBUTE, MinecraftDistribution.CLIENT);
            });
        });

        return List.of(neoForgeClassesAndData, neoForgeSources, compileClasspath, runtimeClasspath);
    }

    private static boolean shouldUseCombinedSourcesAndClassesArtifact() {
        // Only IntelliJ needs the combined artifact
        // For Eclipse, we can attach the sources via the Eclipse project model.
        return IdeDetection.isIntelliJ();
    }

    public void setupTesting() {
        if (configureTesting == null) {
            throw new IllegalStateException("Unit testing was already enabled once!");
        }
        configureTesting.run();
        configureTesting = null;
    }

    private void setupTesting(Project project,
                              Provider<Directory> modDevDir,
                              Configuration userDevConfigOnly,
                              TaskProvider<DownloadAssets> downloadAssets,
                              TaskProvider<Task> ideSyncTask,
                              TaskProvider<CreateMinecraftArtifacts> createArtifacts,
                              Provider<ModuleDependency> neoForgeModDevLibrariesDependency,
                              Provider<ConfigurableFileCollection> minecraftClassesArtifact) {
        var extension = ExtensionUtils.getExtension(project, NeoForgeExtension.NAME, NeoForgeExtension.class);
        var unitTest = extension.getUnitTest();
        var gameDirectory = new File(project.getProjectDir(), JUNIT_GAME_DIR);

        var tasks = project.getTasks();
        var configurations = project.getConfigurations();
        var dependencyFactory = project.getDependencyFactory();

        // Weirdly enough, testCompileOnly extends from compileOnlyApi, and not compileOnly
        configurations.named(JavaPlugin.TEST_COMPILE_ONLY_CONFIGURATION_NAME).configure(configuration -> {
            configuration.getDependencies().addLater(minecraftClassesArtifact.map(dependencyFactory::create));
            configuration.getDependencies().addLater(neoForgeModDevLibrariesDependency);
        });

        var testFixtures = configurations.create("neoForgeTestFixtures", config -> {
            config.setDescription("Additional JUnit helpers provided by NeoForge");
            config.setCanBeResolved(false);
            config.setCanBeConsumed(false);
            config.getDependencies().addLater(extension.getVersion().map(version -> {
                return dependencyFactory.create("net.neoforged:neoforge:" + version)
                        .capabilities(caps -> {
                            caps.requireCapability("net.neoforged:neoforge-moddev-test-fixtures");
                        });
            }));
        });

        var testRuntimeClasspathConfig = configurations.named(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME, files -> {
            files.extendsFrom(configurations.getByName(CONFIGURATION_RUNTIME_DEPENDENCIES));
            files.extendsFrom(testFixtures);
        });

        var neoForgeModDevModules = project.getConfigurations().create("neoForgeTestModules", spec -> {
            spec.setDescription("Libraries that should be placed on the JVMs boot module path for unit tests.");
            spec.setCanBeResolved(true);
            spec.setCanBeConsumed(false);
            spec.shouldResolveConsistentlyWith(testRuntimeClasspathConfig.get());
            // NOTE: When running in vanilla mode, this configuration is simply empty
            spec.getDependencies().addLater(extension.getVersion().map(version -> {
                return dependencyFactory.create("net.neoforged:neoforge:" + version)
                        .capabilities(caps -> {
                            caps.requireCapability("net.neoforged:neoforge-moddev-module-path");
                        })
                        // TODO: this is ugly; maybe make the configuration transitive in neoforge, or fix the SJH dep.
                        .exclude(Map.of("group", "org.jetbrains", "module", "annotations"));
            }));
            spec.getDependencies().add(dependencyFactory.create(RunUtils.DEV_LAUNCH_GAV));
        });

        var legacyClasspathConfiguration = configurations.create("neoForgeTestLibraries", spec -> {
            spec.setDescription("Contains the legacy classpath of unit tests.");
            spec.setCanBeResolved(true);
            spec.setCanBeConsumed(false);
            spec.shouldResolveConsistentlyWith(testRuntimeClasspathConfig.get());
            spec.attributes(attributes -> {
                setNamedAttribute(project, attributes, MinecraftDistribution.ATTRIBUTE, MinecraftDistribution.CLIENT);
                setNamedAttribute(project, attributes, Usage.USAGE_ATTRIBUTE, Usage.JAVA_RUNTIME);
            });
            spec.getDependencies().addLater(neoForgeModDevLibrariesDependency);
        });

        // Place files for junit runtime in a subdirectory to avoid conflicting with other runs
        var runArgsDir = modDevDir.map(dir -> dir.dir("junit"));

        var writeLcpTask = tasks.register("writeNeoForgeTestClasspath", WriteLegacyClasspath.class, writeLcp -> {
            writeLcp.setGroup(INTERNAL_TASK_GROUP);
            writeLcp.setDescription("Writes the legacyClasspath file for the test run, containing all dependencies that shouldn't be considered boot modules.");
            writeLcp.getLegacyClasspathFile().convention(runArgsDir.map(dir -> dir.file("legacyClasspath.txt")));
            writeLcp.addEntries(legacyClasspathConfiguration, createArtifacts.get().getResourcesArtifact());
        });

        var vmArgsFile = runArgsDir.map(dir -> dir.file("vmArgs.txt"));
        var programArgsFile = runArgsDir.map(dir -> dir.file("programArgs.txt"));
        var log4j2ConfigFile = runArgsDir.map(dir -> dir.file("log4j2.xml"));
        var prepareTask = tasks.register("prepareNeoForgeTestFiles", PrepareTest.class, task -> {
            task.setGroup(INTERNAL_TASK_GROUP);
            task.setDescription("Prepares all files needed to run the JUnit test task.");
            task.getGameDirectory().set(gameDirectory);
            task.getVmArgsFile().set(vmArgsFile);
            task.getProgramArgsFile().set(programArgsFile);
            task.getLog4jConfigFile().set(log4j2ConfigFile);
            task.getNeoForgeModDevConfig().from(userDevConfigOnly);
            task.getModules().from(neoForgeModDevModules);
            task.getLegacyClasspathFile().set(writeLcpTask.get().getLegacyClasspathFile());
            task.getAssetProperties().set(downloadAssets.flatMap(DownloadAssets::getAssetPropertiesFile));
            task.getGameLogLevel().set(Level.INFO);
        });

        // Ensure the test files are written on sync so that users who use IDE-only tests can run them
        ideSyncTask.configure(task -> task.dependsOn(prepareTask));

        var testTask = tasks.named(JavaPlugin.TEST_TASK_NAME, Test.class, task -> {
            task.dependsOn(prepareTask);

            // The FML JUnit plugin uses this system property to read a
            // file containing the program arguments needed to launch
            task.systemProperty("fml.junit.argsfile", programArgsFile.get().getAsFile().getAbsolutePath());
            task.jvmArgs(RunUtils.getArgFileParameter(vmArgsFile.get()));

            var modFoldersProvider = RunUtils.getGradleModFoldersProvider(project, unitTest.getLoadedMods(), true);
            task.getJvmArgumentProviders().add(modFoldersProvider);
        });

        project.afterEvaluate(p -> {
            // Test tasks don't have a provider-based property for working directory, so we need to afterEvaluate it.
            testTask.configure(task -> task.setWorkingDir(gameDirectory));

            // Write out a separate file that has IDE specific VM args, which include the definition of the output directories.
            // For JUnit we have to write this to a separate file due to the Run parameters being shared among all projects.
            var intellijVmArgsFile = runArgsDir.map(dir -> dir.file("intellijVmArgs.txt"));
            var outputDirectory = RunUtils.getIntellijOutputDirectory(project);
            var ideSpecificVmArgs = RunUtils.escapeJvmArg(RunUtils.getIdeaModFoldersProvider(project, outputDirectory, unitTest.getLoadedMods(), true).getArgument());
            try {
                var vmArgsFilePath = intellijVmArgsFile.get().getAsFile().toPath();
                Files.createDirectories(vmArgsFilePath.getParent());
                // JVM args generally expect platform encoding
                FileUtils.writeStringSafe(vmArgsFilePath, ideSpecificVmArgs, StringUtils.getNativeCharset());
            } catch (IOException e) {
                throw new GradleException("Failed to write VM args file for IntelliJ unit tests", e);
            }

            // Configure IntelliJ default JUnit parameters, which are used when the user configures IJ to run tests natively
            // IMPORTANT: This affects *all projects*, not just this one. We have to use $MODULE_WORKING_DIR$ to make it work.
            var intelliJRunConfigurations = getIntelliJRunConfigurations(p);
            if (intelliJRunConfigurations != null) {
                intelliJRunConfigurations.defaults(JUnit.class, jUnitDefaults -> {
                    // $MODULE_WORKING_DIR$ is documented here: https://www.jetbrains.com/help/idea/absolute-path-variables.html
                    jUnitDefaults.setWorkingDirectory("$MODULE_WORKING_DIR$/" + JUNIT_GAME_DIR);
                    jUnitDefaults.setVmParameters(
                            // The FML JUnit plugin uses this system property to read a file containing the program arguments needed to launch
                            // NOTE: IntelliJ does not support $MODULE_WORKING_DIR$ in VM Arguments
                            // See https://youtrack.jetbrains.com/issue/IJPL-14230/Add-macro-support-for-VM-options-field-e.g.-expand-ModuleFileDir-properly
                            // As a workaround, we just use paths relative to the working directory.
                            RunUtils.escapeJvmArg("-Dfml.junit.argsfile=" + buildRelativePath(programArgsFile, gameDirectory))
                            + " "
                            + RunUtils.escapeJvmArg("@" + buildRelativePath(vmArgsFile, gameDirectory))
                            + " "
                            + RunUtils.escapeJvmArg("@" + buildRelativePath(intellijVmArgsFile, gameDirectory))
                    );
                });
            }
        });
    }

    private static String buildRelativePath(Provider<RegularFile> file, File workingDirectory) {
        return workingDirectory.toPath().relativize(file.get().getAsFile().toPath()).toString().replace("\\", "/");
    }

    private static void setupJarJar(Project project) {
        SourceSetContainer sourceSets = ExtensionUtils.getExtension(project, "sourceSets", SourceSetContainer.class);
        sourceSets.all(sourceSet -> {
            var jarJarTask = JarJar.registerWithConfiguration(project, sourceSet.getTaskName(null, "jarJar"));
            jarJarTask.configure(task -> task.setGroup(INTERNAL_TASK_GROUP));

            // The target jar task for this source set might not exist, and #named(String) requires the task to exist
            var jarTaskName = sourceSet.getJarTaskName();
            project.getTasks().withType(AbstractArchiveTask.class).named(name -> name.equals(jarTaskName)).configureEach(task -> {
                task.from(jarJarTask);
            });
        });
    }

    private static void addIntelliJRunConfiguration(Project project,
                                                    RunConfigurationContainer runConfigurations,
                                                    @Nullable Function<Project, File> outputDirectory,
                                                    RunModel run,
                                                    PrepareRun prepareTask) {
        var appRun = new Application(run.getIdeName().get(), project);
        var sourceSets = ExtensionUtils.getSourceSets(project);
        var sourceSet = run.getSourceSet().get();
        // Validate that the source set is part of this project
        if (!sourceSets.contains(sourceSet)) {
            throw new GradleException("Cannot use source set from another project for run " + run.getName());
        }
        appRun.setModuleName(RunUtils.getIntellijModuleName(project, sourceSet));
        appRun.setWorkingDirectory(run.getGameDirectory().get().getAsFile().getAbsolutePath());
        appRun.setEnvs(run.getEnvironment().get());

        appRun.setJvmArgs(
                RunUtils.escapeJvmArg(RunUtils.getArgFileParameter(prepareTask.getVmArgsFile().get()))
                + " "
                + RunUtils.escapeJvmArg(RunUtils.getIdeaModFoldersProvider(project, outputDirectory, run.getLoadedMods(), false).getArgument())
        );
        appRun.setMainClass(RunUtils.DEV_LAUNCH_MAIN_CLASS);
        appRun.setProgramParameters(RunUtils.escapeJvmArg(RunUtils.getArgFileParameter(prepareTask.getProgramArgsFile().get())));

        if (!run.getTasksBefore().isEmpty()) {
            // This is slightly annoying.
            // idea-ext does not expose the ability to run multiple gradle tasks at once, but the IDE model is capable of it.
            class GradleTasks extends BeforeRunTask {
                @Inject
                GradleTasks(String nameParam) {
                    type = "gradleTask";
                    name = nameParam;
                }

                @SuppressWarnings("unchecked")
                @Override
                public Map<String, ?> toMap() {
                    var result = (Map<String, Object>) super.toMap();
                    result.put("projectPath", project.getProjectDir().getAbsolutePath().replaceAll("\\\\", "/"));
                    var tasks = run.getTasksBefore().stream().map(task -> task.get().getPath()).collect(Collectors.joining(" "));
                    result.put("taskName", tasks);
                    return result;
                }
            }
            appRun.getBeforeRun().add(new GradleTasks("Prepare"));
        }

        runConfigurations.add(appRun);
    }

    private static void configureIntelliJModel(Project project, TaskProvider<Task> ideSyncTask, NeoForgeExtension extension, Map<RunModel, TaskProvider<PrepareRun>> prepareRunTasks) {
        var rootProject = project.getRootProject();

        if (!rootProject.getPlugins().hasPlugin(IdeaExtPlugin.class)) {
            rootProject.getPlugins().apply(IdeaExtPlugin.class);
        }

        // IDEA Sync has no real notion of tasks or providers or similar
        project.afterEvaluate(ignored -> {
            var settings = getIntelliJProjectSettings(rootProject);
            if (settings != null && IdeDetection.isIntelliJSync()) {
                // Also run the sync task directly as part of the sync. (Thanks Loom).
                var startParameter = project.getGradle().getStartParameter();
                var taskRequests = new ArrayList<>(startParameter.getTaskRequests());

                taskRequests.add(new DefaultTaskExecutionRequest(List.of(ideSyncTask.getName())));
                startParameter.setTaskRequests(taskRequests);
            }

            var runConfigurations = getIntelliJRunConfigurations(rootProject); // TODO: Consider making this a value source

            if (runConfigurations == null) {
                LOG.debug("Failed to find IntelliJ run configuration container. Not adding run configurations.");
            } else {
                var outputDirectory = RunUtils.getIntellijOutputDirectory(project);

                for (var run : extension.getRuns()) {
                    var prepareTask = prepareRunTasks.get(run).get();
                    if (!prepareTask.getEnabled()) {
                        LOG.info("Not creating IntelliJ run {} since its prepare task {} is disabled", run, prepareTask);
                        continue;
                    }
                    addIntelliJRunConfiguration(project, runConfigurations, outputDirectory, run, prepareTask);
                }
            }
        });
    }

    @Nullable
    private static IdeaProject getIntelliJProject(Project project) {
        var ideaModel = ExtensionUtils.findExtension(project, "idea", IdeaModel.class);
        if (ideaModel != null) {
            return ideaModel.getProject();
        }
        return null;
    }

    @Nullable
    private static ProjectSettings getIntelliJProjectSettings(Project project) {
        var ideaProject = getIntelliJProject(project);
        if (ideaProject != null) {
            return ((ExtensionAware) ideaProject).getExtensions().getByType(ProjectSettings.class);
        }
        return null;
    }

    @Nullable
    private static RunConfigurationContainer getIntelliJRunConfigurations(Project project) {
        var projectSettings = getIntelliJProjectSettings(project);
        if (projectSettings != null) {
            return ExtensionUtils.findExtension((ExtensionAware) projectSettings, "runConfigurations", RunConfigurationContainer.class);
        }
        return null;
    }

    private static void configureEclipseModel(Project project,
                                              TaskProvider<Task> ideSyncTask,
                                              TaskProvider<CreateMinecraftArtifacts> createArtifacts,
                                              NeoForgeExtension extension,
                                              Map<RunModel, TaskProvider<PrepareRun>> prepareRunTasks) {

        // Set up stuff for Eclipse
        var eclipseModel = ExtensionUtils.findExtension(project, "eclipse", EclipseModel.class);
        if (eclipseModel == null) {
            // If we detect running under Eclipse or VSCode, we apply the Eclipse plugin
            if (!IdeDetection.isEclipse() && !IdeDetection.isVsCode()) {
                LOG.info("No Eclipse project model found, and not running under Eclipse or VSCode. Skipping Eclipse model configuration.");
                return;
            }

            project.getPlugins().apply(EclipsePlugin.class);
            eclipseModel = ExtensionUtils.findExtension(project, "eclipse", EclipseModel.class);
            if (eclipseModel == null) {
                LOG.error("Even after applying the Eclipse plugin, no 'eclipse' extension was present!");
                return;
            }
        }

        LOG.debug("Configuring Eclipse model for Eclipse project '{}'.", eclipseModel.getProject().getName());

        // Make sure our post-sync task runs on Eclipse
        eclipseModel.synchronizationTasks(ideSyncTask);

        // When using separate artifacts for classes and sources, link them
        if (!shouldUseCombinedSourcesAndClassesArtifact()) {
            var fileClasspath = eclipseModel.getClasspath().getFile();
            fileClasspath.whenMerged((org.gradle.plugins.ide.eclipse.model.Classpath classpath) -> {
                var classesPath = createArtifacts.get().getCompiledArtifact().get().getAsFile();
                var sourcesPath = createArtifacts.get().getSourcesArtifact().get().getAsFile();

                for (var entry : classpath.getEntries()) {
                    if (entry instanceof Library library && classesPath.equals(new File(library.getPath()))) {
                        library.setSourcePath(classpath.fileReference(sourcesPath));
                    }
                }
            });
        }

        // Set up runs if running under buildship and in VS Code
        if (IdeDetection.isVsCode()) {
            project.afterEvaluate(ignored -> {
                var launchWriter = new BatchedLaunchWriter(WritingMode.MODIFY_CURRENT);

                for (var run : extension.getRuns()) {
                    var prepareTask = prepareRunTasks.get(run).get();
                    addVscodeLaunchConfiguration(project, run, prepareTask, launchWriter);
                }

                try {
                    launchWriter.writeToLatestJson(project.getRootDir().toPath());
                } catch (final IOException e) {
                    throw new RuntimeException("Failed to write VSCode launch files", e);
                }
            });
        } else if (IdeDetection.isEclipse()) {
            project.afterEvaluate(ignored -> {
                for (var run : extension.getRuns()) {
                    var prepareTask = prepareRunTasks.get(run).get();
                    addEclipseLaunchConfiguration(project, run, prepareTask);
                }
            });
        }
    }

    private static void addEclipseLaunchConfiguration(Project project,
                                                      RunModel run,
                                                      PrepareRun prepareTask) {
        if (!prepareTask.getEnabled()) {
            LOG.info("Not creating Eclipse run {} since its prepare task {} is disabled", run, prepareTask);
            return;
        }

        // Grab the eclipse model so we can extend it. -> Done on the root project so that the model is available to all subprojects.
        // And so that post sync tasks are only run once for all subprojects.
        var model = project.getExtensions().getByType(EclipseModel.class);

        var runIdeName = run.getIdeName().get();
        var launchConfigName = runIdeName;
        var eclipseProjectName = Objects.requireNonNullElse(model.getProject().getName(), project.getName());

        // If the user wants to run tasks before the actual execution, we create a launch group to facilitate that
        if (!run.getTasksBefore().isEmpty()) {
            // Rename the main launch to "Run " ...
            launchConfigName = "Run " + runIdeName;

            // Creates a launch config to run the preparation tasks
            var prepareRunConfig = GradleLaunchConfig.builder(eclipseProjectName)
                    .tasks(run.getTasksBefore().stream().map(task -> task.get().getPath()).toArray(String[]::new))
                    .build();
            var prepareRunLaunchName = "Prepare " + runIdeName;
            RunUtils.writeEclipseLaunchConfig(project, prepareRunLaunchName, prepareRunConfig);

            // This is the launch group that will first launch Gradle, and then the game
            var withGradleTasksConfig = LaunchGroup.builder()
                    .entry(LaunchGroup.entry(prepareRunLaunchName)
                            .enabled(true)
                            .adoptIfRunning(false)
                            .mode(LaunchGroup.Mode.RUN)
                            // See https://github.com/eclipse/buildship/issues/1272
                            // for why we cannot just wait for termination
                            .action(LaunchGroup.Action.delay(2)))
                    .entry(LaunchGroup.entry(launchConfigName)
                            .enabled(true)
                            .adoptIfRunning(false)
                            .mode(LaunchGroup.Mode.INHERIT)
                            .action(LaunchGroup.Action.none()))
                    .build();
            RunUtils.writeEclipseLaunchConfig(project, runIdeName, withGradleTasksConfig);
        }

        // This is the actual main launch configuration that launches the game
        var config = JavaApplicationLaunchConfig.builder(eclipseProjectName)
                .vmArgs(
                        RunUtils.escapeJvmArg(RunUtils.getArgFileParameter(prepareTask.getVmArgsFile().get())),
                        RunUtils.escapeJvmArg(RunUtils.getEclipseModFoldersProvider(project, run.getLoadedMods(), false).getArgument())
                )
                .args(RunUtils.escapeJvmArg(RunUtils.getArgFileParameter(prepareTask.getProgramArgsFile().get())))
                .envVar(run.getEnvironment().get())
                .workingDirectory(run.getGameDirectory().get().getAsFile().getAbsolutePath())
                .build(RunUtils.DEV_LAUNCH_MAIN_CLASS);
        RunUtils.writeEclipseLaunchConfig(project, launchConfigName, config);

    }

    private static void addVscodeLaunchConfiguration(Project project,
                                                     RunModel run,
                                                     PrepareRun prepareTask,
                                                     BatchedLaunchWriter launchWriter) {
        if (!prepareTask.getEnabled()) {
            LOG.info("Not creating VSCode run {} since its prepare task {} is disabled", run, prepareTask);
            return;
        }

        var model = project.getExtensions().getByType(EclipseModel.class);
        var runIdeName = run.getIdeName().get();
        var eclipseProjectName = Objects.requireNonNullElse(model.getProject().getName(), project.getName());

        // If the user wants to run tasks before the actual execution, we attach them to autoBuildTasks
        // Missing proper support - https://github.com/microsoft/vscode-java-debug/issues/1106
        if (!run.getTasksBefore().isEmpty()) {
            model.autoBuildTasks(run.getTasksBefore().toArray());
        }

        launchWriter.createGroup("Mod Development - " + project.getName(), WritingMode.REMOVE_EXISTING)
                .createLaunchConfiguration()
                .withName(runIdeName)
                .withProjectName(eclipseProjectName)
                .withArguments(List.of(RunUtils.getArgFileParameter(prepareTask.getProgramArgsFile().get())))
                .withAdditionalJvmArgs(List.of(RunUtils.getArgFileParameter(prepareTask.getVmArgsFile().get()),
                        RunUtils.getEclipseModFoldersProvider(project, run.getLoadedMods(), false).getArgument()))
                .withMainClass(RunUtils.DEV_LAUNCH_MAIN_CLASS)
                .withShortenCommandLine(ShortCmdBehaviour.NONE)
                .withConsoleType(ConsoleType.INTERNAL_CONSOLE)
                .withCurrentWorkingDirectory(PathLike.ofNio(run.getGameDirectory().get().getAsFile().toPath()));
    }

    record DataFileCollectionWrapper(DataFileCollection extension, Configuration configuration) {
    }

    private static DataFileCollectionWrapper dataFileConfiguration(Project project, String name, String description, String category) {
        var configuration = project.getConfigurations().create(name, spec -> {
            spec.setDescription(description);
            spec.setCanBeConsumed(false);
            spec.setCanBeResolved(true);
            spec.attributes(attributes -> setNamedAttribute(project, attributes, Category.CATEGORY_ATTRIBUTE, category));
        });

        var elementsConfiguration = project.getConfigurations().create(name + "Elements", spec -> {
            spec.setDescription("Published data files for " + name);
            spec.setCanBeConsumed(true);
            spec.setCanBeResolved(false);
            spec.attributes(attributes -> setNamedAttribute(project, attributes, Category.CATEGORY_ATTRIBUTE, category));
        });

        // Set up the variant publishing conditionally
        var java = (AdhocComponentWithVariants) project.getComponents().getByName("java");
        java.addVariantsFromConfiguration(elementsConfiguration, variant -> {
            // This should be invoked lazily, so checking if the artifacts are empty is fine:
            // "The details object used to determine what to do with a configuration variant **when publishing**."
            if (variant.getConfigurationVariant().getArtifacts().isEmpty()) {
                variant.skip();
            }
        });

        var depFactory = project.getDependencyFactory();
        Consumer<Object> publishCallback = new Consumer<>() {
            ConfigurablePublishArtifact firstArtifact;
            int artifactCount;

            @Override
            public void accept(Object artifactNotation) {
                elementsConfiguration.getDependencies().add(depFactory.create(project.files(artifactNotation)));
                project.getArtifacts().add(elementsConfiguration.getName(), artifactNotation, artifact -> {
                    if (firstArtifact == null) {
                        firstArtifact = artifact;
                        artifact.setClassifier(category);
                        artifactCount = 1;
                    } else {
                        if (artifactCount == 1) {
                            firstArtifact.setClassifier(category + artifactCount);
                        }
                        artifact.setClassifier(category + (++artifactCount));
                    }
                });
            }
        };

        var extension = project.getObjects().newInstance(DataFileCollection.class, publishCallback);
        configuration.getDependencies().add(depFactory.create(extension.getFiles()));

        return new DataFileCollectionWrapper(extension, configuration);
    }

    private <T extends Named> void setNamedAttribute(AttributeContainer attributes, Attribute<T> attribute, String value) {
        attributes.attribute(attribute, objectFactory.named(attribute.getType(), value));
    }

    private static <T extends Named> void setNamedAttribute(Project project, AttributeContainer attributes, Attribute<T> attribute, String value) {
        attributes.attribute(attribute, project.getObjects().named(attribute.getType(), value));
    }
}
