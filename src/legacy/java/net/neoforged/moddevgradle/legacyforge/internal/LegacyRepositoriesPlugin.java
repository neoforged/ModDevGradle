package net.neoforged.moddevgradle.legacyforge.internal;

import java.net.URI;
import net.neoforged.moddevgradle.internal.RepositoriesPlugin;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.PluginAware;

/**
 * Like {@link net.neoforged.moddevgradle.internal.RepositoriesPlugin}, this plugin acts differently depending on where
 * it is applied. It also applies {@link net.neoforged.moddevgradle.internal.RepositoriesPlugin} no matter where it is applied.
 */
public class LegacyRepositoriesPlugin implements Plugin<PluginAware> {
    @Override
    public void apply(PluginAware target) {
        target.getPluginManager().apply(RepositoriesPlugin.class);
        if (target instanceof Project project) {
            applyRepositories(project.getRepositories());
        } else if (target instanceof Settings settings) {
            applyRepositories(settings.getDependencyResolutionManagement().getRepositories());

            // Apply our marker plugin to all projects.
            settings.getGradle().beforeProject(project -> project.getPluginManager().apply(DeclaredInSettingsPlugin.class));
        } else if (target instanceof Gradle gradle) {
            // Do nothing
        } else {
            throw new GradleException("This plugin does not support being applied to " + target);
        }
    }

    /**
     * A marker plugin applied to each project, indicating this plugin was applied on the settings level.
     */
    public static final class DeclaredInSettingsPlugin implements Plugin<Project> {
        @Override
        public void apply(Project target) {}
    }

    private void applyRepositories(RepositoryHandler repositories) {
        repositories.maven(repo -> {
            repo.setName("MinecraftForge");
            repo.setUrl(URI.create("https://maven.minecraftforge.net/"));
        });
    }
}
