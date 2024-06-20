package net.neoforged.moddevgradle.tasks;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gradle.testkit.runner.TaskOutcome.NO_SOURCE;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JarJarTest {
    @TempDir
    Path tempDir;

    @Test
    public void testNoSourceWhenNoDependenciesAreDefined() throws IOException {
        var result = runWithSource("");
        assertEquals(NO_SOURCE, result.task(":jarJar").getOutcome());
    }

    @Test
    public void testSuccessfulEmbed() throws IOException {
        var result = runWithSource("""
                dependencies {
                    jarJar(implementation("org.slf4j:slf4j-api")) {
                        version {
                            strictly '[0.1, 3.0)'
                            prefer '2.0.13'
                        }
                    }
                }
                """);
        assertEquals(SUCCESS, result.task(":jarJar").getOutcome());
    }

    @Test
    public void testUnsupportedStrictlyRange() {
        var e = assertThrows(UnexpectedBuildFailure.class, () -> runWithSource("""
                dependencies {
                    jarJar(implementation("org.slf4j:slf4j-api")) {
                        version {
                            strictly '[0.1, 3.0['
                            prefer '2.0.13'
                        }
                    }
                }
                """));
        assertThat(e).hasMessageContaining("Unsupported version constraint '[0.1, 3.0[' on Jar-in-Jar dependency org.slf4j:slf4j-api");
    }

    @Test
    public void testUnsupportedRequiredRange() {
        var e = assertThrows(UnexpectedBuildFailure.class, () -> runWithSource("""
                dependencies {
                    jarJar(implementation("org.slf4j:slf4j-api:[0.1, 3.0["))
                }
                """));
        assertThat(e).hasMessageContaining("Unsupported version constraint '[0.1, 3.0[' on Jar-in-Jar dependency org.slf4j:slf4j-api");
    }

    @Test
    public void testUnsupportedPreferredRange() {
        var e = assertThrows(UnexpectedBuildFailure.class, () -> runWithSource("""
                dependencies {
                    jarJar(implementation("org.slf4j:slf4j-api")) {
                        version {
                            prefer '[0.1, 3.0['
                        }
                    }
                }
                """));
        assertThat(e).hasMessageContaining("Unsupported version constraint '[0.1, 3.0[' on Jar-in-Jar dependency org.slf4j:slf4j-api");
    }

    @Test
    public void testUnsupportedDynamicVersion() {
        var e = assertThrows(UnexpectedBuildFailure.class, () -> runWithSource("""
                dependencies {
                    jarJar(implementation("org.slf4j:slf4j-api:2.0.+"))
                }
                """));
        assertThat(e).hasMessageContaining("Unsupported version constraint '2.0.+' on Jar-in-Jar dependency org.slf4j:slf4j-api");
    }

    private BuildResult runWithSource(String source) throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle"), "");
        Files.writeString(tempDir.resolve("build.gradle"), """
                                                                   plugins {
                                                                     id "net.neoforged.moddev"
                                                                   }
                                                                   repositories {
                                                                     mavenCentral()
                                                                   }
                                                                   """ + source);

        return GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(tempDir.toFile())
                .withArguments("jarjar")
                .withDebug(true)
                .build();
    }

}