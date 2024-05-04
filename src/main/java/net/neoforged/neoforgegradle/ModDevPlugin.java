package net.neoforged.neoforgegradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;

import java.net.URI;
import java.util.stream.Collectors;

public class ModDevPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPlugins().apply(JavaPlugin.class);
        var extension = project.getExtensions().create("neoForge", NeoForgeExtension.class);

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
            repo.metadataSources(MavenArtifactRepository.MetadataSources::artifact);
        }));

        var configurations = project.getConfigurations();
        var dependencyFactory = project.getDependencyFactory();
        var neoForgeModDev = configurations.create("neoForgeModDev", files -> {
            files.setCanBeConsumed(false);
            files.setCanBeResolved(true);
            files.defaultDependencies(spec -> {
                spec.addLater(extension.getVersion().map(version -> dependencyFactory.create("net.neoforged:neoforge:" + version + ":userdev")));
            });
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

        var createArtifacts = tasks.register("createMinecraftArtifacts", CreateMinecraftArtifactsTask.class, task -> {
            task.getArtifactManifestFile().set(createManifest.get().getManifestFile());
            task.getNeoForgeArtifact().set(extension.getVersion().map(version -> "net.neoforged:neoforge:" + version));
            task.getNeoFormInABox().from(neoFormInABoxConfig);
            task.getCompiledArtifact().set(layout.getBuildDirectory().file("repo/minecraft/joined/local/minecraft-local.jar"));
            task.getSourcesArtifact().set(layout.getBuildDirectory().file("repo/minecraft/joined/local/minecraft-local-sources.jar"));
        });
        tasks.named("compileJava").configure(compileJava -> {
            compileJava.dependsOn(createArtifacts);
        });

        var minecraftBinaries = createArtifacts.map(task -> project.files(task.getCompiledArtifact().get()));
        var localRuntime = configurations.create("neoForgeGeneratedArtifacts");
        project.getDependencies().add(localRuntime.getName(), minecraftBinaries);
        project.getDependencies().add("implementation", extension.getVersion().map(version -> dependencyFactory.create("net.neoforged:neoforge:" + version + ":universal")));
        project.getDependencies().add("compileOnly", minecraftBinaries);
        configurations.named("runtimeClasspath", files -> files.extendsFrom(localRuntime));

        tasks.named("compileJava").configure(compileJava -> {
            compileJava.dependsOn(createArtifacts);
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
