package net.neoforged.neoforgegradle;

import net.neoforged.neoforgegradle.dsl.NeoForgeExtension;
import net.neoforged.neoforgegradle.internal.utils.ExtensionUtils;
import net.neoforged.neoforgegradle.internal.utils.StringUtils;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.process.CommandLineArgumentProvider;
import org.jetbrains.gradle.ext.Application;
import org.jetbrains.gradle.ext.GradleTask;
import org.jetbrains.gradle.ext.IdeaExtPlugin;
import org.jetbrains.gradle.ext.ModuleRef;
import org.jetbrains.gradle.ext.ProjectSettings;
import org.jetbrains.gradle.ext.RunConfiguration;
import org.jetbrains.gradle.ext.TaskTriggersConfig;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ModDevPluginImpl {
    private static final Attribute<String> ATTRIBUTE_DISTRIBUTION = Attribute.of("net.neoforged.distribution", String.class);
    private static final Attribute<String> ATTRIBUTE_OPERATING_SYSTEM = Attribute.of("net.neoforged.operatingsystem", String.class);

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
        repositories.addLast(repositories.maven(repo -> {
            repo.setUrl(project.getLayout().getBuildDirectory().map(dir -> dir.dir("repo").getAsFile().getAbsolutePath()));
            repo.metadataSources(sources -> sources.mavenPom());
        }));
        repositories.add(repositories.mavenLocal()); // TODO TEMP

        var configurations = project.getConfigurations();
        var neoForgeModDev = configurations.create("neoForgeModDev", files -> {
            files.setCanBeConsumed(false);
            files.setCanBeResolved(true);
            //files.defaultDependencies(spec -> spec.addLater(neoForgeUserdevDependency));
        });
        var neoFormInABoxConfig = configurations.create("neoFormInABoxConfig", files -> {
            files.setCanBeConsumed(false);
            files.setCanBeResolved(true);
            files.defaultDependencies(spec -> {
                spec.add(dependencyFactory.create("net.neoforged:NeoFormInABox:1.0-SNAPSHOT").attributes(attributes -> {
                    attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, project.getObjects().named(Bundling.class, Bundling.SHADOWED));
                }));
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
                attributes.attribute(ATTRIBUTE_OPERATING_SYSTEM, "windows");
            });
        });

        var layout = project.getLayout();

        var tasks = project.getTasks();

        var createManifest = tasks.register("createArtifactManifest", CreateArtifactManifestTask.class, task -> {
            task.getNeoForgeModDevArtifacts().set(neoForgeModDev.getIncoming().getArtifacts().getResolvedArtifacts().map(results -> {
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

        // it has to contain client-extra to be loaded by FML, and it must be added to the legacy CP
        var createArtifacts = tasks.register("createMinecraftArtifacts", CreateMinecraftArtifactsTask.class, task -> {
            task.getVerbose().set(extension.getVerbose());
            task.getEnableCache().set(extension.getEnableCache());
            task.getArtifactManifestFile().set(createManifest.get().getManifestFile());
            task.getNeoForgeArtifact().set(extension.getVersion().map(version -> "net.neoforged:neoforge:" + version));
            task.getNeoFormInABox().from(neoFormInABoxConfig);
            task.getCompileClasspath().from(minecraftCompileClasspath);
            task.getCompiledArtifact().set(layout.getBuildDirectory().file("repo/minecraft/neoforge-minecraft-joined/local/neoforge-minecraft-joined-local.jar"));
            task.getSourcesArtifact().set(layout.getBuildDirectory().file("repo/minecraft/neoforge-minecraft-joined/local/neoforge-minecraft-joined-local-sources.jar"));
            task.getResourcesArtifact().set(layout.getBuildDirectory().file("repo/minecraft/neoforge-minecraft-joined/local/neoforge-minecraft-joined-local-resources-aka-client-extra.jar"));
            task.getDummyArtifact().set(layout.getBuildDirectory().file("dummy_artifact.jar"));
        });
        var downloadAssets = tasks.register("downloadAssets", DownloadAssetsTask.class, task -> {
            task.getNeoForgeArtifact().set(extension.getVersion().map(version -> "net.neoforged:neoforge:" + version));
            task.getNeoFormInABox().from(neoFormInABoxConfig);
            task.getAssetPropertiesFile().set(layout.getBuildDirectory().file("minecraft_assets.properties"));
        });

        createDummyFilesInLocalRepository(layout);

        // This is an empty, but otherwise valid jar file that creates an implicit dependency on the task
        // creating our repo, while not creating duplicates on the classpath.
        var minecraftDummyArtifact = createArtifacts.map(task -> project.files(task.getDummyArtifact()));

        var localRuntime = configurations.create("neoForgeGeneratedArtifacts", config -> {
            config.setCanBeResolved(false);
            config.setCanBeConsumed(false);
        });
        project.getDependencies().add(localRuntime.getName(), "minecraft:neoforge-minecraft-joined:local");
        project.getDependencies().addProvider(localRuntime.getName(), minecraftDummyArtifact);
        project.getDependencies().add("compileOnly", "minecraft:neoforge-minecraft-joined:local");
        project.getDependencies().addProvider("compileOnly", minecraftDummyArtifact);
        configurations.named("runtimeClasspath", files -> files.extendsFrom(localRuntime));

        project.getDependencies().attributesSchema(attributesSchema -> {
            attributesSchema.attribute(ATTRIBUTE_DISTRIBUTION);
            attributesSchema.attribute(ATTRIBUTE_OPERATING_SYSTEM);
            attributesSchema.attribute(Category.CATEGORY_ATTRIBUTE);
        });

        configurations.named("compileOnly").configure(configuration -> {
            configuration.getDependencies().addLater(neoForgeModDevLibrariesDependency);
        });

        configurations.named("runtimeClasspath").configure(configuration -> {
            configuration.attributes(attributes -> {
                attributes.attribute(ATTRIBUTE_DISTRIBUTION, "client");
                attributes.attribute(ATTRIBUTE_OPERATING_SYSTEM, "windows");
            });
        });
        configurations.named("compileClasspath").configure(configuration -> {
            configuration.attributes(attributes -> {
                attributes.attribute(ATTRIBUTE_DISTRIBUTION, "client");
                attributes.attribute(ATTRIBUTE_OPERATING_SYSTEM, "windows");
            });
        });

        // Try to give people at least a fighting chance to run on the correct java version
        project.afterEvaluate(ignored -> {
            var toolchainSpec = javaExtension.getToolchain();
            try {
                toolchainSpec.getLanguageVersion().convention(JavaLanguageVersion.of(21));
            } catch (IllegalStateException ignoredException) {
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
            spec.withDependencies(set -> set.addLater(extension.getVersion().map(version -> {
                return dependencyFactory.create("net.neoforged:neoforge:" + version)
                        .capabilities(caps -> {
                            caps.requireCapability("net.neoforged:neoforge-moddev-module-path");
                        })
                        // TODO: this is ugly; maybe make the configuration transitive in neoforge, or fix the SJH dep.
                        .exclude(Map.of("group", "org.jetbrains", "module", "annotations"));
            })));
        });

        var idePostSyncTask = tasks.register("idePostSync");

        extension.getRuns().configureEach(run -> {
            var legacyClasspathConfiguration = configurations.create(run.nameOf("", "legacyClasspath"), spec -> {
                spec.setCanBeResolved(true);
                spec.setCanBeConsumed(false);
                spec.attributes(attributes -> {
                    attributes.attributeProvider(ATTRIBUTE_DISTRIBUTION, run.getType().map(t -> t.equals("client") ? "client" : "server"));
                    attributes.attribute(ATTRIBUTE_OPERATING_SYSTEM, "windows"); // TODO: don't hardcode current OS like that :D
                    attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
                });
                spec.withDependencies(set -> {
                    set.addLater(neoForgeModDevLibrariesDependency);
                });
            });

            var writeLcpTask = tasks.register(run.nameOf("write", "legacyClasspath"), WriteLegacyClasspath.class, writeLcp -> {
                writeLcp.getLegacyClasspathFile().convention(layout.getBuildDirectory().file("moddev/" + run.nameOf("", "legacyClasspath") + ".txt"));
                writeLcp.getEntries().from(legacyClasspathConfiguration);
                writeLcp.getEntries().from(createArtifacts.get().getResourcesArtifact());
            });

            var runDirectory = layout.getProjectDirectory().dir("run");
            var argsFile = layout.getBuildDirectory().file("moddev/" + run.nameOf("", "runArgs") + ".txt");
            var writeArgsFile = tasks.register(run.nameOf("prepare", "run"), PrepareRunForIde.class, task -> {
                task.getRunDirectory().set(runDirectory);
                task.getArgsFile().set(argsFile);
                task.getRunType().set(run.getType());
                task.getNeoForgeModDevConfig().from(userDevConfigOnly);
                task.getModules().from(neoForgeModDevModules);
                task.getLegacyClasspathFile().set(writeLcpTask.get().getLegacyClasspathFile());
                task.getAssetProperties().set(downloadAssets.flatMap(DownloadAssetsTask::getAssetPropertiesFile));
            });
            idePostSyncTask.configure(task -> task.dependsOn(writeArgsFile));

            tasks.register(run.nameOf("run", ""), RunGameTask.class, task -> {
                task.getClasspathProvider().from(configurations.named("runtimeClasspath"));
                task.getGameDirectory().set(project.file("run/"));
                // This should record a dependency ;)
                task.getMainClass().set(writeArgsFile.flatMap(PrepareRunForIde::getArgsFile).map(f -> "@" + f.getAsFile().getAbsolutePath()));

                var modFoldersProvider = project.getObjects().newInstance(ModFoldersProvider.class);
                modFoldersProvider.getModFolders().set(run.getMods().map(mods -> mods.stream()
                        .collect(Collectors.toMap(mod -> mod.getName(), mod -> {
                            var modFolder = project.getObjects().newInstance(ModFolder.class);
                            for (var sourceSet : mod.getModSourceSets().get()) {
                                modFolder.getFolders().from(sourceSet.getOutput().getClassesDirs());
                                modFolder.getFolders().from(sourceSet.getOutput().getResourcesDir());
                            }
                            return modFolder;
                        }))));
                task.getJvmArgumentProviders().add(modFoldersProvider);

                // TODO: how do we do this in a clean way for all source sets?
                task.dependsOn(tasks.named("processResources"));
            });

            IdeaModel ideaModel = ((IdeaModel) project.getExtensions().findByName("idea"));

            if (ideaModel != null && ideaModel.getProject() != null) {
                var settings = ((ExtensionAware) ideaModel.getProject()).getExtensions().getByType(ProjectSettings.class);
                var runConfigurations = (NamedDomainObjectContainer<RunConfiguration>)
                        ((ExtensionAware) settings).getExtensions().getByName("runConfigurations");

                Application a = new Application(StringUtils.capitalize(run.getName()), project);
                //
                //a.setModuleName(String.format("%s.main", template.projectName));
                var sourceSets = ExtensionUtils.getExtension(project, "sourceSets", SourceSetContainer.class);
                a.setModuleRef(new ModuleRef(project, sourceSets.getByName("main")));
                a.setWorkingDirectory(runDirectory.getAsFile().getAbsolutePath());
                a.setMainClass("@" + argsFile.get().getAsFile().getAbsolutePath());
                a.getBeforeRun().create("Prepare", GradleTask.class, gradleTask -> {
                    gradleTask.setTask(writeArgsFile.get());
                });
                runConfigurations.add(a);
            }
        });

        // IDEA Sync has no real notion of tasks or providers or similar
        project.afterEvaluate(ignored -> {
            IdeaModel ideaModel = ((IdeaModel) project.getExtensions().findByName("idea"));

            if (ideaModel == null || ideaModel.getProject() == null) {
                return;
            }

            var settings = ((ExtensionAware) ideaModel.getProject()).getExtensions().getByType(ProjectSettings.class);

            var taskTriggers = ((ExtensionAware) settings).getExtensions().getByType(TaskTriggersConfig.class);
            // Careful, this will overwrite on intellij (and append on eclipse, but we aren't there yet!)
            taskTriggers.afterSync(idePostSyncTask);
        });
    }

    private static void createDummyFilesInLocalRepository(ProjectLayout layout) {
        var emptyJarFile = layout.getBuildDirectory().file("repo/minecraft/neoforge-minecraft-joined/local/neoforge-minecraft-joined-local.jar").get().getAsFile().toPath();
        if (!Files.exists(emptyJarFile)) {
            try {
                Files.createDirectories(emptyJarFile.getParent());
                Files.createFile(emptyJarFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        var pomFile = layout.getBuildDirectory().file("repo/minecraft/neoforge-minecraft-joined/local/neoforge-minecraft-joined-local.pom").get().getAsFile().toPath();
        if (!Files.exists(pomFile)) {
            try {
                Files.writeString(pomFile, """
                        <project xmlns="http://maven.apache.org/POM/4.0.0"
                                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                            <modelVersion>4.0.0</modelVersion>

                            <groupId>minecraft</groupId>
                            <artifactId>neoforge-minecraft-joined</artifactId>
                            <version>local</version>
                            <packaging>jar</packaging>
                        </project>
                        """);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
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
}

abstract class ModFolder {
    @Inject
    public ModFolder() {}

    @InputFiles
    abstract ConfigurableFileCollection getFolders();
}

abstract class ModFoldersProvider implements CommandLineArgumentProvider {
    @Inject
    public ModFoldersProvider() {}

    @Nested
    abstract MapProperty<String, ModFolder> getModFolders();

    @Override
    public Iterable<String> asArguments() {
        return List.of("\"-Dfml.modFolders=%s\"".formatted(getModFolders().get().entrySet().stream()
                .<String>mapMulti((entry, output) -> {
                    for (var directory : entry.getValue().getFolders()) {
                        // Resources
                        output.accept(entry.getKey() + "%%" + directory.getAbsolutePath().replace("\\", "\\\\"));
                    }
                })
                .collect(Collectors.joining(File.pathSeparator))));
    }
}
