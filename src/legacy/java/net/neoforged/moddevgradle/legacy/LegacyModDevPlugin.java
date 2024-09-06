package net.neoforged.moddevgradle.legacy;

import net.neoforged.moddevgradle.dsl.NeoForgeExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.net.URI;

public class LegacyModDevPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getRepositories().maven(repo -> {
            repo.setName("MinecraftForge");
            repo.setUrl(URI.create("https://maven.minecraftforge.net/"));
        });

        project.getDependencies().getComponents().withModule("net.neoforged:forge", LegacyForgeMetadataTransform.class);
        project.getDependencies().getComponents().withModule("net.minecraftforge:forge", LegacyForgeMetadataTransform.class);
        project.getDependencies().getComponents().withModule("de.oceanlabs.mcp:mcp_config", McpMetadataTransform.class);

        project.getPlugins().withId("net.neoforged.moddev", plugin -> {
            project.getExtensions().configure(NeoForgeExtension.class, extension -> {
                extension.getNeoForgeArtifact().set(extension.getVersion().map(version -> "net.minecraftforge:forge:" + version));
                extension.getNeoFormArtifact().set(extension.getNeoFormVersion().map(version -> "de.oceanlabs.mcp:mcp_config:" + version));
            });
        });
    }
}
