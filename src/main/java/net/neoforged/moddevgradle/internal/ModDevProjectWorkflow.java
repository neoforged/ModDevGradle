package net.neoforged.moddevgradle.internal;

import net.neoforged.minecraftdependencies.MinecraftDistribution;
import net.neoforged.moddevgradle.dsl.InternalModelHelper;
import net.neoforged.moddevgradle.dsl.ModModel;
import net.neoforged.moddevgradle.dsl.Parchment;
import net.neoforged.moddevgradle.dsl.RunModel;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import net.neoforged.nfrtgradle.CreateMinecraftArtifacts;
import net.neoforged.nfrtgradle.DownloadAssets;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.jetbrains.annotations.Nullable;
import org.slf4j.event.Level;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * After modding has been enabled, this will be attached as an extension to the project.
 */
public class ModDevProjectWorkflow {
    public static final String EXTENSION_NAME = "modDevProjectWorkflow";

    /**
     * This must be relative to the project directory since we can only set this to the same project-relative
     * directory across all subprojects due to IntelliJ limitations.
     */
    static final String JUNIT_GAME_DIR = "build/minecraft-junit";

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

    private static final String CONFIGURATION_ACCESS_TRANSFORMERS = "accessTransformers";
    private static final String CONFIGURATION_INTERFACE_INJECTION_DATA = "interfaceInjectionData";

    private final Project project;

    private final IdeIntegration ideIntegration;

    private final Provider<Directory> modDevBuildDir;
    private final Configuration compileDependencies;
    private final Configuration runtimeDependencies;
    private final ConfigurationContainer configurations;
    private final ProjectLayout layout;
    private final TaskContainer tasks;
    private final Branding branding;

    /**
     * @param gameLibrariesDependency A module dependency that represents the library dependencies of the game.
     *                                This module can be depended on with different usage attributes, which allow it
     *                                to expose different sets of libraries for use in compiling code or at runtime
     *                                (apiElements vs. runtimeElements).
     */
    public ModDevProjectWorkflow(Project project,
                                 Branding branding,
                                 @Nullable ModuleDependency moddingPlatformDependency,
                                 @Nullable String moddingPlatformDataDependencyNotation,
                                 @Nullable ModuleDependency modulePathDependency,
                                 @Nullable ModuleDependency runTypesConfigDependency,
                                 @Nullable ModuleDependency testFixturesDependency,
                                 @Nullable ModuleDependency recompilableMinecraftWorkflowDependency,
                                 @Nullable String recompilableMinecraftWorkflowDataDependencyNotation,
                                 String artifactFilenamePrefix,
                                 ModuleDependency gameLibrariesDependency,
                                 ModDevExtension extension) {
        this.project = project;
        this.configurations = project.getConfigurations();
        this.layout = project.getLayout();
        this.tasks = project.getTasks();
        this.branding = branding;

        var dependencyFactory = project.getDependencyFactory();

        var javaExtension = ExtensionUtils.getExtension(project, "java", JavaPluginExtension.class);

        ideIntegration = IdeIntegration.of(project, branding);

        // We use this directory to store intermediate files used during moddev
        modDevBuildDir = layout.getBuildDirectory().dir("moddev");

        var createManifestConfigurations = configureArtifactManifestConfigurations(
                moddingPlatformDependency,
                recompilableMinecraftWorkflowDependency
        );

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
            task.setGroup(branding.internalTaskGroup());
            task.setDescription("Creates the NeoForge and Minecraft artifacts by invoking NFRT.");
            for (var configuration : createManifestConfigurations) {
                task.addArtifactsToManifest(configuration);
            }

            task.getAccessTransformers().from(configurations.getByName(CONFIGURATION_ACCESS_TRANSFORMERS));
            task.getInterfaceInjectionData().from(configurations.getByName(CONFIGURATION_INTERFACE_INJECTION_DATA));
            task.getValidateAccessTransformers().set(extension.getValidateAccessTransformers());
            task.getParchmentData().from(parchmentData);
            task.getParchmentEnabled().set(parchment.getEnabled());
            task.getParchmentConflictResolutionPrefix().set(parchment.getConflictResolutionPrefix());

            var minecraftArtifactsDir = modDevBuildDir.map(dir -> dir.dir("artifacts"));
            Function<String, Provider<RegularFile>> jarPathFactory = suffix -> minecraftArtifactsDir.map(artifactDir -> {
                // It's helpful to be able to differentiate the Vanilla jar and the NeoForge jar in classic multiloader setups.
                return artifactDir.file(artifactFilenamePrefix + "-minecraft" + suffix + ".jar");
            });
            task.getCompiledArtifact().set(jarPathFactory.apply(""));
            task.getCompiledWithSourcesArtifact().set(jarPathFactory.apply("-merged"));
            task.getSourcesArtifact().set(jarPathFactory.apply("-sources"));
            task.getResourcesArtifact().set(minecraftArtifactsDir.map(dir -> {
                return dir.file("client-extra-aka-minecraft-resources-" + artifactFilenamePrefix + ".jar");
            }));

            task.getNeoForgeArtifact().set(moddingPlatformDataDependencyNotation);
            task.getNeoFormArtifact().set(recompilableMinecraftWorkflowDataDependencyNotation);
            task.getAdditionalResults().putAll(extension.getAdditionalMinecraftArtifacts());
        });
        ideIntegration.runTaskOnProjectSync(createArtifacts);

        var downloadAssets = tasks.register("downloadAssets", DownloadAssets.class, task -> {
            // Not in the internal group in case someone wants to "preload" the asset before they go offline
            task.setGroup(branding.publicTaskGroup());
            task.setDescription("Downloads the Minecraft assets and asset index needed to run a Minecraft client or generate client-side resources.");
            // While downloadAssets does not require *all* of the dependencies, it does need NeoForge/NeoForm to benefit
            // from any caching/overrides applied to these dependencies in Gradle
            for (var configuration : createManifestConfigurations) {
                task.addArtifactsToManifest(configuration);
            }
            task.getAssetPropertiesFile().set(modDevBuildDir.map(dir -> dir.file("minecraft_assets.properties")));
            task.getNeoForgeArtifact().set(moddingPlatformDataDependencyNotation);
            task.getNeoFormArtifact().set(recompilableMinecraftWorkflowDataDependencyNotation);
        });

        // For IntelliJ, we attach a combined sources+classes artifact which enables an "Attach Sources..." link for IJ users
        // Otherwise, attaching sources is a pain for IJ users.
        Provider<ConfigurableFileCollection> minecraftClassesArtifact;
        if (ideIntegration.shouldUseCombinedSourcesAndClassesArtifact()) {
            minecraftClassesArtifact = createArtifacts.map(task -> project.files(task.getCompiledWithSourcesArtifact()));
        } else {
            minecraftClassesArtifact = createArtifacts.map(task -> project.files(task.getCompiledArtifact()));
        }

        runtimeDependencies = configurations.create(CONFIGURATION_RUNTIME_DEPENDENCIES, config -> {
            config.setDescription("The runtime dependencies to develop a mod for NeoForge, including Minecraft classes.");
            config.setCanBeResolved(false);
            config.setCanBeConsumed(false);

            config.getDependencies().addLater(minecraftClassesArtifact.map(dependencyFactory::create));
            config.getDependencies().addLater(createArtifacts.map(task -> project.files(task.getResourcesArtifact())).map(dependencyFactory::create));
            // Technically the Minecraft dependencies do not strictly need to be on the classpath because they are pulled from the legacy class path.
            // However, we do it anyway because this matches production environments, and allows launch proxies such as DevLogin to use Minecraft's libraries.
            config.getDependencies().add(gameLibrariesDependency);
        });

        compileDependencies = configurations.create(CONFIGURATION_COMPILE_DEPENDENCIES, config -> {
            config.setDescription("The compile-time dependencies to develop a mod for NeoForge, including Minecraft classes.");
            config.setCanBeResolved(false);
            config.setCanBeConsumed(false);
            config.getDependencies().addLater(minecraftClassesArtifact.map(dependencyFactory::create));
            config.getDependencies().add(gameLibrariesDependency);
        });

        // Try to give people at least a fighting chance to run on the correct java version
        var toolchainSpec = javaExtension.getToolchain();
        try {
            toolchainSpec.getLanguageVersion().convention(JavaLanguageVersion.of(21));
        } catch (IllegalStateException e) {
            // We tried our best
        }

        // Let's try to get the userdev JSON out of the universal jar
        // I don't like having to use a configuration for this...
        @Nullable Configuration userDevConfigOnly;
        if (runTypesConfigDependency != null) {
            userDevConfigOnly = project.getConfigurations().create("neoForgeConfigOnly", spec -> {
                spec.setDescription("Resolves exclusively the NeoForge userdev JSON for configuring runs");
                spec.setCanBeResolved(true);
                spec.setCanBeConsumed(false);
                spec.setTransitive(false);
                spec.getDependencies().add(runTypesConfigDependency);
            });
        } else {
            userDevConfigOnly = null;
        }

        var additionalClasspath = configurations.create("additionalRuntimeClasspath", spec -> {
            spec.setDescription("Contains dependencies of every run, that should not be considered boot classpath modules.");
            spec.setCanBeResolved(true);
            spec.setCanBeConsumed(false);

            spec.getDependencies().add(gameLibrariesDependency);
            addClientResources(project, spec, createArtifacts);
        });

        setupRuns(
                project,
                branding,
                modDevBuildDir,
                extension.getRuns(),
                userDevConfigOnly,
                modulePath -> {
                    if (modulePathDependency != null) {
                        modulePath.getDependencies().add(modulePathDependency);
                    }
                },
                legacyClassPath -> legacyClassPath.extendsFrom(additionalClasspath),
                downloadAssets.flatMap(DownloadAssets::getAssetPropertiesFile),
                project.provider(() -> recompilableMinecraftWorkflowDependency).map(ModuleDependency::getVersion)
        );

        // For IDEs that support it, link the source/binary artifacts if we use separated ones
        if (!ideIntegration.shouldUseCombinedSourcesAndClassesArtifact()) {
            ideIntegration.attachSources(
                    Map.of(
                            createArtifacts.get().getCompiledArtifact(),
                            createArtifacts.get().getSourcesArtifact()
                    )
            );
        }
    }

    public void configureTesting() {
        // Weirdly enough, testCompileOnly extends from compileOnlyApi, and not compileOnly
        configurations.named(JavaPlugin.TEST_COMPILE_ONLY_CONFIGURATION_NAME).configure(configuration -> {
            configuration.getDependencies().addLater(minecraftClassesArtifact.map(dependencyFactory::create));
            configuration.getDependencies().add(gameLibrariesDependency);
        });

        // Test fixtures only exist when not running in Vanilla mode
        Configuration testFixtures;
        if (testFixturesDependency != null) {
            testFixtures = configurations.create("neoForgeTestFixtures", config -> {
                config.setDescription("Additional JUnit helpers provided by NeoForge");
                config.setCanBeResolved(false);
                config.setCanBeConsumed(false);
                config.getDependencies().add(testFixturesDependency);
            });
        } else {
            testFixtures = null;
        }

        configurations.getByName(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME, files -> {
            files.extendsFrom(configurations.getByName(CONFIGURATION_RUNTIME_DEPENDENCIES));
            if (testFixtures != null) {
                files.extendsFrom(testFixtures);
            }
        });

        setupTestTask(
                project,
                branding,
                userDevConfigOnly,
                tasks.named("test", Test.class),
                extension.getUnitTest().getLoadedMods(),
                extension.getUnitTest().getTestedMod(),
                modDevBuildDir,
                modulePath -> {
                    if (modulePathDependency != null) {
                        modulePath.getDependencies().add(modulePathDependency);
                    }
                },
                spec -> {
                    spec.getDependencies().add(gameLibrariesDependency);
                    addClientResources(project, spec, createArtifacts);
                },
                downloadAssets.flatMap(DownloadAssets::getAssetPropertiesFile)
        );
    }

    // FML searches for client resources on the legacy classpath
    private static void addClientResources(Project project, Configuration spec, TaskProvider<CreateMinecraftArtifacts> createArtifacts) {
        // FML searches for client resources on the legacy classpath
        spec.getDependencies().add(
                project.getDependencyFactory().create(
                        project.files(createArtifacts.flatMap(CreateMinecraftArtifacts::getResourcesArtifact))
                )
        );
    }

    /**
     * Collects all dependencies needed by the NeoFormRuntime
     */
    private List<Configuration> configureArtifactManifestConfigurations(
            @Nullable ModuleDependency moddingPlatformDependency,
            @Nullable ModuleDependency recompilableMinecraftWorkflowDependency
    ) {
        var configurations = project.getConfigurations();

        var configurationPrefix = "neoFormRuntimeDependencies";

        var result = new ArrayList<Configuration>();

        // Gradle prevents us from having dependencies with "incompatible attributes" in the same configuration.
        // What constitutes incompatible cannot be overridden on a per-configuration basis.
        var neoForgeClassesAndData = configurations.create(configurationPrefix + "NeoForgeClasses", spec -> {
            spec.setDescription("Dependencies needed for running NeoFormRuntime for the selected NeoForge/NeoForm version (NeoForge classes)");
            spec.setCanBeConsumed(false);
            spec.setCanBeResolved(true);
            if (moddingPlatformDependency != null) {
                spec.getDependencies().add(moddingPlatformDependency.copy()
                        .capabilities(caps -> caps.requireCapability("net.neoforged:neoforge-moddev-bundle")));
            }

            // This dependency is used when the NeoForm version is overridden or when we run in Vanilla-only mode
            if (recompilableMinecraftWorkflowDependency != null) {
                spec.getDependencies().add(recompilableMinecraftWorkflowDependency.copy()
                        .capabilities(caps -> caps.requireCapability("net.neoforged:neoform")));
            }
        });
        result.add(neoForgeClassesAndData);

        if (moddingPlatformDependency != null) {
            var neoForgeSources = configurations.create(configurationPrefix + "NeoForgeSources", spec -> {
                spec.setDescription("Dependencies needed for running NeoFormRuntime for the selected NeoForge/NeoForm version (NeoForge sources)");
                spec.setCanBeConsumed(false);
                spec.setCanBeResolved(true);
                spec.getDependencies().add(moddingPlatformDependency);
                spec.attributes(attributes -> {
                    setNamedAttribute(project, attributes, Category.CATEGORY_ATTRIBUTE, Category.DOCUMENTATION);
                    setNamedAttribute(project, attributes, DocsType.DOCS_TYPE_ATTRIBUTE, DocsType.SOURCES);
                });
            });
            result.add(neoForgeSources);
        }

        // Compile-time dependencies used by NeoForm, NeoForge and Minecraft.
        // Also includes any classes referenced by compiled Minecraft code (used by decompilers, renamers, etc.)
        var compileClasspath = configurations.create(configurationPrefix + "CompileClasspath", spec -> {
            spec.setDescription("Dependencies needed for running NeoFormRuntime for the selected NeoForge/NeoForm version (Classpath)");
            spec.setCanBeConsumed(false);
            spec.setCanBeResolved(true);
            if (moddingPlatformDependency != null) {
                spec.getDependencies().add(moddingPlatformDependency.copy()
                        .capabilities(caps -> caps.requireCapability("net.neoforged:neoforge-dependencies")));
            }
            if (recompilableMinecraftWorkflowDependency != null) {
                // This dependency is used when the NeoForm version is overridden or when we run in Vanilla-only mode
                spec.getDependencies().add(recompilableMinecraftWorkflowDependency.copy()
                        .capabilities(caps -> caps.requireCapability("net.neoforged:neoform-dependencies")));
            }
            spec.attributes(attributes -> {
                setNamedAttribute(attributes, Usage.USAGE_ATTRIBUTE, Usage.JAVA_API);
                setNamedAttribute(attributes, MinecraftDistribution.ATTRIBUTE, MinecraftDistribution.CLIENT);
            });
        });
        result.add(compileClasspath);

        // Runtime-time dependencies used by NeoForm, NeoForge and Minecraft.
        var runtimeClasspath = configurations.create(configurationPrefix + "RuntimeClasspath", spec -> {
            spec.setDescription("Dependencies needed for running NeoFormRuntime for the selected NeoForge/NeoForm version (Classpath)");
            spec.setCanBeConsumed(false);
            spec.setCanBeResolved(true);
            if (moddingPlatformDependency != null) {
                spec.getDependencies().add(moddingPlatformDependency); // Universal Jar
                spec.getDependencies().add(moddingPlatformDependency.copy()
                        .capabilities(caps -> caps.requireCapability("net.neoforged:neoforge-dependencies")));
            }
            // This dependency is used when the NeoForm version is overridden or when we run in Vanilla-only mode
            if (recompilableMinecraftWorkflowDependency != null) {
                spec.getDependencies().add(recompilableMinecraftWorkflowDependency.copy()
                        .capabilities(caps -> caps.requireCapability("net.neoforged:neoform-dependencies")));
            }
            spec.attributes(attributes -> {
                setNamedAttribute(attributes, Usage.USAGE_ATTRIBUTE, Usage.JAVA_RUNTIME);
                setNamedAttribute(attributes, MinecraftDistribution.ATTRIBUTE, MinecraftDistribution.CLIENT);
            });
        });
        result.add(runtimeClasspath);

        return result;
    }

    static void setupRuns(Project project,
                          Branding branding,
                          Provider<Directory> argFileDir,
                          DomainObjectCollection<RunModel> runs,
                          Object runTemplatesSourceFile,
                          Consumer<Configuration> configureModulePath,
                          Consumer<Configuration> configureLegacyClasspath,
                          Provider<RegularFile> assetPropertiesFile,
                          Provider<String> neoFormVersion
    ) {
        var ideIntegration = IdeIntegration.of(project, branding);

        // Create a configuration to resolve DevLaunch without leaking it to consumers
        var devLaunchConfig = project.getConfigurations().create("devLaunchConfig", spec -> {
            spec.setDescription("This configuration is used to inject DevLaunch into the runtime classpaths of runs.");
            spec.getDependencies().add(project.getDependencyFactory().create(RunUtils.DEV_LAUNCH_GAV));
        });

        // Create an empty task similar to "assemble" which can be used to generate all launch scripts at once
        var createLaunchScriptsTask = project.getTasks().register("createLaunchScripts", Task.class, task -> {
            task.setGroup(branding.publicTaskGroup());
            task.setDescription("Creates batch files/shell scripts to launch the game from outside of Gradle (i.e. Renderdoc, NVidia Nsight, etc.)");
        });

        Map<RunModel, TaskProvider<PrepareRun>> prepareRunTasks = new IdentityHashMap<>();
        runs.all(run -> {
            var prepareRunTask = setupRunInGradle(
                    project,
                    branding,
                    argFileDir,
                    run,
                    runTemplatesSourceFile,
                    configureModulePath,
                    configureLegacyClasspath,
                    assetPropertiesFile,
                    devLaunchConfig,
                    neoFormVersion,
                    createLaunchScriptsTask
            );
            prepareRunTasks.put(run, prepareRunTask);
        });
        ideIntegration.configureRuns(prepareRunTasks, runs);
    }

    /**
     * @param runTemplatesFile         See {@link ConfigurableFileCollection#from(Object...)}. This must ultimately resolve
     *                                 to a single file that is
     * @param configureLegacyClasspath Callback to add entries to the legacy classpath.
     * @param assetPropertiesFile      File that contains the asset properties file produced by NFRT.
     * @param createLaunchScriptsTask
     */
    private static TaskProvider<PrepareRun> setupRunInGradle(
            Project project,
            Branding branding,
            Provider<Directory> argFileDir,
            RunModel run,
            Object runTemplatesFile,
            Consumer<Configuration> configureModulePath,
            Consumer<Configuration> configureLegacyClasspath, // TODO: can be removed in favor of directly passing a configuration for the moddev libraries
            Provider<RegularFile> assetPropertiesFile,
            Configuration devLaunchConfig,
            Provider<String> neoFormVersion,
            TaskProvider<Task> createLaunchScriptsTask) {
        var ideIntegration = IdeIntegration.of(project, branding);
        var configurations = project.getConfigurations();
        var javaExtension = ExtensionUtils.getExtension(project, "java", JavaPluginExtension.class);
        var tasks = project.getTasks();

        var runtimeClasspathConfig = run.getSourceSet().map(SourceSet::getRuntimeClasspathConfigurationName)
                .map(configurations::getByName);

        // Sucks, but what can you do... Only at the end do we actually know which source set this run will use
        project.afterEvaluate(ignored -> {
            runtimeClasspathConfig.get().extendsFrom(devLaunchConfig);
        });

        var type = RunUtils.getRequiredType(project, run);

        var modulePathConfiguration = project.getConfigurations().create(InternalModelHelper.nameOfRun(run, "", "modulesOnly"), spec -> {
            spec.setDescription("Libraries that should be placed on the JVMs boot module path for run " + run.getName() + ".");
            spec.setCanBeResolved(true);
            spec.setCanBeConsumed(false);
            spec.shouldResolveConsistentlyWith(runtimeClasspathConfig.get());
            configureModulePath.accept(spec);
        });

        var legacyClasspathConfiguration = configurations.create(InternalModelHelper.nameOfRun(run, "", "legacyClasspath"), spec -> {
            spec.setDescription("Contains all dependencies of the " + run.getName() + " run that should not be considered boot classpath modules.");
            spec.setCanBeResolved(true);
            spec.setCanBeConsumed(false);
            spec.shouldResolveConsistentlyWith(runtimeClasspathConfig.get());
            spec.attributes(attributes -> {
                attributes.attributeProvider(MinecraftDistribution.ATTRIBUTE, type.map(t -> {
                    var name = t.equals("client") || t.equals("data") || t.equals("clientData") ? MinecraftDistribution.CLIENT : MinecraftDistribution.SERVER;
                    return project.getObjects().named(MinecraftDistribution.class, name);
                }));
                setNamedAttribute(project, attributes, Usage.USAGE_ATTRIBUTE, Usage.JAVA_RUNTIME);
            });
            configureLegacyClasspath.accept(spec);
            spec.extendsFrom(run.getAdditionalRuntimeClasspathConfiguration());
        });

        var writeLcpTask = tasks.register(InternalModelHelper.nameOfRun(run, "write", "legacyClasspath"), WriteLegacyClasspath.class, writeLcp -> {
            writeLcp.setGroup(branding.internalTaskGroup());
            writeLcp.setDescription("Writes the legacyClasspath file for the " + run.getName() + " Minecraft run, containing all dependencies that shouldn't be considered boot modules.");
            writeLcp.getLegacyClasspathFile().set(argFileDir.map(dir -> dir.file(InternalModelHelper.nameOfRun(run, "", "legacyClasspath") + ".txt")));
            writeLcp.addEntries(legacyClasspathConfiguration);
        });

        var prepareRunTask = tasks.register(InternalModelHelper.nameOfRun(run, "prepare", "run"), PrepareRun.class, task -> {
            task.setGroup(branding.internalTaskGroup());
            task.setDescription("Prepares all files needed to launch the " + run.getName() + " Minecraft run.");

            task.getGameDirectory().set(run.getGameDirectory());
            task.getVmArgsFile().set(RunUtils.getArgFile(argFileDir, run, RunUtils.RunArgFile.VMARGS));
            task.getProgramArgsFile().set(RunUtils.getArgFile(argFileDir, run, RunUtils.RunArgFile.PROGRAMARGS));
            task.getLog4jConfigFile().set(RunUtils.getArgFile(argFileDir, run, RunUtils.RunArgFile.LOG4J_CONFIG));
            task.getRunType().set(run.getType());
            task.getRunTypeTemplatesSource().from(runTemplatesFile);
            task.getModules().from(modulePathConfiguration);
            task.getLegacyClasspathFile().set(writeLcpTask.get().getLegacyClasspathFile());
            task.getAssetProperties().set(assetPropertiesFile);
            task.getSystemProperties().set(run.getSystemProperties().map(props -> {
                props = new HashMap<>(props);
                return props;
            }));
            task.getMainClass().set(run.getMainClass());
            task.getProgramArguments().set(run.getProgramArguments());
            task.getJvmArguments().set(run.getJvmArguments());
            task.getGameLogLevel().set(run.getLogLevel());
            task.getNeoFormVersion().set(neoFormVersion);
        });
        ideIntegration.runTaskOnProjectSync(prepareRunTask);

        var launchScriptTask = tasks.register(InternalModelHelper.nameOfRun(run, "create", "launchScript"), CreateLaunchScriptTask.class, task -> {
            task.setGroup(branding.internalTaskGroup());
            task.setDescription("Creates a bash/shell-script to launch the " + run.getName() + " Minecraft run from outside Gradle or your IDE.");

            task.getWorkingDirectory().set(run.getGameDirectory().map(d -> d.getAsFile().getAbsolutePath()));
            task.getRuntimeClasspath().setFrom(runtimeClasspathConfig);
            task.getLaunchScript().set(RunUtils.getLaunchScript(argFileDir, run));
            task.getClasspathArgsFile().set(RunUtils.getArgFile(argFileDir, run, RunUtils.RunArgFile.CLASSPATH));
            task.getVmArgsFile().set(prepareRunTask.get().getVmArgsFile().map(d -> d.getAsFile().getAbsolutePath()));
            task.getProgramArgsFile().set(prepareRunTask.get().getProgramArgsFile().map(d -> d.getAsFile().getAbsolutePath()));
            task.getEnvironment().set(run.getEnvironment());
            task.getModFolders().set(RunUtils.getGradleModFoldersProvider(project, run.getLoadedMods(), null));
        });
        createLaunchScriptsTask.configure(task -> task.dependsOn(launchScriptTask));

        tasks.register(InternalModelHelper.nameOfRun(run, "run", ""), RunGameTask.class, task -> {
            task.setGroup(branding.publicTaskGroup());
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

            task.getJvmArgumentProviders().add(RunUtils.getGradleModFoldersProvider(project, run.getLoadedMods(), null));
        });

        return prepareRunTask;
    }

    /**
     * @see #setupRunInGradle for a description of the parameters
     */
    static void setupTestTask(Project project,
                              Branding branding,
                              Object runTemplatesSourceFile,
                              TaskProvider<Test> testTask,
                              SetProperty<ModModel> loadedMods,
                              Property<ModModel> testedMod,
                              Provider<Directory> argFileDir,
                              Consumer<Configuration> configureModulePath,
                              Consumer<Configuration> configureLegacyClasspath,
                              Provider<RegularFile> assetPropertiesFile
    ) {
        var gameDirectory = new File(project.getProjectDir(), JUNIT_GAME_DIR);

        var ideIntegration = IdeIntegration.of(project, branding);

        var tasks = project.getTasks();
        var configurations = project.getConfigurations();

        var testRuntimeClasspath = configurations.getByName(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME);

        var neoForgeModDevModules = project.getConfigurations().create("neoForgeTestModules", spec -> {
            spec.setDescription("Libraries that should be placed on the JVMs boot module path for unit tests.");
            spec.setCanBeResolved(true);
            spec.setCanBeConsumed(false);
            spec.shouldResolveConsistentlyWith(testRuntimeClasspath);
            configureModulePath.accept(spec);
        });

        var legacyClasspathConfiguration = configurations.create("neoForgeTestLibraries", spec -> {
            spec.setDescription("Contains the legacy classpath of unit tests.");
            spec.setCanBeResolved(true);
            spec.setCanBeConsumed(false);
            spec.shouldResolveConsistentlyWith(testRuntimeClasspath);
            spec.attributes(attributes -> {
                setNamedAttribute(project, attributes, MinecraftDistribution.ATTRIBUTE, MinecraftDistribution.CLIENT);
                setNamedAttribute(project, attributes, Usage.USAGE_ATTRIBUTE, Usage.JAVA_RUNTIME);
            });
            configureLegacyClasspath.accept(spec);
        });

        // Place files for junit runtime in a subdirectory to avoid conflicting with other runs
        var runArgsDir = argFileDir.map(dir -> dir.dir("junit"));

        var writeLcpTask = tasks.register("writeNeoForgeTestClasspath", WriteLegacyClasspath.class, writeLcp -> {
            writeLcp.setGroup(branding.internalTaskGroup());
            writeLcp.setDescription("Writes the legacyClasspath file for the test run, containing all dependencies that shouldn't be considered boot modules.");
            writeLcp.getLegacyClasspathFile().convention(runArgsDir.map(dir -> dir.file("legacyClasspath.txt")));
            writeLcp.addEntries(legacyClasspathConfiguration);
        });

        var vmArgsFile = runArgsDir.map(dir -> dir.file("vmArgs.txt"));
        var programArgsFile = runArgsDir.map(dir -> dir.file("programArgs.txt"));
        var log4j2ConfigFile = runArgsDir.map(dir -> dir.file("log4j2.xml"));
        var prepareTask = tasks.register("prepareNeoForgeTestFiles", PrepareTest.class, task -> {
            task.setGroup(branding.internalTaskGroup());
            task.setDescription("Prepares all files needed to run the JUnit test task.");
            task.getGameDirectory().set(gameDirectory);
            task.getVmArgsFile().set(vmArgsFile);
            task.getProgramArgsFile().set(programArgsFile);
            task.getLog4jConfigFile().set(log4j2ConfigFile);
            task.getRunTypeTemplatesSource().from(runTemplatesSourceFile);
            task.getModules().from(neoForgeModDevModules);
            task.getLegacyClasspathFile().set(writeLcpTask.get().getLegacyClasspathFile());
            task.getAssetProperties().set(assetPropertiesFile);
            task.getGameLogLevel().set(Level.INFO);
        });

        // Ensure the test files are written on sync so that users who use IDE-only tests can run them
        ideIntegration.runTaskOnProjectSync(prepareTask);

        testTask.configure(task -> {
            task.dependsOn(prepareTask);

            // The FML JUnit plugin uses this system property to read a
            // file containing the program arguments needed to launch
            task.systemProperty("fml.junit.argsfile", programArgsFile.get().getAsFile().getAbsolutePath());
            task.jvmArgs(RunUtils.getArgFileParameter(vmArgsFile.get()));

            var modFoldersProvider = RunUtils.getGradleModFoldersProvider(project, loadedMods, testedMod);
            task.getJvmArgumentProviders().add(modFoldersProvider);
        });

        project.afterEvaluate(p -> {
            // Test tasks don't have a provider-based property for working directory, so we need to afterEvaluate it.
            testTask.configure(task -> task.setWorkingDir(gameDirectory));
        });

        ideIntegration.configureTesting(loadedMods, testedMod, runArgsDir, gameDirectory, programArgsFile, vmArgsFile);
    }

    public void addToSourceSet(SourceSet sourceSet) {
        configurations.getByName(sourceSet.getRuntimeClasspathConfigurationName()).extendsFrom(runtimeDependencies);
        configurations.getByName(sourceSet.getCompileClasspathConfigurationName()).extendsFrom(compileDependencies);
    }

    private <T extends Named> void setNamedAttribute(AttributeContainer attributes, Attribute<T> attribute, String value) {
        attributes.attribute(attribute, project.getObjects().named(attribute.getType(), value));
    }

    private static <T extends Named> void setNamedAttribute(Project project, AttributeContainer attributes, Attribute<T> attribute, String value) {
        attributes.attribute(attribute, project.getObjects().named(attribute.getType(), value));
    }
}
