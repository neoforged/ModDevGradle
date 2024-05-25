package net.neoforged.moddevgradle.functional;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class KotlinScriptTest {
    @TempDir
    File testProjectDir;
    private File settingsFile;
    private File buildFile;

    @BeforeEach
    public void setup() {
        settingsFile = new File(testProjectDir, "settings.gradle.kts");
        buildFile = new File(testProjectDir, "build.gradle.kts");
    }

    @Test
    public void testApplyInEmptyProject() throws IOException {
        writeFile(settingsFile, """
                rootProject.name = "hello-world";
                """);
        String buildFileContent = """
                plugins {
                    id("net.neoforged.moddev")
                }
                """;
        writeFile(buildFile, buildFileContent);

        BuildResult result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir)
                .withArguments("tasks", "--all")
                .build();

        assertThat(result.getOutput()).contains("createMinecraftArtifacts");
        assertEquals(TaskOutcome.SUCCESS, result.task(":tasks").getOutcome());
    }

    private void writeFile(File destination, String content) throws IOException {
        BufferedWriter output = null;
        try {
            output = new BufferedWriter(new FileWriter(destination));
            output.write(content);
        } finally {
            if (output != null) {
                output.close();
            }
        }


    }
}
