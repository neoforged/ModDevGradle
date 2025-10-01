package net.neoforged.moddevgradle.internal;

import java.io.File;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.neoforged.minecraftdependencies.MinecraftDistribution;
import net.neoforged.moddevgradle.dsl.InternalModelHelper;
import net.neoforged.moddevgradle.dsl.ModModel;
import net.neoforged.moddevgradle.dsl.RunModel;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import net.neoforged.moddevgradle.internal.utils.VersionCapabilitiesInternal;
import net.neoforged.nfrtgradle.CreateMinecraftArtifacts;
import net.neoforged.nfrtgradle.DownloadAssets;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.testing.base.TestingExtension;
import org.jetbrains.annotations.Nullable;
import org.slf4j.event.Level;

/**
 * After modding has been enabled, this will be attached as an extension to the project.
 */
public class ModDevRunWorkflow {
    private static final String EXTENSION_NAME = "__internal_modDevRunWorkflow";

    /**
     * This must be relative to the project directory since we can only set this to the same project-relative
     * directory across all subprojects due to IntelliJ limitations.
     */
    static final String JUNIT_GAME_DIR = "build/minecraft-junit";

    private final Project project;
    private final Branding branding;
    @Nullable
    private final ModuleDependency modulePathDependency;
    @Nullable
    private final ModuleDependency testFixturesDependency;
    private final ModuleDependency gameLibrariesDependency;
    private final Configuration userDevConfigOnly;

    /**
     * @param gameLibrariesDependency A module dependency that represents the library dependencies of the game.
     *                                This module can be depended on with different usage attributes, which allow it
     *                                to expose different sets of libraries for use in compiling code or at runtime
     *                                (apiElements vs. runtimeElements).
     */
    private ModDevRunWorkflow(Project project,
            Branding branding,
            ModDevArtifactsWorkflow artifactsWorkflow,
            @Nullable ModuleDependency modulePathDependency,
            @Nullable ModuleDependency runTypesConfigDependency,
            @Nullable ModuleDependency testFixturesDependency,
            ModuleDependency gameLibrariesDependency,
            DomainObjectCollection<RunModel> runs,
            VersionCapabilitiesInternal versionCapabilities) {
        this.project = project;
        this.branding = branding;
        this.modulePathDependency = modulePathDependency;
        this.testFixturesDependency = testFixturesDependency;
        this.gameLibrariesDependency = gameLibrariesDependency;

        var configurations = project.getConfigurations();

        // Let's try to get the userdev JSON out of the universal jar
        // I don't like having to use a configuration for this...
        userDevConfigOnly = configurations.create("neoForgeConfigOnly", spec -> {
            spec.setDescription("Resolves exclusively the NeoForge userdev JSON for configuring runs");
            spec.setCanBeResolved(true);
            spec.setCanBeConsumed(false);
            spec.setTransitive(false);
            if (runTypesConfigDependency != null) {
                spec.getDependencies().add(runTypesConfigDependency);
            }
        });

        Consumer<Configuration> configureLegacyClasspath;
        if (versionCapabilities.legacyClasspath()) {
            var additionalClasspath = configurations.create("additionalRuntimeClasspath", spec -> {
                spec.setDescription("Contains dependencies of every run, that should not be considered boot classpath modules.");
                spec.setCanBeResolved(true);
                spec.setCanBeConsumed(false);

                spec.getDependencies().add(gameLibrariesDependency);
                addClientResources(project, spec, artifactsWorkflow.createArtifacts());
                if (!versionCapabilities.modLocatorRework()) {
                    // Forge expects to find the Forge and client-extra jar on the legacy classpath
                    // Newer FML versions also search for it on the java.class.path.
                    spec.getDependencies().addLater(artifactsWorkflow.minecraftClassesDependency());
                }
            });
            configureLegacyClasspath = legacyClassPath -> legacyClassPath.extendsFrom(additionalClasspath);
        } else {
            // Create the configuration but disallow adding anything to it, to notify users about potential mistakes.
            // We might decide to remove it entirely in the future
            var additionalClasspath = configurations.create("additionalRuntimeClasspath");
            forbidAdditionalRuntimeDependencies(additionalClasspath, versionCapabilities);

            configureLegacyClasspath = legacyClassPath -> {
                throw new IllegalStateException("There is no legacy classpath for Minecraft " + versionCapabilities.minecraftVersion());
            };
        }

        setupRuns(
                project,
                branding,
                artifactsWorkflow.modDevBuildDir(),
                runs,
                userDevConfigOnly,
                modulePath -> {
                    if (modulePathDependency != null) {
                        modulePath.getDependencies().add(modulePathDependency);
                    }
                },
                configureLegacyClasspath,
                artifactsWorkflow.downloadAssets().flatMap(DownloadAssets::getAssetPropertiesFile),
                versionCapabilities);
    }

    private static void forbidAdditionalRuntimeDependencies(Configuration configuration, VersionCapabilitiesInternal versionCapabilities) {
        // We cannot use withDependencies() since the configuration should never get resolved,
        // but we want to inform the user anyway.
        configuration.getDependencies().all(dependency -> {
            throw new IllegalStateException(String.format(
                    "Tried to add a dependency to configuration %s, but there is no additional classpath anymore for Minecraft %s. "
                            + "Add the dependency to a standard configuration such as implementation or runtimeOnly. Dependency: %s",
                    configuration,
                    versionCapabilities.minecraftVersion(),
                    dependency));
        });
    }

    public static ModDevRunWorkflow get(Project project) {
        var workflow = ExtensionUtils.findExtension(project, EXTENSION_NAME, ModDevRunWorkflow.class);
        if (workflow == null) {
            throw new InvalidUserCodeException("Please enable the modding plugin first by setting a version");
        }
        return workflow;
    }

    public static ModDevRunWorkflow create(Project project,
            Branding branding,
            ModDevArtifactsWorkflow artifactsWorkflow,
            DomainObjectCollection<RunModel> runs) {
        var dependencies = artifactsWorkflow.dependencies();
        var versionCapabilites = artifactsWorkflow.versionCapabilities();

        var workflow = new ModDevRunWorkflow(
                project,
                branding,
                artifactsWorkflow,
                dependencies.modulePathDependency(),
                dependencies.runTypesConfigDependency(),
                dependencies.testFixturesDependency(),
                dependencies.gameLibrariesDependency(),
                runs,
                versionCapabilites);

        project.getExtensions().add(EXTENSION_NAME, workflow);

        return workflow;
    }

    public void configureTesting(Provider<ModModel> testedMod, Provider<Set<ModModel>> loadedMods) {
        var testing = project.getExtensions().getByType(TestingExtension.class);
        var testSuite = (JvmTestSuite) testing.getSuites().getByName("test");
        var testSourceSet = testSuite.getSources();

        var artifactsWorkflow = ModDevArtifactsWorkflow.get(project);
        artifactsWorkflow.addToSourceSet(testSourceSet);

        var configurations = project.getConfigurations();

        // If test fixtures are available for the current workflow, add them to runtime only
        if (testFixturesDependency != null) {
            configurations.getByName(testSourceSet.getRuntimeOnlyConfigurationName(), configuration -> {
                configuration.getDependencies().add(testFixturesDependency);
            });
        }

        if (testSuite.getTargets().size() > 1) {
            // NOTE: We can implement support for multiple test tasks later if someone is adamant about it
            throw new InvalidUserCodeException("MDG currently only supports test suites with a single test task.");
        }

        for (var target : testSuite.getTargets()) {
            setupTestTask(
                    project,
                    branding,
                    userDevConfigOnly,
                    target.getTestTask(),
                    loadedMods,
                    testedMod,
                    artifactsWorkflow.modDevBuildDir(),
                    modulePath -> {
                        if (modulePathDependency != null) {
                            modulePath.getDependencies().add(modulePathDependency);
                        }
                    },
                    legacyClassPath -> {
                        legacyClassPath.getDependencies().add(gameLibrariesDependency);
                        addClientResources(project, legacyClassPath, artifactsWorkflow.createArtifacts());
                    },
                    artifactsWorkflow.downloadAssets().flatMap(DownloadAssets::getAssetPropertiesFile),
                    artifactsWorkflow.versionCapabilities());
        }
    }

    // FML searches for client resources on the legacy classpath
    private static void addClientResources(Project project, Configuration spec, TaskProvider<CreateMinecraftArtifacts> createArtifacts) {
        spec.getDependencies().add(
                project.getDependencyFactory().create(
                        project.files(createArtifacts.flatMap(CreateMinecraftArtifacts::getResourcesArtifact))));
    }

    public static void setupRuns(
            Project project,
            Branding branding,
            Provider<Directory> argFileDir,
            DomainObjectCollection<RunModel> runs,
            Object runTemplatesSourceFile,
            Consumer<Configuration> configureModulePath,
            Consumer<Configuration> configureLegacyClasspath,
            Provider<RegularFile> assetPropertiesFile,
            VersionCapabilitiesInternal versionCapabilities) {
        var dependencyFactory = project.getDependencyFactory();
        var ideIntegration = IdeIntegration.of(project, branding);

        // Create a configuration to resolve DevLaunch and DevLogin without leaking them to consumers
        var supplyDevLogin = project.provider(() -> runs.stream().anyMatch(model -> model.getDevLogin().get()));
        var devLaunchConfig = project.getConfigurations().create("devLaunchConfig", spec -> {
            spec.setDescription("This configuration is used to inject DevLaunch and optionally DevLogin into the runtime classpaths of runs.");
            spec.getDependencies().add(dependencyFactory.create(RunUtils.DEV_LAUNCH_GAV));
            spec.getDependencies().addAllLater(supplyDevLogin.map(
                    supply -> supply ? List.of(dependencyFactory.create(RunUtils.DEV_LOGIN_GAV)) : List.of()));
        });

        // Create an empty task similar to "assemble" which can be used to generate all launch scripts at once
        var createLaunchScriptsTask = project.getTasks().register("createLaunchScripts", Task.class, task -> {
            task.setGroup(branding.publicTaskGroup());
            task.setDescription("Creates batch files/shell scripts to launch the game from outside of Gradle (i.e. Renderdoc, NVidia Nsight, etc.)");
        });

        Map<RunModel, TaskProvider<PrepareRun>> prepareRunTasks = new IdentityHashMap<>();
        runs.all(run -> {
            if (!versionCapabilities.modLocatorRework()) {
                // TODO: do this properly now that we have a flag in the version capabilities
                // This will explicitly be replaced in RunUtils to make this work for IDEs
                run.getEnvironment().put("MOD_CLASSES", RunUtils.getGradleModFoldersProvider(project, run.getLoadedMods(), null).getClassesArgument());
            }
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
                    versionCapabilities,
                    createLaunchScriptsTask);
            prepareRunTasks.put(run, prepareRunTask);
        });
        ideIntegration.configureRuns(prepareRunTasks, runs);
    }

    /**
     * @param runTemplatesFile         See {@link ConfigurableFileCollection#from(Object...)}. This must ultimately resolve
     *                                 to a single file that is
     * @param configureLegacyClasspath Callback to add entries to the legacy classpath.
     * @param assetPropertiesFile      File that contains the asset properties file produced by NFRT.
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
            VersionCapabilitiesInternal versionCapabilities,
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

        Provider<RegularFile> legacyClasspathFile;
        if (versionCapabilities.legacyClasspath()) {
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
            legacyClasspathFile = writeLcpTask.get().getLegacyClasspathFile();
        } else {
            // Disallow adding dependencies to the additional classpath configuration since it would have no effect.
            forbidAdditionalRuntimeDependencies(run.getAdditionalRuntimeClasspathConfiguration(), versionCapabilities);
            legacyClasspathFile = null;
        }

        var prepareRunTask = tasks.register(InternalModelHelper.nameOfRun(run, "prepare", "run"), PrepareRun.class, task -> {
            task.setGroup(branding.internalTaskGroup());
            task.setDescription("Prepares all files needed to launch the " + run.getName() + " Minecraft run.");

            task.getGameDirectory().set(run.getGameDirectory());
            task.getVmArgsFile().set(RunUtils.getArgFile(argFileDir, run, RunUtils.RunArgFile.VMARGS));
            task.getProgramArgsFile().set(RunUtils.getArgFile(argFileDir, run, RunUtils.RunArgFile.PROGRAMARGS));
            task.getLog4jConfigFileOverride().set(run.getLoggingConfigFile());
            task.getLog4jConfigFile().set(RunUtils.getArgFile(argFileDir, run, RunUtils.RunArgFile.LOG4J_CONFIG));
            task.getRunType().set(run.getType());
            task.getRunTypeTemplatesSource().from(runTemplatesFile);
            task.getModules().from(modulePathConfiguration);
            if (legacyClasspathFile != null) {
                task.getLegacyClasspathFile().set(legacyClasspathFile);
            }
            task.getAssetProperties().set(assetPropertiesFile);
            task.getSystemProperties().set(run.getSystemProperties().map(props -> {
                props = new HashMap<>(props);
                return props;
            }));
            task.getMainClass().set(run.getMainClass());
            task.getProgramArguments().set(run.getProgramArguments());
            task.getJvmArguments().set(run.getJvmArguments());
            task.getGameLogLevel().set(run.getLogLevel());
            task.getDevLogin().set(run.getDevLogin());
            task.getVersionCapabilities().set(versionCapabilities);
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
            Provider<Set<ModModel>> loadedMods,
            Provider<ModModel> testedMod,
            Provider<Directory> argFileDir,
            Consumer<Configuration> configureModulePath,
            Consumer<Configuration> configureLegacyClasspath,
            Provider<RegularFile> assetPropertiesFile,
            VersionCapabilitiesInternal versionCapabilities) {
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

        // Place files for junit runtime in a subdirectory to avoid conflicting with other runs
        var runArgsDir = argFileDir.map(dir -> dir.dir("junit"));

        Provider<RegularFile> legacyClasspathFile;
        if (versionCapabilities.legacyClasspath()) {
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

            var writeLcpTask = tasks.register("writeNeoForgeTestClasspath", WriteLegacyClasspath.class, writeLcp -> {
                writeLcp.setGroup(branding.internalTaskGroup());
                writeLcp.setDescription("Writes the legacyClasspath file for the test run, containing all dependencies that shouldn't be considered boot modules.");
                writeLcp.getLegacyClasspathFile().convention(runArgsDir.map(dir -> dir.file("legacyClasspath.txt")));
                writeLcp.addEntries(legacyClasspathConfiguration);
            });
            legacyClasspathFile = writeLcpTask.get().getLegacyClasspathFile();
        } else {
            legacyClasspathFile = null;
        }

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
            if (legacyClasspathFile != null) {
                task.getLegacyClasspathFile().set(legacyClasspathFile);
            }
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

    private static <T extends Named> void setNamedAttribute(Project project, AttributeContainer attributes, Attribute<T> attribute, String value) {
        attributes.attribute(attribute, project.getObjects().named(attribute.getType(), value));
    }
}
