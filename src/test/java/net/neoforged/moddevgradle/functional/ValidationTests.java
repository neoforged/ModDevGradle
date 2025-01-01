package net.neoforged.moddevgradle.functional;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ValidationTests extends AbstractFunctionalTest {
    @BeforeEach
    public void setup() throws IOException {
        Files.writeString(settingsFile.toPath(), "rootProject.name = 'test-project'");
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

        BuildResult result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir)
                .withArguments("tasks")
                .buildAndFail();

        assertThat(result.getOutput()).contains("Run name 'invalid name' is invalid!");
    }
}
