package net.neoforged.moddevgradle.functional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

public class LaunchMetadataFunctionalTest extends AbstractFunctionalTest {
    @Test
    public void testCreateServerLaunchMetadata() throws IOException {
        writeFile(settingsFile, "rootProject.name = 'launch-metadata'");
        writeGroovyBuildScript("""
                plugins {
                    id "net.neoforged.moddev"
                }

                neoForge {
                    version = "{DEFAULT_NEOFORGE_VERSION}"
                    runs {
                        server {
                            server()
                            environment("MDG_TEST_ENV", "present")
                        }
                    }
                }
                """);

        var result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir)
                .withArguments("createServerLaunchMetadata", "--stacktrace")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":createServerLaunchMetadata").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":createServerLaunchScript").getOutcome());

        var metadataFile = testProjectDir.toPath().resolve("build/moddev/serverLaunchMetadata.properties");
        assertThat(metadataFile).exists();

        var properties = loadProperties(metadataFile);

        assertThat(properties.getProperty("workingDirectory")).endsWith("run");
        assertThat(properties.getProperty("javaExecutable")).isNotBlank();
        assertThat(properties.getProperty("mainClass")).isEqualTo("net.neoforged.devlaunch.Main");
        assertThat(properties.getProperty("classpathArgsFile")).endsWith("serverRunClasspath.txt");
        assertThat(properties.getProperty("vmArgsFile")).endsWith("serverRunVmArgs.txt");
        assertThat(properties.getProperty("programArgsFile")).endsWith("serverRunProgramArgs.txt");
        assertThat(properties.getProperty("modFolders")).startsWith("-Dfml.modFolders=");
        assertThat(properties.getProperty("environment.MDG_TEST_ENV")).isEqualTo("present");

        assertThat(Path.of(properties.getProperty("classpathArgsFile"))).exists();
        assertThat(Path.of(properties.getProperty("vmArgsFile"))).exists();
        assertThat(Path.of(properties.getProperty("programArgsFile"))).exists();
    }

    @Test
    public void testCreateLaunchMetadataAggregate() throws IOException {
        writeFile(settingsFile, "rootProject.name = 'launch-metadata-aggregate'");
        writeGroovyBuildScript("""
                plugins {
                    id "net.neoforged.moddev"
                }

                neoForge {
                    version = "{DEFAULT_NEOFORGE_VERSION}"
                    runs {
                        client {
                            client()
                        }
                        server {
                            server()
                        }
                    }
                }
                """);

        var result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir)
                .withArguments("createLaunchMetadata", "--stacktrace")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":createLaunchMetadata").getOutcome());
        assertThat(testProjectDir.toPath().resolve("build/moddev/clientLaunchMetadata.properties")).exists();
        assertThat(testProjectDir.toPath().resolve("build/moddev/serverLaunchMetadata.properties")).exists();
    }

    private static Properties loadProperties(Path path) throws IOException {
        var properties = new Properties();
        try (var input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
    }
}
