package net.neoforged.moddevgradle.internal;

import net.neoforged.minecraftdependencies.MinecraftDependenciesPlugin;
import net.neoforged.minecraftdependencies.MinecraftDistribution;
import net.neoforged.moddevgradle.dsl.DataFileCollection;
import net.neoforged.moddevgradle.dsl.InternalModelHelper;
import net.neoforged.moddevgradle.dsl.ModModel;
import net.neoforged.moddevgradle.dsl.NeoForgeExtension;
import net.neoforged.moddevgradle.dsl.RunModel;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import net.neoforged.moddevgradle.tasks.JarJar;
import net.neoforged.nfrtgradle.CreateMinecraftArtifacts;
import net.neoforged.nfrtgradle.DownloadAssets;
import net.neoforged.nfrtgradle.NeoFormRuntimePlugin;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.Named;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import javax.inject.Inject;
import java.io.File;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The main plugin class.
 */
public class ModDevPlugin implements Plugin<Project> {
    private static final Logger LOG = LoggerFactory.getLogger(ModDevPlugin.class);

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

        var ideIntegration = IdeIntegration.of(project, Branding.MDG);

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
        ideIntegration.runTaskOnProjectSync(extension.getIdeSyncTasks());
        var dependencyFactory = project.getDependencyFactory();

        // When a NeoForge version is specified, we use the dependencies published by that, and otherwise
        // we fall back to a potentially specified NeoForm version, which allows us to run in "Vanilla" mode.
        var neoForgeModDevLibrariesDependency = extension.getNeoForgeArtifact().map(artifactId -> {
            return dependencyFactory.create(artifactId)
                    .capabilities(caps -> {
                        caps.requireCapability("net.neoforged:neoforge-dependencies");
                    });
        }).orElse(extension.getNeoFormArtifact().map(artifact -> {
            return dependencyFactory.create(artifact)
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
            task.setGroup(Branding.MDG.internalTaskGroup());
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
                        extension.getNeoForgeArtifact().map(art -> {
                                    var split = art.split(":", 3);
                                    return split[1] + "-" + split[2];
                                })
                                .orElse(extension.getNeoFormArtifact().map(v -> "vanilla-" + v.split(":", 3)[2])),
                        (dir, prefix) -> dir.file(prefix + "-minecraft" + suffix + ".jar"));
            };
            task.getCompiledArtifact().set(jarPathFactory.apply(""));
            task.getCompiledWithSourcesArtifact().set(jarPathFactory.apply("-merged"));
            task.getSourcesArtifact().set(jarPathFactory.apply("-sources"));
            task.getResourcesArtifact().set(minecraftArtifactsDir.zip(
                    extension.getNeoForgeArtifact().map(art -> {
                                var split = art.split(":", 3);
                                return split[2] + "-" + split[1];
                            })
                            .orElse(extension.getNeoFormArtifact().map(v -> "vanilla-" + v.split(":", 3)[2])),
                    (dir, prefix) -> dir.file("client-extra-aka-minecraft-resources-" + prefix + ".jar")
            ));

            task.getNeoForgeArtifact().set(getNeoForgeUserDevDependencyNotation(extension));
            task.getNeoFormArtifact().set(getNeoFormDataDependencyNotation(extension));
            task.getAdditionalResults().putAll(extension.getAdditionalMinecraftArtifacts());
        });
        ideIntegration.runTaskOnProjectSync(createArtifacts);

        var downloadAssets = tasks.register("downloadAssets", DownloadAssets.class, task -> {
            // Not in the internal group in case someone wants to "preload" the asset before they go offline
            task.setGroup(Branding.MDG.publicTaskGroup());
            task.setDescription("Downloads the Minecraft assets and asset index needed to run a Minecraft client or generate client-side resources.");
            // While downloadAssets does not require *all* of the dependencies, it does need NeoForge/NeoForm to benefit
            // from any caching/overrides applied to these dependencies in Gradle
            for (var configuration : createManifestConfigurations) {
                task.addArtifactsToManifest(configuration);
            }
            task.getAssetPropertiesFile().set(modDevBuildDir.map(dir -> dir.file("minecraft_assets.properties")));
            task.getNeoForgeArtifact().set(getNeoForgeUserDevDependencyNotation(extension));
            task.getNeoFormArtifact().set(getNeoFormDataDependencyNotation(extension));
        });

        // For IntelliJ, we attach a combined sources+classes artifact which enables an "Attach Sources..." link for IJ users
        // Otherwise, attaching sources is a pain for IJ users.
        Provider<ConfigurableFileCollection> minecraftClassesArtifact;
        if (ideIntegration.shouldUseCombinedSourcesAndClassesArtifact()) {
            minecraftClassesArtifact = createArtifacts.map(task -> project.files(task.getCompiledWithSourcesArtifact()));
        } else {
            minecraftClassesArtifact = createArtifacts.map(task -> project.files(task.getCompiledArtifact()));
        }

        configurations.create(CONFIGURATION_RUNTIME_DEPENDENCIES, config -> {
            config.setDescription("The runtime dependencies to develop a mod for NeoForge, including Minecraft classes.");
            config.setCanBeResolved(false);
            config.setCanBeConsumed(false);

            config.getDependencies().addLater(minecraftClassesArtifact.map(dependencyFactory::create));
            config.getDependencies().addLater(createArtifacts.map(task -> project.files(task.getResourcesArtifact())).map(dependencyFactory::create));
            // Technically the Minecraft dependencies do not strictly need to be on the classpath because they are pulled from the legacy class path.
            // However, we do it anyway because this matches production environments, and allows launch proxies such as DevLogin to use Minecraft's libraries.
            config.getDependencies().addLater(neoForgeModDevLibrariesDependency);
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
            spec.getDependencies().addLater(extension.getNeoForgeArtifact().map(artifact -> {
                return dependencyFactory.create(artifact)
                        .capabilities(caps -> {
                            caps.requireCapability("net.neoforged:neoforge-moddev-config");
                        });
            }));
        });

        var additionalClasspath = configurations.create("additionalRuntimeClasspath", spec -> {
            spec.setDescription("Contains dependencies of every run, that should not be considered boot classpath modules.");
            spec.setCanBeResolved(true);
            spec.setCanBeConsumed(false);

            spec.getDependencies().addLater(neoForgeModDevLibrariesDependency);
            addClientResources(project, spec, createArtifacts);
        });

        // This defines the module path for runs
        // NOTE: When running in vanilla mode, this provider is undefined and will not result in an actual dependency
        var modulePathDependency = extension.getNeoForgeArtifact().map(artifactId -> {
            return dependencyFactory.create(artifactId)
                    .capabilities(caps -> {
                        caps.requireCapability("net.neoforged:neoforge-moddev-module-path");
                    })
                    // TODO: this is ugly; maybe make the configuration transitive in neoforge, or fix the SJH dep.
                    .exclude(Map.of("group", "org.jetbrains", "module", "annotations"));
        });

        setupRuns(
                project,
                Branding.MDG,
                modDevBuildDir,
                extension.getRuns(),
                userDevConfigOnly,
                modulePath -> modulePath.getDependencies().addLater(modulePathDependency),
                legacyClassPath -> legacyClassPath.extendsFrom(additionalClasspath),
                downloadAssets.flatMap(DownloadAssets::getAssetPropertiesFile),
                extension.getNeoFormVersion()
        );

        setupJarJar(project);

        configureTesting = () -> {
            // Weirdly enough, testCompileOnly extends from compileOnlyApi, and not compileOnly
            configurations.named(JavaPlugin.TEST_COMPILE_ONLY_CONFIGURATION_NAME).configure(configuration -> {
                configuration.getDependencies().addLater(minecraftClassesArtifact.map(dependencyFactory::create));
                configuration.getDependencies().addLater(neoForgeModDevLibrariesDependency);
            });

            var testFixtures = configurations.create("neoForgeTestFixtures", config -> {
                config.setDescription("Additional JUnit helpers provided by NeoForge");
                config.setCanBeResolved(false);
                config.setCanBeConsumed(false);
                config.getDependencies().addLater(extension.getNeoForgeArtifact().map(artifact -> {
                    return dependencyFactory.create(artifact)
                            .capabilities(caps -> {
                                caps.requireCapability("net.neoforged:neoforge-moddev-test-fixtures");
                            });
                }));
            });

            configurations.getByName(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME, files -> {
                files.extendsFrom(configurations.getByName(CONFIGURATION_RUNTIME_DEPENDENCIES));
                files.extendsFrom(testFixtures);
            });

            setupTestTask(
                    project,
                    Branding.MDG,
                    userDevConfigOnly,
                    tasks.named("test", Test.class),
                    extension.getUnitTest().getLoadedMods(),
                    extension.getUnitTest().getTestedMod(),
                    modDevBuildDir,
                    modulePath -> modulePath.getDependencies().addLater(modulePathDependency),
                    spec -> {
                        spec.getDependencies().addLater(neoForgeModDevLibrariesDependency);
                        addClientResources(project, spec, createArtifacts);
                    },
                    downloadAssets.flatMap(DownloadAssets::getAssetPropertiesFile)
            );
        };

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

    // FML searches for client resources on the legacy classpath
    private static void addClientResources(Project project, Configuration spec, TaskProvider<CreateMinecraftArtifacts> createArtifacts) {
        // FML searches for client resources on the legacy classpath
        spec.getDependencies().add(
                project.getDependencyFactory().create(
                        project.files(createArtifacts.flatMap(CreateMinecraftArtifacts::getResourcesArtifact))
                )
        );
    }

    private static Provider<String> getNeoFormDataDependencyNotation(NeoForgeExtension extension) {
        return extension.getNeoFormArtifact().map(art -> art + "@zip");
    }

    private static Provider<String> getNeoForgeUserDevDependencyNotation(NeoForgeExtension extension) {
        return extension.getNeoForgeArtifact().map(art -> art + ":userdev");
    }

    /**
     * Collects all dependencies needed by the NeoFormRuntime
     */
    private List<Configuration> configureArtifactManifestConfigurations(Project project, NeoForgeExtension extension) {
        var configurations = project.getConfigurations();
        var dependencyFactory = project.getDependencyFactory();

        var configurationPrefix = "neoFormRuntimeDependencies";

        Provider<ExternalModuleDependency> neoForgeDependency = extension.getNeoForgeArtifact().map(dependencyFactory::create);
        Provider<ExternalModuleDependency> neoFormDependency = extension.getNeoFormArtifact().map(dependencyFactory::create);

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
                setNamedAttribute(project, attributes, Category.CATEGORY_ATTRIBUTE, Category.DOCUMENTATION);
                setNamedAttribute(project, attributes, DocsType.DOCS_TYPE_ATTRIBUTE, DocsType.SOURCES);
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
            spec.getDependencies().addLater(extension.getNeoForgeArtifact().map(a -> a + ":universal").map(dependencyFactory::create)); // Universal Jar
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
                setNamedAttribute(attributes, Category.CATEGORY_ATTRIBUTE, Category.LIBRARY);
                setNamedAttribute(attributes, LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, LibraryElements.JAR);
            });
        });

        return List.of(neoForgeClassesAndData, neoForgeSources, compileClasspath, runtimeClasspath);
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
                    neoFormVersion
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
            Provider<String> neoFormVersion
    ) {
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

        var createLaunchScriptTask = tasks.register(InternalModelHelper.nameOfRun(run, "create", "launchScript"), CreateLaunchScriptTask.class, task -> {
            task.setGroup(branding.internalTaskGroup());
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
            task.getLaunchScript().set(RunUtils.getLaunchScript(argFileDir, run));
            task.getClasspathArgsFile().set(RunUtils.getArgFile(argFileDir, run, RunUtils.RunArgFile.CLASSPATH));
            task.getVmArgsFile().set(prepareRunTask.get().getVmArgsFile().map(d -> d.getAsFile().getAbsolutePath()));
            task.getProgramArgsFile().set(prepareRunTask.get().getProgramArgsFile().map(d -> d.getAsFile().getAbsolutePath()));
            task.getEnvironment().set(run.getEnvironment());
            task.getModFolders().set(RunUtils.getGradleModFoldersProvider(project, run.getLoadedMods(), null));
        });
        ideIntegration.runTaskOnProjectSync(createLaunchScriptTask);

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

    public void setupTestTask() {
        if (configureTesting == null) {
            throw new IllegalStateException("Unit testing was already enabled once!");
        }
        configureTesting.run();
        configureTesting = null;
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

    private static void setupJarJar(Project project) {
        SourceSetContainer sourceSets = ExtensionUtils.getExtension(project, "sourceSets", SourceSetContainer.class);
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
