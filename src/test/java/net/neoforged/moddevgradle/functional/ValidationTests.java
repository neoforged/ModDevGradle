package net.neoforged.moddevgradle.functional;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

public class ValidationTests {
    @TempDir
    File testProjectDir;
    private File settingsFile;
    private File buildFile;

    @BeforeEach
    public void setup() throws IOException {
        settingsFile = new File(testProjectDir, "settings.gradle");
        Files.writeString(settingsFile.toPath(), "rootProject.name = 'test-project'");

        buildFile = new File(testProjectDir, "build.gradle");
    }

    @Test
    void testRunNameValidation() throws IOException {
        Files.writeString(buildFile.toPath(), """
                plugins {
                    id "net.neoforged.moddev"
                }
                
                neoForge {
                    runs {
                        validName1 {}
                        create("invalid name") {}
                    }
                }
                """);

        final BuildResult result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir)
                .withArguments("tasks")
                .buildAndFail();

        assertThat(result.getOutput()).contains("Run name 'invalid name' is invalid!");
    }
}
