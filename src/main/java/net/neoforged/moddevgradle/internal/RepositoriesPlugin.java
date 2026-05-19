package net.neoforged.moddevgradle.internal;

import java.net.URI;
import net.neoforged.moddevgradle.internal.generated.MojangRepositoryFilter;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.PluginAware;

/**
 * This plugin acts in different roles depending on where it is applied:
 * <ul>
 * <li>At the project-level, it will add the required repositories for moddev.
 * The NeoForge repository content filter is deferred so that it can be
 * populated dynamically from the selected NeoForge version metadata.</li>
 * <li>At the settings-level, it will add the required repositories to the dependency
 * management block with an immediate content filter, and add a marker plugin to
 * the Gradle instance to prevent the repositories from being added again at the
 * project-level.</li>
 * </ul>
 */
public class RepositoriesPlugin implements Plugin<PluginAware> {
    static final String NEOFORGE_REPO_EXTENSION = "__internal_neoForgeRepository";

    @Override
    public void apply(PluginAware target) {
        if (target instanceof Project project) {
            // Defer content filter so ModDevPlugin.enable() can populate it dynamically
            var neoRepo = applyRepositories(project.getRepositories(), false);
            project.getExtensions().add(MavenArtifactRepository.class, NEOFORGE_REPO_EXTENSION, neoRepo);
        } else if (target instanceof Settings settings) {
            // Settings-level: apply content filter immediately since per-project
            // dynamic population is not possible for shared repositories
            applyRepositories(settings.getDependencyResolutionManagement().getRepositories(), true);
            settings.getGradle().getPlugins().apply(getClass()); // Add a marker to Gradle
        } else if (target instanceof Gradle gradle) {
            // Do nothing
        } else {
            throw new GradleException("This plugin does not support being applied to " + target);
        }
    }

    /**
     * Returns the NeoForge repository. Exposed for dynamic filter population.
     */
    static MavenArtifactRepository getNeoForgeRepository(Project project) {
        return (MavenArtifactRepository) project.getExtensions().getByName(NEOFORGE_REPO_EXTENSION);
    }

    /**
     * Applies the content filter to the NeoForge repository, including any
     * dynamically discovered game library modules from
     * {@link NeoForgedRepositoryFilter#addGameLibrary(String, String)}.
     * <p>
     * Must be called before any dependency resolution uses this repository —
     * Gradle locks the content descriptor on first use.
     */
    static void applyContentFilter(Project project) {
        var neoRepo = getNeoForgeRepository(project);
        neoRepo.content(NeoForgedRepositoryFilter::filter);
    }

    private static MavenArtifactRepository applyRepositories(RepositoryHandler repositories, boolean applyContentFilter) {
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

        var neoForgeRepo = repositories.maven(repo -> {
            repo.setName("NeoForged Releases");
            repo.setUrl(URI.create("https://maven.neoforged.net/releases/"));
            if (applyContentFilter) {
                repo.content(NeoForgedRepositoryFilter::filter);
            }
        });
        return neoForgeRepo;
    }

    private static void sortFirst(RepositoryHandler repositories, MavenArtifactRepository repo) {
        repositories.remove(repo);
        repositories.add(0, repo);
    }
}
