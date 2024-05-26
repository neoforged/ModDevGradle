package net.neoforged.moddevgradle.internal;

import net.neoforged.moddevgradle.dsl.InternalModelHelper;
import net.neoforged.moddevgradle.dsl.NeoForgeExtension;
import net.neoforged.moddevgradle.dsl.Parchment;
import net.neoforged.moddevgradle.dsl.RunModel;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import net.neoforged.moddevgradle.internal.utils.StringUtils;
import net.neoforged.moddevgradle.tasks.JarJar;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.DefaultTaskExecutionRequest;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.Library;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.idea.model.IdeaProject;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.gradle.ext.Application;
import org.jetbrains.gradle.ext.IdeaExtPlugin;
import org.jetbrains.gradle.ext.ModuleRef;
import org.jetbrains.gradle.ext.ProjectSettings;
import org.jetbrains.gradle.ext.RunConfigurationContainer;
import org.slf4j.event.Level;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ModDevPlugin implements Plugin<Project> {
    private static final Attribute<String> ATTRIBUTE_DISTRIBUTION = Attribute.of("net.neoforged.distribution", String.class);
    private static final Attribute<String> ATTRIBUTE_OPERATING_SYSTEM = Attribute.of("net.neoforged.operatingsystem", String.class);

    private static final String JAR_JAR_GROUP = "jarjar";

    private static final String TASK_GROUP = "mod development";

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(JavaLibraryPlugin.class);
        var javaExtension = ExtensionUtils.getExtension(project, "java", JavaPluginExtension.class);

        project.getPlugins().apply(IdeaExtPlugin.class);
        var extension = project.getExtensions().create("neoForge", NeoForgeExtension.class);
        var dependencyFactory = project.getDependencyFactory();
        var neoForgeModDevLibrariesDependency = extension.getVersion().map(version -> {
            return dependencyFactory.create("net.neoforged:neoforge:" + version)
                    .capabilities(caps -> {
                        caps.requireCapability("net.neoforged:neoforge-dependencies");
                    });
        });

        var repositories = project.getRepositories();
        repositories.addLast(repositories.maven(repo -> {
            repo.setUrl(URI.create("https://maven.neoforged.net/releases/"));
        }));
        repositories.addLast(repositories.maven(repo -> {
            repo.setUrl(URI.create("https://libraries.minecraft.net/"));
            repo.metadataSources(sources -> sources.artifact());
            // TODO: Filter known groups that they ship and dont just run everything against it
        }));
        addTemporaryRepositories(repositories);

        var configurations = project.getConfigurations();
        // Configuration for all artifact that should be passed to NFRT for preventing repeated downloads
        var neoFormRuntimeArtifactManifestNeoForgeClasses = configurations.create("neoFormRuntimeArtifactManifestNeoForgeClasses", spec -> {
            spec.setCanBeConsumed(false);
            spec.setCanBeResolved(true);
            spec.withDependencies(dependencies -> {
                // Add the dep on NeoForge itself
                dependencies.addLater(extension.getVersion().map(version -> {
                    return dependencyFactory.create("net.neoforged:neoforge:" + version)
                            .capabilities(caps -> {
                                caps.requireCapability("net.neoforged:neoforge-moddev-bundle");
                            });
                }));
            });
        });
        // Configuration for all artifact that should be passed to NFRT for preventing repeated downloads
        var neoFormRuntimeArtifactManifestNeoForgeSources = configurations.create("neoFormRuntimeArtifactManifestNeoForgeSources", spec -> {
            spec.setCanBeConsumed(false);
            spec.setCanBeResolved(true);
            spec.withDependencies(dependencies -> {
                // Add the dep on NeoForge itself
                dependencies.addLater(extension.getVersion().map(version -> {
                    return dependencyFactory.create("net.neoforged:neoforge:" + version)
                            .attributes(attributes -> {
                                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.DOCUMENTATION));
                                attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, project.getObjects().named(DocsType.class, DocsType.SOURCES));
                            });
                }));
            });
        });
        var neoFormRuntimeArtifactManifestNeoForm = configurations.create("neoFormRuntimeArtifactManifestNeoForm", spec -> {
            spec.setCanBeConsumed(false);
            spec.setCanBeResolved(true);
            spec.withDependencies(dependencies -> {
                dependencies.addLater(extension.getVersion().map(version -> {
                    return dependencyFactory.create("net.neoforged:neoforge:" + version)
                            .capabilities(caps -> {
                                caps.requireCapability("net.neoforged:neoforge-dependencies");
                            });
                }));
            });
        });
        var neoFormRuntimeConfig = configurations.create("neoFormRuntime", files -> {
            files.setCanBeConsumed(false);
            files.setCanBeResolved(true);
            files.defaultDependencies(spec -> {
                spec.addLater(extension.getNeoFormRuntime().getVersion().map(version -> dependencyFactory.create("net.neoforged:neoform-runtime:" + version).attributes(attributes -> {
                    attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, project.getObjects().named(Bundling.class, Bundling.SHADOWED));
                })));
            });
        });

        // Create an access transformer configuration
        var accessTransformers = configurations.create("accessTransformers", files -> {
            files.setCanBeConsumed(false);
            files.setCanBeResolved(true);
            files.defaultDependencies(dependencies -> {
                dependencies.addLater(
                        extension.getAccessTransformers()
                                .map(project::files)
                                .map(dependencyFactory::create)
                );
            });
        });

        // This configuration will include the classpath needed to decompile and recompile Minecraft,
        // and has to include the libraries added by NeoForm and NeoForge.
        var minecraftCompileClasspath = configurations.create("minecraftCompileClasspath", spec -> {
            spec.setCanBeResolved(true);
            spec.setCanBeConsumed(false);
            spec.setVisible(false);
            spec.withDependencies(set -> set.addLater(neoForgeModDevLibrariesDependency));
            spec.attributes(attributes -> {
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_API));
                attributes.attribute(ATTRIBUTE_DISTRIBUTION, "client");
            });
        });

        var layout = project.getLayout();

        var tasks = project.getTasks();

        var createManifest = tasks.register("createArtifactManifest", CreateArtifactManifestTask.class, task -> {
            task.getNeoForgeModDevArtifacts().addAll(neoFormRuntimeArtifactManifestNeoForgeClasses.getIncoming().getArtifacts().getResolvedArtifacts().map(results -> {
                return results.stream().map(result -> {
                    var gav = guessMavenGav(result);
                    return new ArtifactManifestEntry(
                            gav,
                            result.getFile()
                    );
                }).collect(Collectors.toSet());
            }));
            task.getNeoForgeModDevArtifacts().addAll(neoFormRuntimeArtifactManifestNeoForgeSources.getIncoming().getArtifacts().getResolvedArtifacts().map(results -> {
                return results.stream().map(result -> {
                    var gav = guessMavenGav(result);
                    return new ArtifactManifestEntry(
                            gav,
                            result.getFile()
                    );
                }).collect(Collectors.toSet());
            }));
            task.getNeoForgeModDevArtifacts().addAll(neoFormRuntimeArtifactManifestNeoForm.getIncoming().getArtifacts().getResolvedArtifacts().map(results -> {
                return results.stream().map(result -> {
                    var gav = guessMavenGav(result);
                    return new ArtifactManifestEntry(
                            gav,
                            result.getFile()
                    );
                }).collect(Collectors.toSet());
            }));
            task.getManifestFile().set(layout.getBuildDirectory().file("neoform_artifact_manifest.properties"));
        });

        // Add a filtered parchment repository automatically if enabled
        var parchment = extension.getParchment();
        configureParchmentRepository(project, parchment);
        var parchmentData = configurations.create("parchmentData", spec -> {
            spec.setCanBeResolved(true);
            spec.setCanBeConsumed(false);
            spec.setTransitive(false); // Expect a single result
            spec.withDependencies(dependencies -> {
                dependencies.addLater(parchment.getParchmentArtifact().map(project.getDependencyFactory()::create));
            });
        });

        // it has to contain client-extra to be loaded by FML, and it must be added to the legacy CP
        var createArtifacts = tasks.register("createMinecraftArtifacts", CreateMinecraftArtifactsTask.class, task -> {
            var nfrtSettings = extension.getNeoFormRuntime();
            task.getVerbose().set(nfrtSettings.getVerbose());
            task.getEnableCache().set(nfrtSettings.getEnableCache());
            task.getAnalyzeCacheMisses().set(nfrtSettings.getAnalyzeCacheMisses());
            task.getUseEclipseCompiler().set(nfrtSettings.getUseEclipseCompiler());
            task.getArtifactManifestFile().set(createManifest.get().getManifestFile());
            task.getNeoForgeArtifact().set(extension.getVersion().map(version -> "net.neoforged:neoforge:" + version));
            task.getAccessTransformers().from(accessTransformers);
            task.getParchmentData().from(parchmentData);
            task.getNeoFormRuntime().from(neoFormRuntimeConfig);
            task.getCompileClasspath().from(minecraftCompileClasspath);
            task.getCompiledArtifact().set(layout.getBuildDirectory().file("repo/minecraft/neoforge-minecraft-joined/local/neoforge-minecraft-joined-local.jar"));
            task.getCompiledWithSourcesArtifact().set(layout.getBuildDirectory().file("repo/minecraft/neoforge-minecraft-joined/local/neoforge-minecraft-joined-local-merged.jar"));
            task.getSourcesArtifact().set(layout.getBuildDirectory().file("repo/minecraft/neoforge-minecraft-joined/local/neoforge-minecraft-joined-local-sources.jar"));
            task.getResourcesArtifact().set(layout.getBuildDirectory().file("repo/minecraft/neoforge-minecraft-joined/local/neoforge-minecraft-joined-local-resources-aka-client-extra.jar"));
        });
        var downloadAssets = tasks.register("downloadAssets", DownloadAssetsTask.class, task -> {
            task.getNeoForgeArtifact().set(extension.getVersion().map(version -> "net.neoforged:neoforge:" + version));
            task.getNeoFormRuntime().from(neoFormRuntimeConfig);
            task.getArtifactManifestFile().set(createManifest.get().getManifestFile());
            task.getAssetPropertiesFile().set(layout.getBuildDirectory().file("minecraft_assets.properties"));
        });

        // For IntelliJ, we attach a combined sources+classes artifact which enables an "Attach Sources..." link for IJ users
        // Otherwise, attaching sources is a pain for IJ users.
        Provider<ConfigurableFileCollection> minecraftClassesArtifact;
        if (shouldUseCombinedSourcesAndClassesArtifact()) {
            minecraftClassesArtifact = createArtifacts.map(task -> project.files(task.getCompiledWithSourcesArtifact()));
        } else {
            minecraftClassesArtifact = createArtifacts.map(task -> project.files(task.getCompiledArtifact()));
        }

        var localRuntime = configurations.create("neoForgeGeneratedArtifacts", config -> {
            config.setCanBeResolved(false);
            config.setCanBeConsumed(false);
            config.withDependencies(dependencies -> {
                dependencies.addLater(minecraftClassesArtifact.map(dependencyFactory::create));
                // Technically the Minecraft dependencies do not strictly need to be on the classpath because they are pulled from the legacy class path.
                // However, we do it anyway because this matches production environments, and allows launch proxies such as DevLogin to use Minecraft's libraries.
                dependencies.addLater(neoForgeModDevLibrariesDependency);
                dependencies.add(dependencyFactory.create(RunUtils.DEV_LAUNCH_GAV));
            });
        });

        project.getDependencies().attributesSchema(attributesSchema -> {
            attributesSchema.attribute(ATTRIBUTE_DISTRIBUTION).getDisambiguationRules().add(DistributionDisambiguation.class);
            attributesSchema.attribute(ATTRIBUTE_OPERATING_SYSTEM).getDisambiguationRules().add(OperatingSystemDisambiguation.class);
        });

        configurations.named("compileOnly").configure(configuration -> {
            configuration.withDependencies(dependencies -> {
                dependencies.addLater(minecraftClassesArtifact.map(dependencyFactory::create));
                dependencies.addLater(neoForgeModDevLibrariesDependency);
            });
        });
        configurations.named("runtimeClasspath", files -> files.extendsFrom(localRuntime));

        // Weirdly enough, testCompileOnly extends from compileOnlyApi, and not compileOnly
        configurations.named("testCompileOnly").configure(configuration -> {
            configuration.withDependencies(dependencies -> {
                dependencies.addLater(minecraftClassesArtifact.map(dependencyFactory::create));
                dependencies.addLater(neoForgeModDevLibrariesDependency);
            });
        });
        configurations.named("testRuntimeClasspath", files -> files.extendsFrom(localRuntime));

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
            spec.setCanBeResolved(true);
            spec.setCanBeConsumed(false);
            spec.setTransitive(false);
            spec.withDependencies(set -> set.addLater(extension.getVersion().map(version -> {
                return dependencyFactory.create("net.neoforged:neoforge:" + version)
                        .capabilities(caps -> {
                            caps.requireCapability("net.neoforged:neoforge-moddev-config");
                        });
            })));
        });
        var neoForgeModDevModules = project.getConfigurations().create("neoForgeModuleOnly", spec -> {
            spec.setCanBeResolved(true);
            spec.setCanBeConsumed(false);
            spec.withDependencies(set -> {
                set.addLater(extension.getVersion().map(version -> {
                    return dependencyFactory.create("net.neoforged:neoforge:" + version)
                            .capabilities(caps -> {
                                caps.requireCapability("net.neoforged:neoforge-moddev-module-path");
                            })
                            // TODO: this is ugly; maybe make the configuration transitive in neoforge, or fix the SJH dep.
                            .exclude(Map.of("group", "org.jetbrains", "module", "annotations"));
                }));
                set.add(dependencyFactory.create(RunUtils.DEV_LAUNCH_GAV));
            });
        });

        var ideSyncTask = tasks.register("neoForgeIdeSync");

        Map<RunModel, TaskProvider<PrepareRunForIde>> prepareRunTasks = new IdentityHashMap<>();
        extension.getRuns().configureEach(run -> {
            var type = RunUtils.getRequiredType(project, run);

            var sourceSet = ExtensionUtils.getExtension(project, "sourceSets", SourceSetContainer.class).getByName("main");

            var legacyClasspathConfiguration = configurations.create(InternalModelHelper.nameOfRun(run, "", "legacyClasspath"), spec -> {
                spec.setCanBeResolved(true);
                spec.setCanBeConsumed(false);
                spec.attributes(attributes -> {
                    attributes.attributeProvider(ATTRIBUTE_DISTRIBUTION, type.map(t -> t.equals("client") ? "client" : "server"));
                    attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
                });
                spec.withDependencies(set -> {
                    set.addLater(neoForgeModDevLibrariesDependency);
                });
                spec.extendsFrom(run.getAdditionalRuntimeClasspathConfiguration());
                spec.getDependencies().addAllLater(run.getAdditionalRuntimeClasspath().getDependencies());
            });

            var writeLcpTask = tasks.register(InternalModelHelper.nameOfRun(run, "write", "legacyClasspath"), WriteLegacyClasspath.class, writeLcp -> {
                writeLcp.getLegacyClasspathFile().convention(layout.getBuildDirectory().file("moddev/" + InternalModelHelper.nameOfRun(run, "", "legacyClasspath") + ".txt"));
                writeLcp.getEntries().from(legacyClasspathConfiguration);
                writeLcp.getEntries().from(createArtifacts.get().getResourcesArtifact());
            });

            var runDirectory = layout.getProjectDirectory().dir("run");
            var prepareRunTask = tasks.register(InternalModelHelper.nameOfRun(run, "prepare", "run"), PrepareRunForIde.class, task -> {
                task.getGameDirectory().set(runDirectory);
                task.getVmArgsFile().set(RunUtils.getArgFile(project, run, true));
                task.getProgramArgsFile().set(RunUtils.getArgFile(project, run, false));
                task.getRunType().set(run.getType());
                task.getNeoForgeModDevConfig().from(userDevConfigOnly);
                task.getModules().from(neoForgeModDevModules);
                task.getLegacyClasspathFile().set(writeLcpTask.get().getLegacyClasspathFile());
                task.getAssetProperties().set(downloadAssets.flatMap(DownloadAssetsTask::getAssetPropertiesFile));
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

            tasks.register(InternalModelHelper.nameOfRun(run, "run", ""), RunGameTask.class, task -> {
                task.setGroup(TASK_GROUP);

                // Launch with the Java version used in the project
                var toolchainService = ExtensionUtils.findExtension(project, "javaToolchains", JavaToolchainService.class);
                task.getJavaLauncher().set(toolchainService.launcherFor(spec -> spec.getLanguageVersion().set(javaExtension.getToolchain().getLanguageVersion())));
                // Note: this contains both the runtimeClasspath configuration and the sourceset's outputs.
                // This records a dependency on compiling and processing the resources of the source set.
                task.getClasspathProvider().from(sourceSet.getRuntimeClasspath());
                task.getGameDirectory().set(run.getGameDirectory());

                task.jvmArgs(RunUtils.getArgFileParameter(prepareRunTask.get().getVmArgsFile().get()).replace("\\", "\\\\"));
                task.getMainClass().set(RunUtils.DEV_LAUNCH_MAIN_CLASS);
                task.args(RunUtils.getArgFileParameter(prepareRunTask.get().getProgramArgsFile().get()).replace("\\", "\\\\"));
                // Of course we need the arg files to be up-to-date ;)
                task.dependsOn(prepareRunTask);

                task.getJvmArgumentProviders().add(RunUtils.getGradleModFoldersProvider(project, run));
            });
        });


        setupJarJar(project);

        setupTesting(project, userDevConfigOnly, neoForgeModDevModules, downloadAssets, ideSyncTask, createArtifacts, neoForgeModDevLibrariesDependency);

        configureIntelliJModel(project, ideSyncTask, extension, prepareRunTasks);

        configureEclipseModel(project, ideSyncTask, createArtifacts);
    }

    private static void configureParchmentRepository(Project project, Parchment parchment) {
        project.afterEvaluate(p -> {
            if (!parchment.getEnabled().get() || !parchment.getAddRepository().get()) {
                return;
            }
            MavenArtifactRepository repo = p.getRepositories().maven(m -> {
                m.setName("Parchment Data");
                m.setUrl(URI.create("https://maven.parchmentmc.org/"));
                m.mavenContent(mavenContent -> mavenContent.includeGroup("org.parchmentmc.data"));
            });
            // Make sure it comes first due to its filtered group, that should speed up resolution
            p.getRepositories().remove(repo);
            p.getRepositories().addFirst(repo);
        });
    }

    private static boolean shouldUseCombinedSourcesAndClassesArtifact() {
        return true;
        // return Boolean.getBoolean("idea.active");
    }

    private void addTemporaryRepositories(RepositoryHandler repositories) {

        repositories.maven(repo -> {
            repo.setName("Temporary Repo for minecraft-dependencies");
            repo.setUrl("https://prmaven.neoforged.net/GradleMinecraftDependencies/pr1");
            repo.content(content -> {
                content.includeModule("net.neoforged", "minecraft-dependencies");
            });
        });

        repositories.maven(repo -> {
            repo.setName("Temporary Repo for neoform");
            repo.setUrl("https://prmaven.neoforged.net/NeoForm/pr10");
            repo.content(content -> {
                content.includeModule("net.neoforged", "neoform");
            });
        });

        repositories.maven(repo -> {
            repo.setName("Temporary Repo for neoforge");
            repo.setUrl("https://prmaven.neoforged.net/NeoForge/pr959");
            repo.content(content -> {
                content.includeModule("net.neoforged", "neoforge");
            });
        });

    }

    private void setupTesting(Project project,
                              Configuration userDevConfigOnly,
                              Configuration neoForgeModDevModules,
                              TaskProvider<DownloadAssetsTask> downloadAssets,
                              TaskProvider<Task> ideSyncTask,
                              TaskProvider<CreateMinecraftArtifactsTask> createArtifacts,
                              Provider<ModuleDependency> neoForgeModDevLibrariesDependency) {
        var tasks = project.getTasks();
        var layout = project.getLayout();
        var configurations = project.getConfigurations();

        var legacyClasspathConfiguration = configurations.create("fmljunitLibraries", spec -> {
            spec.setCanBeResolved(true);
            spec.setCanBeConsumed(false);
            spec.attributes(attributes -> {
                attributes.attribute(ATTRIBUTE_DISTRIBUTION, "client");
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            });
            spec.withDependencies(set -> {
                set.addLater(neoForgeModDevLibrariesDependency);
            });
        });

        var writeLcpTask = tasks.register("writeFmlJunitClasspath", WriteLegacyClasspath.class, writeLcp -> {
            writeLcp.getLegacyClasspathFile().convention(layout.getBuildDirectory().file("moddev/fmljunitrunVmArgsLegacyClasspath.txt"));
            writeLcp.getEntries().from(legacyClasspathConfiguration);
            writeLcp.getEntries().from(createArtifacts.get().getResourcesArtifact());
        });

        var runDirectory = layout.getBuildDirectory().dir("fmljunitrun");
        var testVmArgsFile = layout.getBuildDirectory().file("moddev/fmljunitrunVmArgs.txt");
        var fmlJunitArgsFile = layout.getBuildDirectory().file("moddev/fmljunitrunProgramArgs.txt");
        var prepareRunTask = tasks.register("prepareFmlJunitFiles", PrepareArgsForTesting.class, task -> {
            task.getGameDirectory().set(runDirectory);
            task.getVmArgsFile().set(testVmArgsFile);
            task.getProgramArgsFile().set(fmlJunitArgsFile);
            task.getNeoForgeModDevConfig().from(userDevConfigOnly);
            task.getModules().from(neoForgeModDevModules);
            task.getLegacyClasspathFile().set(writeLcpTask.get().getLegacyClasspathFile());
            task.getAssetProperties().set(downloadAssets.flatMap(DownloadAssetsTask::getAssetPropertiesFile));
            task.getGameLogLevel().set(Level.INFO);
        });
        ideSyncTask.configure(task -> task.dependsOn(prepareRunTask));

        tasks.withType(Test.class).configureEach(task -> {
            task.dependsOn(prepareRunTask);

            // The FML JUnit plugin uses this system property to read a
            // file containing the program arguments needed to launch
            task.systemProperty("fml.junit.argsfile", RunUtils.escapeJvmArg(fmlJunitArgsFile.get().getAsFile().getAbsolutePath()));
            task.jvmArgs(RunUtils.escapeJvmArg(RunUtils.getArgFileParameter(testVmArgsFile.get())));
        });
    }

    private static void setupJarJar(Project project) {
        //var jarJar = project.getExtensions().create(JarJar.class, "jarJar", JarJarExtension.class);
        //((JarJarExtension) jarJar).createTaskAndConfiguration();

        SourceSetContainer sourceSets = ExtensionUtils.getExtension(project, "sourceSets", SourceSetContainer.class);
        sourceSets.configureEach(sourceSet -> {
            final Configuration configuration = project.getConfigurations().create(sourceSet.getTaskName(null, "jarJar"));
            configuration.setTransitive(false);
            // jarJar configurations should be resolvable, but ought not to be exposed to consumers;
            // as it has attributes, it could conflict with normal exposed configurations
            configuration.setCanBeResolved(true);
            configuration.setCanBeConsumed(false);

            var javaPlugin = project.getExtensions().getByType(JavaPluginExtension.class);

            configuration.attributes(attributes -> {
                // Unfortunately, while we can hopefully rely on disambiguation rules to get us some of these, others run
                // into issues. The target JVM version is the most worrying - we don't want to pull in a variant for a newer
                // jvm version. We could copy DefaultJvmFeature, and search for the target version of the compile task,
                // but this is difficult - we only have a feature name, not the linked source set. For this reason, we use
                // the toolchain version, which is the most likely to be correct.
                attributes.attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaPlugin.getToolchain().getLanguageVersion().map(JavaLanguageVersion::asInt));
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
                attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.getObjects().named(LibraryElements.class, LibraryElements.JAR));
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
                attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, project.getObjects().named(Bundling.class, Bundling.EXTERNAL));
            });

            var jarJarTask = project.getTasks().register(sourceSet.getTaskName(null, "jarJar"), JarJar.class, jarJar -> {
                jarJar.setGroup(JAR_JAR_GROUP);
                jarJar.setDescription("Create a combined JAR of project and selected dependencies");

                jarJar.configuration(configuration);
            });

            project.getTasks().withType(AbstractArchiveTask.class).named(name -> name.equals(sourceSet.getJarTaskName())).configureEach(task -> {
                task.from(jarJarTask.get().getOutputDirectory());
                task.dependsOn(jarJarTask);
            });
        });
    }

    private static void addIntelliJRunConfiguration(Project project,
                                                    RunConfigurationContainer runConfigurations,
                                                    @Nullable File outputDirectory,
                                                    RunModel run,
                                                    PrepareRunForIde prepareTask) {
        var a = new Application(StringUtils.capitalize(run.getName()), project);
        var sourceSets = ExtensionUtils.getExtension(project, "sourceSets", SourceSetContainer.class);
        a.setModuleRef(new ModuleRef(project, sourceSets.getByName("main")));
        a.setWorkingDirectory(run.getGameDirectory().get().getAsFile().getAbsolutePath());

        a.setJvmArgs(
                RunUtils.escapeJvmArg(RunUtils.getArgFileParameter(prepareTask.getVmArgsFile().get()))
                + " "
                + RunUtils.escapeJvmArg(RunUtils.getIdeaModFoldersProvider(project, outputDirectory, run).getArgument())
        );
        a.setMainClass(RunUtils.DEV_LAUNCH_MAIN_CLASS);
        a.setProgramParameters(RunUtils.escapeJvmArg(RunUtils.getArgFileParameter(prepareTask.getProgramArgsFile().get())));
        runConfigurations.add(a);
    }

    private static void configureIntelliJModel(Project project, TaskProvider<Task> ideSyncTask, NeoForgeExtension extension, Map<RunModel, TaskProvider<PrepareRunForIde>> prepareRunTasks) {
        // IDEA Sync has no real notion of tasks or providers or similar
        project.afterEvaluate(ignored -> {
            var settings = getIntelliJProjectSettings(project);
            if (settings != null && Boolean.getBoolean("idea.sync.active")) {
                // Also run the sync task directly as part of the sync. (Thanks Loom).
                var startParameter = project.getGradle().getStartParameter();
                var taskRequests = new ArrayList<>(startParameter.getTaskRequests());

                taskRequests.add(new DefaultTaskExecutionRequest(List.of(ideSyncTask.getName())));
                startParameter.setTaskRequests(taskRequests);
            }

            var runConfigurations = getIntelliJRunConfigurations(project); // TODO: Consider making this a value source

            if (runConfigurations == null) {
                project.getLogger().debug("Failed to find IntelliJ run configuration container. Not adding run configurations.");
            } else {
                var outputDirectory = RunUtils.getIntellijOutputDirectory(project);

                extension.getRuns().forEach(run -> {
                    var prepareTask = prepareRunTasks.get(run).get();
                    if (!prepareTask.getEnabled()) {
                        project.getLogger().lifecycle("Not creating IntelliJ run {} since its prepare task {} is disabled", run, prepareTask);
                        return;
                    }
                    addIntelliJRunConfiguration(project, runConfigurations, outputDirectory, run, prepareTask);
                });
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

    static String guessMavenGav(ResolvedArtifactResult result) {
        String artifactId;
        String ext = "";
        String classifier = null;
        if (result.getId() instanceof ModuleComponentArtifactIdentifier moduleId) {
            var artifact = moduleId.getComponentIdentifier().getModule();
            var version = moduleId.getComponentIdentifier().getVersion();
            var expectedBasename = artifact + "-" + version;
            var filename = result.getFile().getName();
            var startOfExt = filename.lastIndexOf('.');
            if (startOfExt != -1) {
                ext = filename.substring(startOfExt + 1);
                filename = filename.substring(0, startOfExt);
            }

            if (filename.startsWith(expectedBasename + "-")) {
                classifier = filename.substring((expectedBasename + "-").length());
            }
            artifactId = moduleId.getComponentIdentifier().getGroup() + ":" + artifact + ":" + version;
        } else {
            ext = "jar";
            artifactId = result.getId().getComponentIdentifier().toString();
        }
        String gav = artifactId;
        if (classifier != null) {
            gav += ":" + classifier;
        }
        if (!"jar".equals(ext)) {
            gav += "@" + ext;
        }
        return gav;
    }

    private static void configureEclipseModel(Project project,
                                              TaskProvider<Task> ideSyncTask,
                                              TaskProvider<CreateMinecraftArtifactsTask> createArtifacts) {
        // Set up stuff for Eclipse
        var eclipseModel = ExtensionUtils.findExtension(project, "eclipse", EclipseModel.class);
        if (eclipseModel == null) {
            return;
        }

        // Make sure our post-sync task runs on Eclipse
        eclipseModel.synchronizationTasks(ideSyncTask);

        // When using separate artifacts for classes and sources, link them
        if (!shouldUseCombinedSourcesAndClassesArtifact()) {
            var fileClasspath = eclipseModel.getClasspath().getFile();
            fileClasspath.whenMerged((org.gradle.plugins.ide.eclipse.model.Classpath classpath) -> {
                var classesPath = createArtifacts.get().getCompiledArtifact().get().getAsFile();
                var sourcesPath = createArtifacts.get().getSourcesArtifact().get().getAsFile();

                for (var entry : classpath.getEntries()) {
                    System.out.println(entry);
                    if (entry instanceof Library library && classesPath.equals(new File(library.getPath()))) {
                        library.setSourcePath(classpath.fileReference(sourcesPath));
                    }
                }
            });
        }

        // Set up runs
    }
}

