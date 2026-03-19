package net.neoforged.moddevgradle.internal;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import net.neoforged.moddevgradle.internal.generated.MojangRepositoryFilter;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.PluginAware;

/**
 * This plugin acts in different roles depending on where it is applied:
 * <ul>
 * <li>At the project-level, it will add the required repositories for moddev.</li>
 * <li>At the settings-level, it will add the required repositories to the dependency management block, and add a marker plugin to the Gradle instance to prevent the
 * repositories from being added again at the project-level.</li>
 * </ul>
 */
public class RepositoriesPlugin implements Plugin<PluginAware> {
    private static final String NEOFORGED_RELEASES_NAME = "NeoForged Releases";

    private static final Set<String> MAVEN_CENTRAL_URLS = Set.of(
            normalizeRepositoryUrl(URI.create("https://repo.maven.apache.org/maven2/")),
            normalizeRepositoryUrl(URI.create("https://repo1.maven.org/maven2/")));

    @Override
    public void apply(PluginAware target) {
        if (target instanceof Project project) {
            applyRepositories(project.getRepositories());
            project.afterEvaluate(p -> moveMavenCentralBeforeManagedRepositories(p.getRepositories()));
        } else if (target instanceof Settings settings) {
            applyRepositories(settings.getDependencyResolutionManagement().getRepositories());
            settings.getGradle().settingsEvaluated(s -> moveMavenCentralBeforeManagedRepositories(settings.getDependencyResolutionManagement().getRepositories()));
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
            repo.setName(NEOFORGED_RELEASES_NAME);
            repo.setUrl(URI.create("https://maven.neoforged.net/releases/"));
        });
    }

    private static void sortFirst(RepositoryHandler repositories, MavenArtifactRepository repo) {
        repositories.remove(repo);
        repositories.add(0, repo);
    }

    private static void moveMavenCentralBeforeManagedRepositories(RepositoryHandler repositories) {
        MavenArtifactRepository mavenCentral = repositories.stream()
                .filter(MavenArtifactRepository.class::isInstance)
                .map(MavenArtifactRepository.class::cast)
                .filter(RepositoriesPlugin::isMavenCentral)
                .findFirst()
                .orElse(null);

        if (mavenCentral == null) {
            return;
        }

        repositories.remove(mavenCentral);

        int insertIndex = repositories.size();
        for (int i = 0; i < repositories.size(); i++) {
            ArtifactRepository repository = repositories.get(i);
            if (NEOFORGED_RELEASES_NAME.equals(repository.getName())) {
                insertIndex = i;
                break;
            }
        }

        repositories.add(insertIndex, mavenCentral);
    }

    private static boolean isMavenCentral(MavenArtifactRepository repository) {
        return MAVEN_CENTRAL_URLS.contains(normalizeRepositoryUrl(repository.getUrl()));
    }

    private static String normalizeRepositoryUrl(URI uri) {
        return uri.toString().replaceAll("/+$", "").toLowerCase(Locale.ROOT);
    }
}
