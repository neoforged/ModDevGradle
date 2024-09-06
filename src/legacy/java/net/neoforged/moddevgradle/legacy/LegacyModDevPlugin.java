package net.neoforged.moddevgradle.legacy;

import net.neoforged.moddevgradle.dsl.NeoForgeExtension;
import net.neoforged.moddevgradle.internal.ModDevPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.jvm.tasks.Jar;

import java.net.URI;

public class LegacyModDevPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPlugins().apply(ModDevPlugin.class);

        project.getRepositories().maven(repo -> {
            repo.setName("MinecraftForge");
            repo.setUrl(URI.create("https://maven.minecraftforge.net/"));
        });

        project.getDependencies().getComponents().withModule("net.neoforged:forge", LegacyForgeMetadataTransform.class);
        project.getDependencies().getComponents().withModule("net.minecraftforge:forge", LegacyForgeMetadataTransform.class);
        project.getDependencies().getComponents().withModule("de.oceanlabs.mcp:mcp_config", McpMetadataTransform.class);

        var depFactory = project.getDependencyFactory();
        var autoRenamingToolRuntime = project.getConfigurations().create("autoRenamingToolRuntime", spec -> {
            spec.setDescription("The AutoRenamingTool CLI tool");
            spec.setCanBeConsumed(false);
            spec.setCanBeResolved(true);
            spec.setTransitive(false);
            spec.defaultDependencies(dependencies -> dependencies.add(depFactory.create("net.neoforged:AutoRenamingTool:2.0.3:all")));
        });

        // We use this directory to store intermediate files used during moddev
        var modDevBuildDir = project.getLayout().getBuildDirectory().dir("moddev");
        var officialToSrg = modDevBuildDir.map(d -> d.file("officialToSrg.tsrg"));

        project.getExtensions().configure(NeoForgeExtension.class, extension -> {
            extension.getNeoForgeArtifact().set(extension.getVersion().map(version -> "net.minecraftforge:forge:" + version));
            extension.getNeoFormArtifact().set(extension.getNeoFormVersion().map(version -> "de.oceanlabs.mcp:mcp_config:" + version));
            extension.getNeoFormRuntime().getAdditionalResults().put("officialToSrgMapping", officialToSrg.map(RegularFile::getAsFile));
        });

        var reobf = project.getExtensions().create("reobfuscation", Reobfuscation.class, project, officialToSrg, autoRenamingToolRuntime);

        var reobfJar = reobf.reobfuscate(
                project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class),
                project.getExtensions().getByType(SourceSetContainer.class).getByName(SourceSet.MAIN_SOURCE_SET_NAME),
                remapJarTask -> remapJarTask.getArchiveClassifier().set("")
        );

        project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class).configure(jar -> jar.getArchiveClassifier().set("dev"));
        project.getTasks().named("assemble", assemble -> assemble.dependsOn(reobfJar));
    }
}
