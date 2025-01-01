package net.neoforged.moddevgradle.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gradle.testkit.runner.TaskOutcome.NO_SOURCE;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import net.neoforged.jarjar.metadata.ContainedJarIdentifier;
import net.neoforged.jarjar.metadata.ContainedJarMetadata;
import net.neoforged.jarjar.metadata.ContainedVersion;
import net.neoforged.jarjar.metadata.Metadata;
import net.neoforged.jarjar.metadata.MetadataIOHandler;
import net.neoforged.moddevgradle.functional.AbstractFunctionalTest;
import net.neoforged.moddevgradle.internal.utils.FileUtils;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.junit.jupiter.api.Test;

class JarJarTest extends AbstractFunctionalTest {
    @Test
    public void testNoSourceWhenNoDependenciesAreDefined() throws IOException {
        var result = runWithSource("");
        assertEquals(NO_SOURCE, result.task(":jarJar").getOutcome());
    }

    @Test
    void testEmbeddingCurseMavenDependencyProducesWarning() throws IOException {
        var result = runWithSource("""
                repositories {
                    maven {
                        url "https://www.cursemaven.com"
                        content {
                            includeGroup "curse.maven"
                        }
                    }
                }
                dependencies {
                    jarJar(implementation("curse.maven:jade-324717:5444008"))
                }
                """);
        assertEquals(SUCCESS, result.task(":jarJar").getOutcome());
        assertThat(result.getOutput()).contains("Embedding dependency curse.maven:jade-324717:5444008 from cursemaven using JiJ is likely to cause conflicts at runtime when other mods include the same library from a normal Maven repository.");
    }

    @Test
    void testEmbeddingmODRINTHMavenDependencyProducesWarning() throws IOException {
        var result = runWithSource("""
                repositories {
                    maven {
                        url = "https://api.modrinth.com/maven"
                        content {
                            includeGroup "maven.modrinth"
                        }
                    }
                }
                dependencies {
                    jarJar(implementation("maven.modrinth:lithium:mc1.19.2-0.10.0"))
                }
                """);
        assertEquals(SUCCESS, result.task(":jarJar").getOutcome());
        assertThat(result.getOutput()).contains("Embedding dependency maven.modrinth:lithium:mc1.19.2-0.10.0 from Modrinth Maven using JiJ is likely to cause conflicts at runtime when other mods include the same library from a normal Maven repository.");
    }

    @Test
    void testCannotEmbedLocalFileWithoutExplicitJavaModuleName() throws IOException {
        var localFile = testProjectDir.toPath().resolve("file.jar");
        new JarOutputStream(Files.newOutputStream(localFile), new Manifest()).close();

        var e = assertThrows(UnexpectedBuildFailure.class, () -> runWithSource("""
                dependencies {
                    jarJar(files("file.jar"))
                }
                """));
        assertThat(e).hasMessageFindingMatch("Cannot embed local file dependency .*file.jar because it has no explicit Java module name.\\s*" +
                "Please set either 'Automatic-Module-Name' in the Jar manifest, or make it an explicit Java module.\\s*" +
                "This ensures that your file does not conflict with another mods library that has the same or a similar filename.");
    }

    @Test
    void testCanEmbedLocalFileWithAutomaticModuleName() throws Exception {
        var localFile = testProjectDir.toPath().resolve("file.jar");
        var manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("Automatic-Module-Name", "super_duper_module");
        new JarOutputStream(Files.newOutputStream(localFile), manifest).close();
        var md5Hash = FileUtils.hashFile(localFile.toFile(), "MD5");

        var result = runWithSource("""
                dependencies {
                    jarJar(files("file.jar"))
                }
                """);
        assertEquals(SUCCESS, result.task(":jarJar").getOutcome());
        assertEquals(new Metadata(
                List.of(
                        new ContainedJarMetadata(
                                new ContainedJarIdentifier("", "super_duper_module"),
                                new ContainedVersion(VersionRange.createFromVersionSpec("[" + md5Hash + "]"), new DefaultArtifactVersion(md5Hash)),
                                "META-INF/jarjar/file.jar",
                                false))),
                readMetadata());
    }

    /**
     * The default capability of a subproject uses group=name of the root project
     */
    @Test
    void testEmbeddingSubprojectUsesDefaultCapabilityCoordinate() throws Exception {
        writeProjectFile("settings.gradle", """
                plugins {
                    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
                }
                rootProject.name = 'root_project_name'

                include ':plugin'
                """);
        writeProjectFile("build.gradle", """
                plugins {
                  id "net.neoforged.moddev"
                }
                dependencies {
                    jarJar(project(":plugin"))
                }
                """);
        writeProjectFile("plugin/build.gradle", """
                plugins {
                  id 'java'
                }
                version = "9.0.0"
                """);

        var result = run();
        assertEquals(SUCCESS, result.task(":jarJar").getOutcome());
        assertEquals(new Metadata(
                List.of(
                        new ContainedJarMetadata(
                                new ContainedJarIdentifier("root_project_name", "plugin"),
                                new ContainedVersion(VersionRange.createFromVersionSpec("[9.0.0,)"), new DefaultArtifactVersion("9.0.0")),
                                "META-INF/jarjar/root_project_name.plugin-9.0.0.jar",
                                false))),
                readMetadata());
    }

    /**
     * The default capability of a subproject uses group=name of the root project
     */
    @Test
    void testEmbeddingSubprojectWithExplicitGroupIdSet() throws Exception {
        writeProjectFile("settings.gradle", """
                plugins {
                    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
                }
                rootProject.name = 'root_project_name'

                include ':plugin'
                """);
        writeProjectFile("build.gradle", """
                plugins {
                  id "net.neoforged.moddev"
                }
                dependencies {
                    jarJar(project(":plugin"))
                }
                """);
        writeProjectFile("plugin/build.gradle", """
                plugins {
                  id 'java'
                }
                version = "9.0.0"
                group = "net.somegroup"
                """);

        var result = run();
        assertEquals(SUCCESS, result.task(":jarJar").getOutcome());
        assertEquals(new Metadata(
                List.of(
                        new ContainedJarMetadata(
                                new ContainedJarIdentifier("net.somegroup", "plugin"),
                                new ContainedVersion(VersionRange.createFromVersionSpec("[9.0.0,)"), new DefaultArtifactVersion("9.0.0")),
                                "META-INF/jarjar/net.somegroup.plugin-9.0.0.jar",
                                false))),
                readMetadata());
    }

    @Test
    void testCanEmbedLocalFileWithModuleInfo() throws Exception {
        var moduleInfoJava = testProjectDir.toPath().resolve("src/plugin/java/module-info.java");
        Files.createDirectories(moduleInfoJava.getParent());
        Files.writeString(moduleInfoJava, "module super_duper_module {}");

        var result = runWithSource("""
                sourceSets {
                    plugin
                }
                compilePluginJava {
                    // otherwise testkit needs to run with J21
                    options.release = 17
                }
                var pluginJar = tasks.register(sourceSets.plugin.jarTaskName, Jar) {
                    from sourceSets.plugin.output
                    archiveClassifier = "plugin"
                }
                dependencies {
                    jarJar(files(pluginJar))
                }
                """);
        assertEquals(SUCCESS, result.task(":jarJar").getOutcome());
        var md5Hash = FileUtils.hashFile(new File(testProjectDir, "build/libs/jijtest-plugin.jar"), "MD5");
        assertEquals(new Metadata(
                List.of(
                        new ContainedJarMetadata(
                                new ContainedJarIdentifier("", "super_duper_module"),
                                new ContainedVersion(VersionRange.createFromVersionSpec("[" + md5Hash + "]"), new DefaultArtifactVersion(md5Hash)),
                                "META-INF/jarjar/jijtest-plugin.jar",
                                false))),
                readMetadata());
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
                "META-INF/jarjar/metadata.json", "META-INF/jarjar/slf4j-api-2.0.13.jar");
        assertEquals(new Metadata(
                List.of(
                        new ContainedJarMetadata(
                                new ContainedJarIdentifier("org.slf4j", "slf4j-api"),
                                new ContainedVersion(VersionRange.createFromVersionSpec("[0.1,3.0)"), new DefaultArtifactVersion("2.0.13")),
                                "META-INF/jarjar/slf4j-api-2.0.13.jar",
                                false))),
                readMetadata());
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
                "META-INF/jarjar/metadata.json", "META-INF/jarjar/slf4j-api-2.0.13.jar");
        assertEquals(new Metadata(
                List.of(
                        new ContainedJarMetadata(
                                new ContainedJarIdentifier("org.slf4j", "slf4j-api"),
                                new ContainedVersion(VersionRange.createFromVersionSpec("[2.0.13,)"), new DefaultArtifactVersion("2.0.13")),
                                "META-INF/jarjar/slf4j-api-2.0.13.jar",
                                false))),
                readMetadata());
    }

    /**
     * When a single version is specified, the jarjar metadata should use the real resolved version,
     * rather than the one the user indicated (which can get upgraded).
     */
    @Test
    public void testSimpleStringVersionPullsRangeFromResolution() throws Exception {
        // Note that slf4j 0.0.1 does not exist!
        var result = runWithSource("""
                dependencies {
                    jarJar(implementation("org.slf4j:slf4j-api:0.0.1"))
                    constraints {
                        jarJar(implementation("org.slf4j:slf4j-api:2.0.13"))
                    }
                }
                """);
        assertEquals(SUCCESS, result.task(":jarJar").getOutcome());

        assertThat(listFiles()).containsOnly(
                "META-INF/jarjar/metadata.json", "META-INF/jarjar/slf4j-api-2.0.13.jar");
        assertEquals(new Metadata(
                List.of(
                        new ContainedJarMetadata(
                                new ContainedJarIdentifier("org.slf4j", "slf4j-api"),
                                new ContainedVersion(VersionRange.createFromVersionSpec("[2.0.13,)"), new DefaultArtifactVersion("2.0.13")),
                                "META-INF/jarjar/slf4j-api-2.0.13.jar",
                                false))),
                readMetadata());
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

    /**
     * {@code x[} is alternative notation for {@code x)} in math and Gradle. Maven does not support it, so we should reject it.
     */
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
        writeProjectFile("settings.gradle", """
                plugins {
                    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
                }
                rootProject.name = 'jijtest'
                """);
        writeProjectFile("build.gradle", """
                plugins {
                  id "net.neoforged.moddev"
                }
                repositories {
                  mavenCentral()
                }
                """ + source);

        return run();
    }

    private BuildResult run() {
        return GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir)
                .withArguments("jarjar", "--stacktrace")
                .withDebug(true)
                .build();
    }

    private List<String> listFiles() throws IOException {
        var path = testProjectDir.toPath().resolve("build/generated/jarJar");
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
        try (var in = Files.newInputStream(testProjectDir.toPath().resolve("build/generated/jarJar/META-INF/jarjar/metadata.json"))) {
            return MetadataIOHandler.fromStream(in).orElseThrow();
        }
    }
}
