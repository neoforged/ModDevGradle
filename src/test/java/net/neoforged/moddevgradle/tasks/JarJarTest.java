package net.neoforged.moddevgradle.tasks;

import net.neoforged.jarjar.metadata.ContainedJarIdentifier;
import net.neoforged.jarjar.metadata.ContainedJarMetadata;
import net.neoforged.jarjar.metadata.ContainedVersion;
import net.neoforged.jarjar.metadata.Metadata;
import net.neoforged.jarjar.metadata.MetadataIOHandler;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
    public void testSuccessfulEmbed() throws Exception {
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

        assertThat(listFiles()).containsOnly(
                "META-INF/jarjar/metadata.json", "META-INF/jarjar/slf4j-api-2.0.13.jar"
        );
        assertEquals(new Metadata(
                List.of(
                        new ContainedJarMetadata(
                                new ContainedJarIdentifier("org.slf4j", "slf4j-api"),
                                new ContainedVersion(VersionRange.createFromVersionSpec("[0.1,3.0)"), new DefaultArtifactVersion("2.0.13")),
                                "META-INF/jarjar/slf4j-api-2.0.13.jar",
                                false
                        )
                )
        ), readMetadata());
    }

    @Test
    public void testSimpleStringVersion() throws Exception {
        var result = runWithSource("""
                dependencies {
                    jarJar(implementation("org.slf4j:slf4j-api:2.0.13"))
                }
                """);
        assertEquals(SUCCESS, result.task(":jarJar").getOutcome());

        assertThat(listFiles()).containsOnly(
                "META-INF/jarjar/metadata.json", "META-INF/jarjar/slf4j-api-2.0.13.jar"
        );
        assertEquals(new Metadata(
                List.of(
                        new ContainedJarMetadata(
                                new ContainedJarIdentifier("org.slf4j", "slf4j-api"),
                                new ContainedVersion(VersionRange.createFromVersionSpec("[2.0.13,)"), new DefaultArtifactVersion("2.0.13")),
                                "META-INF/jarjar/slf4j-api-2.0.13.jar",
                                false
                        )
                )
        ), readMetadata());
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
                    jarJar(implementation("org.slf4j:slf4j-api")) {
                        version {
                            prefer '[2.0.+, 3.0)'
                        }
                    }
                }
                """));
        assertThat(e).hasMessageContaining("Unsupported version constraint '[2.0.+, 3.0)' on Jar-in-Jar dependency org.slf4j:slf4j-api: dynamic versions are unsupported");
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

    private List<String> listFiles() throws IOException {
        var path = tempDir.resolve("build/generated/jarJar");
        if (!Files.isDirectory(path)) {
            return List.of();
        }
        try (var stream = Files.walk(path)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(p -> path.relativize(p).toString().replace('\\', '/')).toList();
        }
    }

    private Metadata readMetadata() throws IOException {
        try (var in = Files.newInputStream(tempDir.resolve("build/generated/jarJar/META-INF/jarjar/metadata.json"))) {
            return MetadataIOHandler.fromStream(in).orElseThrow();
        }
    }

}