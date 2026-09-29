package net.neoforged.moddevgradle.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.neoforged.moddevgradle.AbstractProjectBuilderTest;
import net.neoforged.moddevgradle.dsl.NeoForgeExtension;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import net.neoforged.moddevgradle.internal.utils.VersionCapabilitiesInternal;
import net.neoforged.nfrtgradle.CreateMinecraftArtifacts;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.testfixtures.ProjectBuilder;
import org.jetbrains.gradle.ext.Gradle;
import org.jetbrains.gradle.ext.ProjectSettings;
import org.jetbrains.gradle.ext.RunConfigurationContainer;
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

        configureCurrentJavaToolchain(project);
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
    void testVanillaRuntimeDependencyUsesKnownArtifactPath() {
        extension.setVersion("21.11.0");

        var dependency = ModDevArtifactsWorkflow.get(project)
                .requestVanillaMinecraftClassesDependency()
                .get();

        assertThat(describeDependency(dependency))
                .isEqualTo("build/moddev/artifacts/vanilla-runtime-1.21.11.jar");
        assertThat(dependency).isInstanceOf(FileCollectionDependency.class);
        assertThat(((FileCollectionDependency) dependency).getFiles().getBuildDependencies().getDependencies(null))
                .extracting(Task::getName)
                .containsOnly("createMinecraftArtifacts");

        var createArtifacts = project.getTasks().named("createMinecraftArtifacts", CreateMinecraftArtifacts.class).get();
        assertThat(createArtifacts.getAdditionalResults().get())
                .containsOnlyKeys("vanillaDeobfuscated");
    }

    @Test
    void testVanillaRuntimeDependenciesDoNotUseNeoForge() {
        extension.setVersion("21.11.0");

        assertThatDependencies("modDevVanillaRuntimeDependencies")
                .containsOnly("net.neoforged:minecraft-dependencies:1.21.11");
        assertDoesNotContainNeoForgeDependency("modDevVanillaRuntimeDependencies");
    }

    @Test
    void testVanillaServerRunDefaultsToDedicatedVanillaRuntime() {
        extension.getMods().create("mainmod", mod -> mod.sourceSet(mainSourceSet));
        extension.getRuns().create("vanilla", run -> run.vanillaServer());

        var run = extension.getRuns().getByName("vanilla");
        assertThat(run.getType().get()).isEqualTo("server");
        assertThat(run.getUseVanillaRunTemplates().get()).isTrue();
        assertThat(run.getLoadedMods().get()).isEmpty();
    }

    @Test
    void testVanillaClientRunDefaultsToDedicatedVanillaRuntime() {
        extension.getMods().create("mainmod", mod -> mod.sourceSet(mainSourceSet));
        extension.getRuns().create("vanilla", run -> run.vanillaClient());

        var run = extension.getRuns().getByName("vanilla");
        assertThat(run.getType().get()).isEqualTo("client");
        assertThat(run.getUseVanillaRunTemplates().get()).isTrue();
        assertThat(run.getLoadedMods().get()).isEmpty();
    }

    @Test
    void testVanillaIntelliJRunUsesGradleTaskClasspath() {
        withSystemProperty("idea.sync.active", "true", () -> {
            var ideProject = createProjectWithModDevPlugin();
            createVanillaServerRun(ideProject);

            evaluateProject(ideProject);

            var runConfiguration = getIntelliJRunConfigurations(ideProject).getByName("Vanilla");
            assertThat(runConfiguration).isInstanceOf(Gradle.class);

            var runConfigurationData = runConfiguration.toMap();
            assertThat(runConfigurationData.get("type")).isEqualTo("gradle");
            assertThat(runConfigurationData.get("taskNames")).isEqualTo(List.of(":runVanilla"));
        });
    }

    @Test
    void testVanillaEclipseRunUsesGradleTaskClasspath() throws IOException {
        Project ideProject = withSystemProperty("eclipse.application", "org.eclipse.buildship.core", () -> {
            var result = createProjectWithModDevPlugin();
            createVanillaServerRun(result);

            evaluateProject(result);
            return result;
        });

        var launchConfig = Files.readString(ideProject.file(".eclipse/configurations/Vanilla.launch").toPath());
        assertThat(launchConfig)
                .contains("org.eclipse.buildship.core.launch.runconfiguration")
                .contains(":runVanilla")
                .doesNotContain("org.eclipse.jdt.launching.localJavaApplication");
    }

    @Test
    void testVanillaVsCodeRunUsesGeneratedClasspathArgfile() throws IOException {
        var ideProject = createProjectWithVsCodeIntegration();
        createVanillaServerRun(ideProject);

        evaluateProject(ideProject);

        var launchJson = Files.readString(ideProject.file(".vscode/launch.json").toPath());
        assertThat(launchJson)
                .contains("\"classPaths\": []")
                .contains("vanillaRunClasspath.txt")
                .doesNotContain("\"$Runtime\"");

        assertThat(ideProject.getTasks().named("neoForgeIdeSync").get().getTaskDependencies().getDependencies(null))
                .extracting(Task::getName)
                .contains("createVanillaLaunchScript");
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

    private void assertDoesNotContainNeoForgeDependency(String configurationName) {
        var dependencies = project.getConfigurations().getByName(configurationName).getAllDependencies();
        assertThat(dependencies).allSatisfy(dependency -> {
            if (dependency instanceof ExternalModuleDependency moduleDependency) {
                assertThat(moduleDependency.getName()).isNotEqualTo("neoforge");
                assertThat(moduleDependency.getRequestedCapabilities())
                        .extracting("name")
                        .doesNotContain("neoforge-dependencies", "neoforge-moddev-bundle", "neoforge-moddev-config", "neoforge-moddev-module-path");
            }
        });
    }

    private static Project createProjectWithModDevPlugin() {
        var result = ProjectBuilder.builder().build();
        result.getPlugins().apply(ModDevPlugin.class);
        configureCurrentJavaToolchain(result);
        return result;
    }

    private static Project createProjectWithVsCodeIntegration() {
        var result = ProjectBuilder.builder().build();
        result.getExtensions().add(IdeIntegration.class, "mdgInternalIdeIntegration", new VsCodeIntegration(result, Branding.MDG));
        result.getPlugins().apply(ModDevPlugin.class);
        configureCurrentJavaToolchain(result);
        return result;
    }

    private static void configureCurrentJavaToolchain(Project project) {
        var java = ExtensionUtils.getExtension(project, "java", JavaPluginExtension.class);
        java.getToolchain().getLanguageVersion().set(JavaLanguageVersion.current());
    }

    private static NeoForgeExtension getNeoForgeExtension(Project project) {
        return ExtensionUtils.getExtension(project, "neoForge", NeoForgeExtension.class);
    }

    private static void createVanillaServerRun(Project project) {
        var extension = getNeoForgeExtension(project);
        extension.setVersion("21.11.0");
        extension.getRuns().create("vanilla", run -> run.vanillaServer());
    }

    private static void evaluateProject(Project project) {
        ((ProjectInternal) project).evaluate();
    }

    private static RunConfigurationContainer getIntelliJRunConfigurations(Project project) {
        var rootIdeaModel = ExtensionUtils.getExtension(project.getRootProject(), "idea", IdeaModel.class);
        var projectSettings = ((ExtensionAware) rootIdeaModel.getProject()).getExtensions().getByType(ProjectSettings.class);
        var runConfigurations = ExtensionUtils.findExtension((ExtensionAware) projectSettings, "runConfigurations", RunConfigurationContainer.class);
        assertThat(runConfigurations).isNotNull();
        return runConfigurations;
    }

    private static void withSystemProperty(String name, String value, Runnable runnable) {
        withSystemProperty(name, value, () -> {
            runnable.run();
            return null;
        });
    }

    private static <T> T withSystemProperty(String name, String value, Supplier<T> supplier) {
        var previousValue = System.getProperty(name);
        System.setProperty(name, value);
        try {
            return supplier.get();
        } finally {
            if (previousValue == null) {
                System.clearProperty(name);
            } else {
                System.setProperty(name, previousValue);
            }
        }
    }
}
