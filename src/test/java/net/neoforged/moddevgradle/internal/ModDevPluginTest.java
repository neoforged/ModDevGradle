package net.neoforged.moddevgradle.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import net.neoforged.moddevgradle.AbstractProjectBuilderTest;
import net.neoforged.moddevgradle.dsl.NeoForgeExtension;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import net.neoforged.moddevgradle.internal.utils.VersionCapabilitiesInternal;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class ModDevPluginTest extends AbstractProjectBuilderTest {
    private final NeoForgeExtension extension;
    private final SourceSet mainSourceSet;
    private final SourceSet testSourceSet;

    public ModDevPluginTest() {
        project = ProjectBuilder.builder().build();
        project.getPlugins().apply(ModDevPlugin.class);

        extension = ExtensionUtils.getExtension(project, "neoForge", NeoForgeExtension.class);

        var sourceSets = ExtensionUtils.getSourceSets(project);
        mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        testSourceSet = sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME);

        // Set the Java version to the currently running Java to make it use that
        var java = ExtensionUtils.getExtension(project, "java", JavaPluginExtension.class);
        java.getToolchain().getLanguageVersion().set(JavaLanguageVersion.current());
    }

    @Test
    void testModdingCannotBeEnabledTwice() {
        extension.setVersion("2.3.0");
        var e = assertThrows(InvalidUserCodeException.class, () -> extension.setVersion("2.3.0"));
        assertThat(e).hasMessage("You cannot enable modding in the same project twice.");
    }

    @Test
    void testEnableForTestSourceSetOnly() {
        extension.enable(settings -> {
            settings.setVersion("100.3.0"); // Needs to be at least 20.5 to use paths for newer FML
            settings.setEnabledSourceSets(Set.of(testSourceSet));
        });

        // Both the compile and runtime classpath of the main source set had no dependencies added
        assertThatDependencies(mainSourceSet.getCompileClasspathConfigurationName()).isEmpty();
        assertThatDependencies(mainSourceSet.getRuntimeClasspathConfigurationName()).isEmpty();

        // While the test classpath should have modding dependencies
        assertContainsModdingCompileDependencies("100.3.0", testSourceSet.getCompileClasspathConfigurationName());
        assertContainsModdingRuntimeDependencies("100.3.0", testSourceSet.getRuntimeClasspathConfigurationName());
    }

    @Test
    void testAddModdingDependenciesTo() {
        extension.setVersion("100.3.0"); // Needs to be at least 20.5 to use paths for newer FML

        // Initially, only the main source set should have the dependencies
        assertContainsModdingCompileDependencies("100.3.0", mainSourceSet.getCompileClasspathConfigurationName());
        assertContainsModdingRuntimeDependencies("100.3.0", mainSourceSet.getRuntimeClasspathConfigurationName());
        assertThatDependencies(testSourceSet.getCompileClasspathConfigurationName()).isEmpty();
        assertThatDependencies(testSourceSet.getRuntimeClasspathConfigurationName()).isEmpty();

        // Now add it to the test source set too
        extension.addModdingDependenciesTo(testSourceSet);

        assertContainsModdingCompileDependencies("100.3.0", testSourceSet.getCompileClasspathConfigurationName());
        assertContainsModdingRuntimeDependencies("100.3.0", testSourceSet.getRuntimeClasspathConfigurationName());
    }

    @Test
    void testGetVersion() {
        extension.setVersion("2.3.0");
        assertEquals("2.3.0", extension.getVersion());
    }

    @Test
    void testGetVersionCapabilities() {
        extension.setVersion("2.3.0");
        assertEquals(VersionCapabilitiesInternal.ofMinecraftVersion("1.2.3"), extension.getVersionCapabilities());
        assertEquals("1.2.3", extension.getMinecraftVersion());
    }

    @Test
    void testGetMinecraftVersion() {
        extension.setVersion("2.3.0-suffixstuff");
        assertEquals("1.2.3", extension.getMinecraftVersion());
    }

    @Nested
    class VanillaOnlyMode {
        final static String VERSION = "1.21.4-20240101.235959";

        @Test
        void testEnable() {
            extension.setNeoFormVersion(VERSION);

            assertThatDependencies(mainSourceSet.getCompileClasspathConfigurationName())
                    .containsOnly(
                            "build/moddev/artifacts/vanilla-" + VERSION + ".jar",
                            "net.neoforged:neoform:" + VERSION + "[net.neoforged:neoform-dependencies]");
            assertThatDependencies(mainSourceSet.getRuntimeClasspathConfigurationName())
                    .containsOnly(
                            "build/moddev/artifacts/vanilla-" + VERSION + ".jar",
                            "build/moddev/artifacts/vanilla-" + VERSION + "-client-extra-aka-minecraft-resources.jar",
                            "net.neoforged:neoform:" + VERSION + "[net.neoforged:neoform-dependencies]");
        }

        @Test
        void testGetVersion() {
            extension.setNeoFormVersion(VERSION);
            assertEquals(VERSION, extension.getNeoFormVersion());
        }

        @Test
        void testGetMinecraftVersion() {
            extension.setNeoFormVersion(VERSION);
            assertEquals("1.21.4", extension.getMinecraftVersion());
        }

        @Test
        void testGetVersionCapabilities() {
            extension.setNeoFormVersion(VERSION);
            assertEquals(VersionCapabilitiesInternal.ofNeoFormVersion(VERSION), extension.getVersionCapabilities());
        }

        @Test
        void testGetVersionCapabilitiesForUnknownVersion() {
            extension.setNeoFormVersion("1.99.1-20990101.235959");
            // Should use latest features, but with the specified Minecraft version
            assertEquals(
                    VersionCapabilitiesInternal.latest().withMinecraftVersion("1.99.1"),
                    extension.getVersionCapabilities());
        }
    }

    @Nested
    class CannotCallWhenModdingIsNotEnabled {
        static String expectedMessage = "Mod development has not been enabled yet for project root project 'test'";

        @Test
        void testGettingMinecraftVersionThrows() {
            var e = assertThrows(InvalidUserCodeException.class, extension::getVersionCapabilities);
            assertThat(e).hasMessage(expectedMessage);
        }

        @Test
        void testGettingVersionCapabilitiesThrows() {
            var e = assertThrows(InvalidUserCodeException.class, extension::getVersion);
            assertThat(e).hasMessage(expectedMessage);
        }

        @Test
        void testAddModdingDependenciesToThrows() {
            var e = assertThrows(InvalidUserCodeException.class, () -> extension.addModdingDependenciesTo(mainSourceSet));
            assertThat(e).hasMessage(expectedMessage);
        }
    }

    private void assertContainsModdingCompileDependencies(String version, String configurationName) {
        assertThatDependencies(configurationName)
                .containsOnly(
                        "build/moddev/artifacts/neoforge-" + version + ".jar",
                        "net.neoforged:neoforge:" + version + "[net.neoforged:neoforge-dependencies]");
    }

    private void assertContainsModdingRuntimeDependencies(String version, String configurationName) {
        var configuration = project.getConfigurations().getByName(configurationName);

        var dependentTasks = configuration.getBuildDependencies().getDependencies(null);
        assertThat(dependentTasks)
                .extracting(Task::getName)
                .containsOnly("createMinecraftArtifacts");

        assertThatDependencies(configurationName)
                .containsOnly(
                        "build/moddev/artifacts/neoforge-" + version + ".jar",
                        "build/moddev/artifacts/neoforge-" + version + "-client-extra-aka-minecraft-resources.jar",
                        "net.neoforged:neoforge:" + version + "[net.neoforged:neoforge-dependencies]");
    }
}
