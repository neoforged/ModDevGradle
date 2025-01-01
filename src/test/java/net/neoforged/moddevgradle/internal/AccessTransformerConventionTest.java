package net.neoforged.moddevgradle.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.moddevgradle.dsl.NeoForgeExtension;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import net.neoforged.nfrtgradle.CreateMinecraftArtifacts;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AccessTransformerConventionTest {
    Project project;
    NeoForgeExtension extension;

    @BeforeEach
    void setup() {
        project = ProjectBuilder.builder().build();
        project.getPlugins().apply(ModDevPlugin.class);

        extension = ExtensionUtils.getExtension(project, "neoForge", NeoForgeExtension.class);
        extension.setVersion("1.2.3");
    }

    @Test
    void testNoDefaultAccessTransformerIfItDoesNotExist() {
        var task = getCreateMinecraftArtifactsTask();
        var files = task.getAccessTransformers().getFiles();
        assertThat(files).isEmpty();
    }

    @Test
    void testDefaultAccessTransformerIfItExists() throws Exception {
        var transformerPath = createDefaultAT();

        var task = getCreateMinecraftArtifactsTask();
        var files = task.getAccessTransformers().getFiles();
        assertThat(files).containsOnly(transformerPath.toFile());
    }

    /**
     * The default AT will persist, even if additional ATs are added.
     */
    @Test
    void testCustomAccessTransformerDisablesConvention() throws Exception {
        var defaultAT = createDefaultAT();

        var transformerPath = project.getProjectDir().toPath().resolve("custom_at.cfg");
        Files.createDirectories(transformerPath.getParent());
        Files.writeString(transformerPath, "# Hello World");

        extension.getAccessTransformers().from(transformerPath.toFile());

        var task = getCreateMinecraftArtifactsTask();
        var files = task.getAccessTransformers().getFiles();
        assertThat(files).containsOnly(defaultAT.toFile(), transformerPath.toFile());
    }

    private Path createDefaultAT() throws IOException {
        var transformerPath = project.getProjectDir().toPath().resolve("src/main/resources/META-INF/accesstransformer.cfg");
        Files.createDirectories(transformerPath.getParent());
        Files.writeString(transformerPath, "# Hello World");
        return transformerPath;
    }

    private CreateMinecraftArtifacts getCreateMinecraftArtifactsTask() {
        return (CreateMinecraftArtifacts) project.getTasks().getByName("createMinecraftArtifacts");
    }
}
