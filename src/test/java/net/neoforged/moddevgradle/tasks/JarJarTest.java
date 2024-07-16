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
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

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
        final BuildResult result = runWithSource("");
        assertEquals(NO_SOURCE, result.task(":jarJar").getOutcome());
    }

    @Test
    public void testSuccessfulEmbed() throws Exception {
        final BuildResult result = runWithSource("""
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
        final BuildResult result = runWithSource("""
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

    /**
     * When a single version is specified, the jarjar metadata should use the real resolved version,
     * rather than the one the user indicated (which can get upgraded).
     */
    @Test
    public void testSimpleStringVersionPullsRangeFromResolution() throws Exception {
        // Note that slf4j 0.0.1 does not exist!
        final BuildResult result = runWithSource("""
                dependencies {
                    jarJar(implementation("org.slf4j:slf4j-api:0.0.1"))
                    constraints {
                        jarJar(implementation("org.slf4j:slf4j-api:2.0.13"))
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
                                new ContainedVersion(VersionRange.createFromVersionSpec("[2.0.13,)"), new DefaultArtifactVersion("2.0.13")),
                                "META-INF/jarjar/slf4j-api-2.0.13.jar",
                                false
                        )
                )
        ), readMetadata());
    }

    @Test
    public void testUnsupportedStrictlyRange() {
        final UnexpectedBuildFailure e = assertThrows(UnexpectedBuildFailure.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                JarJarTest.this.runWithSource("""
                        dependencies {
                            jarJar(implementation("org.slf4j:slf4j-api")) {
                                version {
                                    strictly '[0.1, 3.0['
                                    prefer '2.0.13'
                                }
                            }
                        }
                        """);
            }
        });
        assertThat(e).hasMessageContaining("Unsupported version constraint '[0.1, 3.0[' on Jar-in-Jar dependency org.slf4j:slf4j-api");
    }

    @Test
    public void testUnsupportedRequiredRange() {
        final UnexpectedBuildFailure e = assertThrows(UnexpectedBuildFailure.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                JarJarTest.this.runWithSource("""
                        dependencies {
                            jarJar(implementation("org.slf4j:slf4j-api:[0.1, 3.0["))
                        }
                        """);
            }
        });
        assertThat(e).hasMessageContaining("Unsupported version constraint '[0.1, 3.0[' on Jar-in-Jar dependency org.slf4j:slf4j-api");
    }

    /**
     * {@code x[} is alternative notation for {@code x)} in math and Gradle. Maven does not support it, so we should reject it.
     */
    @Test
    public void testUnsupportedPreferredRange() {
        final UnexpectedBuildFailure e = assertThrows(UnexpectedBuildFailure.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                JarJarTest.this.runWithSource("""
                        dependencies {
                            jarJar(implementation("org.slf4j:slf4j-api")) {
                                version {
                                    prefer '[0.1, 3.0['
                                }
                            }
                        }
                        """);
            }
        });
        assertThat(e).hasMessageContaining("Unsupported version constraint '[0.1, 3.0[' on Jar-in-Jar dependency org.slf4j:slf4j-api");
    }

    @Test
    public void testUnsupportedDynamicVersion() {
        final UnexpectedBuildFailure e = assertThrows(UnexpectedBuildFailure.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                JarJarTest.this.runWithSource("""
                        dependencies {
                            jarJar(implementation("org.slf4j:slf4j-api")) {
                                version {
                                    prefer '[2.0.+, 3.0)'
                                }
                            }
                        }
                        """);
            }
        });
        assertThat(e).hasMessageContaining("Unsupported version constraint '[2.0.+, 3.0)' on Jar-in-Jar dependency org.slf4j:slf4j-api: dynamic versions are unsupported");
    }

    private BuildResult runWithSource(final String source) throws IOException {
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
        final Path path = tempDir.resolve("build/generated/jarJar");
        if (!Files.isDirectory(path)) {
            return List.of();
        }
        try (final Stream<Path> stream = Files.walk(path)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(new Function<Path, String>() {
                        @Override
                        public String apply(Path p) {
                            return path.relativize(p).toString().replace('\\', '/');
                        }
                    }).toList();
        }
    }

    private Metadata readMetadata() throws IOException {
        try (final InputStream in = Files.newInputStream(tempDir.resolve("build/generated/jarJar/META-INF/jarjar/metadata.json"))) {
            return MetadataIOHandler.fromStream(in).orElseThrow();
        }
    }

}
