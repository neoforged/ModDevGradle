package net.neoforged.moddevgradle.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Set;
import net.neoforged.moddevgradle.AbstractProjectBuilderTest;
import net.neoforged.moddevgradle.dsl.NeoForgeExtension;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import net.neoforged.moddevgradle.internal.utils.VersionCapabilitiesInternal;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Task;
import org.gradle.api.artifacts.repositories.RepositoryContentDescriptor;
import org.gradle.api.attributes.Attribute;
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
            settings.setVersion("21.11.0"); // Needs to be at least 20.5 to use paths for newer FML
            settings.setEnabledSourceSets(Set.of(testSourceSet));
        });

        // Both the compile and runtime classpath of the main source set had no dependencies added
        assertThatDependencies(mainSourceSet.getCompileClasspathConfigurationName()).isEmpty();
        assertThatDependencies(mainSourceSet.getRuntimeClasspathConfigurationName()).isEmpty();

        // While the test classpath should have modding dependencies
        assertContainsModdingCompileDependencies("21.11.0", testSourceSet.getCompileClasspathConfigurationName());
        assertContainsModdingRuntimeDependencies("21.11.0", testSourceSet.getRuntimeClasspathConfigurationName());
    }

    @Test
    void testAddModdingDependenciesTo() {
        extension.setVersion("21.11.0"); // Needs to be at least 20.5 to use paths for newer FML

        // Initially, only the main source set should have the dependencies
        assertContainsModdingCompileDependencies("21.11.0", mainSourceSet.getCompileClasspathConfigurationName());
        assertContainsModdingRuntimeDependencies("21.11.0", mainSourceSet.getRuntimeClasspathConfigurationName());
        assertThatDependencies(testSourceSet.getCompileClasspathConfigurationName()).isEmpty();
        assertThatDependencies(testSourceSet.getRuntimeClasspathConfigurationName()).isEmpty();

        // Now add it to the test source set too
        extension.addModdingDependenciesTo(testSourceSet);

        assertContainsModdingCompileDependencies("21.11.0", testSourceSet.getCompileClasspathConfigurationName());
        assertContainsModdingRuntimeDependencies("21.11.0", testSourceSet.getRuntimeClasspathConfigurationName());
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

    @Nested
    class RepositoryFilter {
        @Test
        void testFilterIncludesStableModules() {
            var descriptor = new RecordingDescriptor();
            NeoForgedRepositoryFilter.filter(descriptor, Set.of());

            // The stable baseline must include NeoForge's own artifacts.
            assertThat(descriptor.included).anyMatch(
                    r -> r[0].equals("net.neoforged") && r[1].equals("neoforge"));
        }

        @Test
        void testFilterIncludesDynamicModules() {
            var descriptor = new RecordingDescriptor();
            var dynamic = Set.of("com.example:new-lib", "org.test:another");
            NeoForgedRepositoryFilter.filter(descriptor, dynamic);

            assertThat(descriptor.included).anyMatch(
                    r -> r[0].equals("com.example") && r[1].equals("new-lib"));
            assertThat(descriptor.included).anyMatch(
                    r -> r[0].equals("org.test") && r[1].equals("another"));
        }

        @Test
        void testFilterIgnoresMalformedDynamicEntries() {
            var descriptor = new RecordingDescriptor();
            // Entries without a colon are skipped gracefully.
            NeoForgedRepositoryFilter.filter(descriptor, Set.of("malformed"));

            // Stable modules still included; malformed entry did not throw.
            assertThat(descriptor.included).isNotEmpty();
            assertThat(descriptor.included).noneMatch(
                    r -> r[0].equals("malformed"));
        }

        @Test
        void testContentFilterAppliedInNeoForgeMode() {
            extension.setVersion("21.10.48-beta");

            var neoRepo = RepositoriesPlugin.getNeoForgeRepository(project);
            assertThat(neoRepo).isNotNull();
            assertThat(project.getRepositories().stream()
                    .filter(r -> "NeoForged Releases".equals(r.getName()))
                    .findFirst()).isPresent();
        }

        @Test
        void testContentFilterAppliedInVanillaOnlyMode() {
            extension.setNeoFormVersion("1.21.4-20240101.235959");

            var neoRepo = RepositoriesPlugin.getNeoForgeRepository(project);
            assertThat(neoRepo).isNotNull();
            assertThat(project.getRepositories().stream()
                    .filter(r -> "NeoForged Releases".equals(r.getName()))
                    .findFirst()).isPresent();
        }

        @Test
        void testApplyContentFilterIsNoOpWhenRepositoryNotOnProject() {
            var freshProject = ProjectBuilder.builder().build();
            // Must not throw, even though there is no NeoForge repository extension.
            RepositoriesPlugin.applyContentFilter(freshProject, Set.of());
        }

        @Test
        void testApplyContentFilterDoesNotThrowWithEmptyDynamicSet() {
            extension.setVersion("21.10.48-beta");
            var neoRepo = RepositoriesPlugin.getNeoForgeRepository(project);
            assertThat(neoRepo).isNotNull();
            // Applying the filter with an empty dynamic set is valid (e.g. offline mode).
            RepositoriesPlugin.applyContentFilter(project, Set.of());
        }

        @Test
        void testParallelEnablesUseIsolatedDynamicSets() {
            // Simulate two projects enabling modding concurrently — each call
            // to populateNeoForgeRepositoryFilter passes its own local HashSet,
            // so there is no shared mutable state between them.
            extension.setVersion("21.10.48-beta");

            var project2 = ProjectBuilder.builder().build();
            project2.getPlugins().apply(ModDevPlugin.class);
            var ext2 = ExtensionUtils.getExtension(project2, "neoForge", NeoForgeExtension.class);
            var java2 = ExtensionUtils.getExtension(project2, "java", JavaPluginExtension.class);
            java2.getToolchain().getLanguageVersion().set(JavaLanguageVersion.current());
            ext2.setVersion("21.0.133-beta");

            // Both projects must have their NeoForge repository available without
            // cross-contamination of dynamic module sets.
            assertThat(RepositoriesPlugin.getNeoForgeRepository(project)).isNotNull();
            assertThat(RepositoriesPlugin.getNeoForgeRepository(project2)).isNotNull();
        }

        /**
         * A minimal {@link RepositoryContentDescriptor} that records every
         * {@code includeModule} call so tests can assert filter behavior.
         */
        static class RecordingDescriptor implements RepositoryContentDescriptor {
            final Set<String[]> included = new HashSet<>();

            @Override
            public void includeModule(String group, String name) {
                included.add(new String[] { group, name });
            }

            // Remaining methods are unused by NeoForgedRepositoryFilter; stub them out.
            @Override
            public void includeGroup(String group) {}

            @Override
            public void includeGroupAndSubgroups(String groupPrefix) {}

            @Override
            public void includeGroupByRegex(String groupRegex) {}

            @Override
            public void includeModuleByRegex(String groupRegex, String nameRegex) {}

            @Override
            public void includeVersion(String group, String name, String version) {}

            @Override
            public void includeVersionByRegex(String groupRegex, String nameRegex, String versionRegex) {}

            @Override
            public void excludeGroup(String group) {}

            @Override
            public void excludeGroupAndSubgroups(String groupPrefix) {}

            @Override
            public void excludeGroupByRegex(String groupRegex) {}

            @Override
            public void excludeModule(String group, String name) {}

            @Override
            public void excludeModuleByRegex(String groupRegex, String nameRegex) {}

            @Override
            public void excludeVersion(String group, String name, String version) {}

            @Override
            public void excludeVersionByRegex(String groupRegex, String nameRegex, String versionRegex) {}

            @Override
            public void onlyForConfigurations(String... configurationNames) {}

            @Override
            public void notForConfigurations(String... configurationNames) {}

            @Override
            public <T> void onlyForAttribute(Attribute<T> attribute, T... validValues) {}
        }
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
