package net.neoforged.moddevgradle.internal;

import net.neoforged.moddevgradle.internal.generated.MojangRepositoryFilter;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.PluginAware;

import java.net.URI;

/**
 * This plugin acts in different roles depending on where it is applied:
 * <ul>
 *     <li>At the project-level, it will add the required repositories for moddev.</li>
 *     <li>At the settings-level, it will add the required repositories to the dependency management block, and add a marker plugin to the Gradle instance to prevent the
 *     repositories from being added again at the project-level.</li>
 * </ul>
 */
public class RepositoriesPlugin implements Plugin<PluginAware> {
    @Override
    public void apply(PluginAware target) {
        if (target instanceof Project project) {
            applyRepositories(project.getRepositories());
        } else if (target instanceof Settings settings) {
            applyRepositories(settings.getDependencyResolutionManagement().getRepositories());
            settings.getGradle().getPlugins().apply(getClass()); // Add a marker to Gradle
        } else if (target instanceof Gradle gradle) {
            // Do nothing
        } else {
            throw new GradleException("This plugin does not support being applied to " + target);
        }
    }

    private void applyRepositories(RepositoryHandler repositories) {
        var mojangMaven = repositories.maven(repo -> {
            repo.setName("Mojang Minecraft Libraries");
            repo.setUrl(URI.create("https://libraries.minecraft.net/"));
            repo.metadataSources(sources -> sources.mavenPom());
            repo.content(MojangRepositoryFilter::filter);
        });
        sortFirst(repositories, mojangMaven);

        var mojangMetaMaven = repositories.maven(repo -> {
            repo.setName("Mojang Meta");
            repo.setUrl("https://maven.neoforged.net/mojang-meta/");
            repo.metadataSources(sources -> sources.gradleMetadata());
            repo.content(content -> {
                content.includeModule("net.neoforged", "minecraft-dependencies");
            });
        });
        sortFirst(repositories, mojangMetaMaven);

        repositories.maven(repo -> {
            repo.setName("NeoForged Releases");
            repo.setUrl(URI.create("https://maven.neoforged.net/releases/"));
        });
    }

    private static void sortFirst(RepositoryHandler repositories, MavenArtifactRepository repo) {
        repositories.remove(repo);
        repositories.add(0, repo);
    }
}
