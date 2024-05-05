package net.neoforged.neoforgegradle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

public class ModDevPluginImpl implements Plugin<Project> {
    private static final GradleVersion MIN_VERSION = GradleVersion.version("8.7");

    @Override
    public void apply(Project project) {
        if (GradleVersion.current().compareTo(MIN_VERSION) < 0) {
            throw new GradleException("To use the NeoForge plugin, please use at least " + MIN_VERSION + ". You are currently using " + GradleVersion.current() + ".");
        }

        project.getPlugins().apply(JavaLibraryPlugin.class);
        var extension = project.getExtensions().create("neoForge", NeoForgeExtension.class);
        var dependencyFactory = project.getDependencyFactory();
        var neoForgeUserdevDependency = extension.getVersion().map(version -> dependencyFactory.create("net.neoforged:neoforge:" + version + ":userdev"));
        var neoForgeUniversalDependency = extension.getVersion().map(version -> dependencyFactory.create("net.neoforged:neoforge:" + version + ":universal"));

        var repositories = project.getRepositories();
        repositories.addLast(repositories.maven(repo -> {
            repo.setUrl(URI.create("https://maven.neoforged.net/releases/"));
        }));
        repositories.addLast(repositories.maven(repo -> {
            repo.setUrl(URI.create("https://libraries.minecraft.net/"));
            repo.metadataSources(sources -> sources.artifact());
        }));
        repositories.addLast(repositories.maven(repo -> {
            repo.setUrl(project.getLayout().getBuildDirectory().map(dir -> dir.dir("repo").getAsFile().getAbsolutePath()));
            repo.metadataSources(sources -> sources.mavenPom());
        }));

        var configurations = project.getConfigurations();
        var neoForgeModDev = configurations.create("neoForgeModDev", files -> {
            files.setCanBeConsumed(false);
            files.setCanBeResolved(true);
            files.defaultDependencies(spec -> spec.addLater(neoForgeUserdevDependency));
        });
        var neoFormInABoxConfig = configurations.create("neoFormInABoxConfig", files -> {
            files.setCanBeConsumed(false);
            files.setCanBeResolved(true);
            files.defaultDependencies(spec -> {
                spec.add(dependencyFactory.create("net.neoforged:NeoFormInABox:1.0-SNAPSHOT"));
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
        var extraJarPath = layout.getBuildDirectory().file("repo/minecraft/minecraft-joined/local/minecraft-joined-local-resources-aka-client-extra.jar");

        var createArtifacts = tasks.register("createMinecraftArtifacts", CreateMinecraftArtifactsTask.class, task -> {
            task.getArtifactManifestFile().set(createManifest.get().getManifestFile());
            task.getNeoForgeArtifact().set(extension.getVersion().map(version -> "net.neoforged:neoforge:" + version));
            task.getNeoFormInABox().from(neoFormInABoxConfig);
            task.getCompiledArtifact().set(layout.getBuildDirectory().file("repo/minecraft/minecraft-joined/local/minecraft-joined-local.jar"));
            task.getSourcesArtifact().set(layout.getBuildDirectory().file("repo/minecraft/minecraft-joined/local/minecraft-joined-local-sources.jar"));
            task.getResourcesArtifact().set(extraJarPath);
        });

        createDummyFilesInLocalRepository(layout);

        var minecraftBinaries = createArtifacts.map(task -> project.files(task.getCompiledArtifact()));
        project.getDependencies().add("compileOnly", minecraftBinaries.get());

        var localRuntime = configurations.create("neoForgeGeneratedArtifacts", config -> {
            config.setCanBeResolved(false);
            config.setCanBeConsumed(false);
        });
        project.getDependencies().add(localRuntime.getName(), "minecraft:minecraft-joined:local");
        project.getDependencies().add("compileOnly", "minecraft:minecraft-joined:local");
        configurations.named("runtimeClasspath", files -> files.extendsFrom(localRuntime));

        // Setup java toolchains if the current JVM isn't already J21 (how the hell did you load this plugin...)
        if (!JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_21)) {
            var javaExtension = ExtensionUtils.getExtension(project, "java", JavaPluginExtension.class);
            var toolchainSpec = javaExtension.getToolchain();
            toolchainSpec.getLanguageVersion().convention(JavaLanguageVersion.of(21));
        }

        // Let's try to get the userdev JSON out of the universal jar
        // I don't like having to use a configuration for this...
        var userDevConfig = getUserdevConfigProvider(project, neoForgeUserdevDependency);

        var legacyClasspathConfiguration = configurations.create("legacyClassPath", spec -> {
            spec.setCanBeResolved(true);
            spec.setCanBeConsumed(false);
            spec.setTransitive(false);
            spec.withDependencies(set -> {
                set.addAllLater(userDevConfig.map(config -> {
                    return Stream.of(config.getAsJsonArray("libraries"))
                            .flatMap(a -> a.asList().stream())
                            .map(lib -> (Dependency) dependencyFactory.create(lib.getAsString()))
                            .toList();
                }));
            });
        });

        var writeLcpTask = tasks.register("writeLegacyClasspath", WriteLegacyClasspath.class, writeLcp -> {
            writeLcp.getEntries().from(legacyClasspathConfiguration);
            writeLcp.getEntries().from(extraJarPath);
        });

        tasks.register("runClient", JavaExec.class, runClientTask -> {
            // Modules: these will be used to generate the module path
            var modulesConfiguration = configurations.create("modules", c -> {
                c.setCanBeConsumed(false);
                c.setCanBeResolved(true);
                c.withDependencies(set -> {
                    for (var moduleDep : userDevConfig.get().getAsJsonArray("modules")) {
                        set.add(dependencyFactory.create(moduleDep.getAsString()));
                    }
                });
            });

            var runs = userDevConfig.get().getAsJsonObject("runs");
            var clientRun = runs.getAsJsonObject("server");

            // This should probably all be done using providers; but that's for later :)
            runClientTask.getMainClass().set(clientRun.get("main").getAsString());
            for (var arg : clientRun.getAsJsonArray("args")) {
                runClientTask.args(arg.getAsString());
            }
            for (var jvmArg : clientRun.getAsJsonArray("jvmArgs")) {
                String arg = jvmArg.getAsString();
                if (arg.equals("{modules}")) {
                    arg = modulesConfiguration.getFiles().stream()
                            .map(File::getAbsolutePath)
                            .collect(Collectors.joining(File.pathSeparator));
                }
                runClientTask.jvmArgs(arg);
            }
            for (var env : clientRun.getAsJsonObject("env").entrySet()) {
                var envValue = env.getValue().getAsString();
                if (envValue.equals("{source_roots}")) {
                    continue; // This is MOD_CLASSES, skip for now.
                }
                runClientTask.environment(env.getKey(), envValue);
            }
            for (var prop : clientRun.getAsJsonObject("props").entrySet()) {
                var propValue = prop.getValue().getAsString();
                if (propValue.equals("{minecraft_classpath_file}")) {
                    propValue = writeLcpTask.flatMap(WriteLegacyClasspath::getLegacyClasspathFile).get().getAsFile().getAbsolutePath();
                    runClientTask.dependsOn(writeLcpTask); // I think this is needed because Gradle can't track who called .get()? to be confirmed... I need to check that I didn't make the same mistake elsewhere
                }

                runClientTask.systemProperty(prop.getKey(), propValue);
            }

            runClientTask.classpath(configurations.named("runtimeClasspath"));
            // Create directory if needed
            var runDir = project.file("run/").toPath(); // store here, can't reference project inside doFirst for the config cache
            runClientTask.doFirst(t -> {
                try {
                    Files.createDirectories(runDir);
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to create run directory", e);
                }
            });
            runClientTask.setWorkingDir(project.file("run/"));

            // Enable debug logging; doesn't work for FML???
//            runClientTask.systemProperty("forge.logging.console.level", "debug");
        });
    }

    private static void createDummyFilesInLocalRepository(ProjectLayout layout) {
        var emptyJarFile = layout.getBuildDirectory().file("repo/minecraft/minecraft-joined/local/minecraft-joined-local.jar").get().getAsFile().toPath();
        if (!Files.exists(emptyJarFile)) {
            try {
                Files.createDirectories(emptyJarFile.getParent());
                Files.createFile(emptyJarFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        var pomFile = layout.getBuildDirectory().file("repo/minecraft/minecraft-joined/local/minecraft-joined-local.pom").get().getAsFile().toPath();
        if (!Files.exists(pomFile)) {
            try {
                Files.writeString(pomFile, """
                        <project xmlns="http://maven.apache.org/POM/4.0.0"
                                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                            <modelVersion>4.0.0</modelVersion>

                            <groupId>minecraft</groupId>
                            <artifactId>minecraft-joined</artifactId>
                            <version>local</version>
                            <packaging>jar</packaging>
                        </project>
                        """);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static Provider<JsonObject> getUserdevConfigProvider(Project project, Provider<? extends Dependency> userdevDependency) {
        var configuration = project.getConfigurations().create("neoForgeConfigOnly", spec -> {
            spec.setCanBeResolved(true);
            spec.setCanBeConsumed(false);
            spec.setTransitive(false);
            spec.withDependencies(set -> set.addLater(userdevDependency));
        });

        return configuration.getIncoming().getArtifacts().getArtifactFiles().getElements().map(files -> {
            if (files.size() != 1) {
                throw new GradleException("Expected the NeoForge userdev artifact to be resolved to exactly one file: " + files);
            }

            var userdefFile = files.iterator().next().getAsFile();

            try (var zf = new ZipFile(userdefFile)) {
                var configEntry = zf.getEntry("config.json");
                if (configEntry == null) {
                    throw new IOException("The NeoForge Userdev artifact is missing a config.json file");
                }

                try (var in = zf.getInputStream(configEntry);
                     var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    return JsonParser.parseReader(reader).getAsJsonObject();
                }
            } catch (Exception e) {
                throw new GradleException("Failed to read NeoForge config file from " + userdefFile, e);
            }
        });
    }

    private static String guessMavenGav(ResolvedArtifactResult result) {
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
