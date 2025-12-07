package net.neoforged.moddevgradle.legacyforge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import net.neoforged.moddevgradle.AbstractProjectBuilderTest;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import net.neoforged.moddevgradle.legacyforge.dsl.LegacyForgeExtension;
import net.neoforged.moddevgradle.legacyforge.internal.LegacyForgeModDevPlugin;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

public class LegacyModDevPluginTest extends AbstractProjectBuilderTest {
    private static final String[] MODDING_COMPILE_DEPENDENCIES = {
            "build/moddev/artifacts/forge-1.2.3.jar",
            "net.minecraftforge:forge:1.2.3[net.neoforged:neoforge-dependencies]"
    };
    private static final String[] MODDING_RUNTIME_ONLY_DEPENDENCIES = {
            "build/moddev/artifacts/client-extra-1.2.3.jar",
            "build/moddev/artifacts/intermediateToNamed.zip",
    };
    public static final String VERSION = "1.2.3";

    private final LegacyForgeExtension extension;
    private final SourceSet mainSourceSet;
    private final SourceSet testSourceSet;

    public LegacyModDevPluginTest() {
        project = ProjectBuilder.builder().build();
        project.getPlugins().apply(LegacyForgeModDevPlugin.class);

        extension = ExtensionUtils.getExtension(project, "legacyForge", LegacyForgeExtension.class);

        var sourceSets = ExtensionUtils.getSourceSets(project);
        mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        testSourceSet = sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME);

        // Set the Java version to the currently running Java to make it use that
        var java = ExtensionUtils.getExtension(project, "java", JavaPluginExtension.class);
        java.getToolchain().getLanguageVersion().set(JavaLanguageVersion.current());
    }

    @Test
    void testModdingCannotBeEnabledTwice() {
        extension.setVersion(VERSION);
        var e = assertThrows(InvalidUserCodeException.class, () -> extension.setVersion(VERSION));
        assertThat(e).hasMessage("You cannot enable modding in the same project twice.");
    }

    @Test
    void testEnableVanillaOnlyMode() {
        extension.setMcpVersion("1.17.1");

        assertThatDependencies(mainSourceSet.getCompileClasspathConfigurationName())
                .contains(
                        "build/moddev/artifacts/vanilla-1.17.1.jar",
                        "de.oceanlabs.mcp:mcp_config:1.17.1[net.neoforged:neoform-dependencies]");
        assertThatDependencies(mainSourceSet.getCompileClasspathConfigurationName())
                .doesNotContain(
                        "build/moddev/artifacts/vanilla-1.17.1-client-extra-aka-minecraft-resources.jar",
                        "build/moddev/artifacts/intermediateToNamed.zip");
        assertThatDependencies(mainSourceSet.getRuntimeClasspathConfigurationName())
                .contains(
                        "build/moddev/artifacts/vanilla-1.17.1.jar",
                        "build/moddev/artifacts/vanilla-1.17.1-client-extra-aka-minecraft-resources.jar",
                        "de.oceanlabs.mcp:mcp_config:1.17.1[net.neoforged:neoform-dependencies]",
                        "build/moddev/artifacts/intermediateToNamed.zip");
        assertEquals("1.17.1", extension.getMcpVersion());
    }

    @Test
    void testGetMcpVersionThrowsBeforeEnabling() {
        assertThrows(InvalidUserCodeException.class, extension::getMcpVersion);
    }

    @Test
    void testEnableForTestSourceSetOnly() {
        extension.enable(settings -> {
            settings.setForgeVersion(VERSION);
            settings.setEnabledSourceSets(Set.of(testSourceSet));
        });

        // Both the compile and runtime classpath of the main source set had no dependencies added
        assertDoesNotContainModdingDependencies(mainSourceSet.getCompileClasspathConfigurationName());
        assertDoesNotContainModdingDependencies(mainSourceSet.getRuntimeClasspathConfigurationName());

        // While the test classpath should have modding dependencies
        assertContainsModdingCompileDependencies(testSourceSet.getCompileClasspathConfigurationName());
        assertContainsModdingRuntimeDependencies(testSourceSet.getRuntimeClasspathConfigurationName());
    }

    @Test
    void testAddModdingDependenciesTo() {
        extension.setVersion(VERSION);

        // Initially, only the main source set should have the dependencies
        assertContainsModdingCompileDependencies(mainSourceSet.getCompileClasspathConfigurationName());
        assertContainsModdingRuntimeDependencies(mainSourceSet.getRuntimeClasspathConfigurationName());
        assertDoesNotContainModdingDependencies(testSourceSet.getCompileClasspathConfigurationName());
        assertDoesNotContainModdingDependencies(testSourceSet.getRuntimeClasspathConfigurationName());

        // Now add it to the test source set too
        extension.addModdingDependenciesTo(testSourceSet);

        assertContainsModdingCompileDependencies(testSourceSet.getCompileClasspathConfigurationName());
        assertContainsModdingRuntimeDependencies(testSourceSet.getRuntimeClasspathConfigurationName());
    }

    @Test
    void testEnableWithoutReobfTask() {
        extension.enable(settings -> {
            settings.setForgeVersion(VERSION);
            settings.setObfuscateJar(false);
        });

        assertNull(project.getTasks().findByName("reobfJar"));
    }

    private void assertDoesNotContainModdingDependencies(String configurationName) {
        assertThatDependencies(configurationName).doesNotContain(MODDING_COMPILE_DEPENDENCIES);
        assertThatDependencies(configurationName).doesNotContain(MODDING_RUNTIME_ONLY_DEPENDENCIES);
    }

    private void assertContainsModdingCompileDependencies(String configurationName) {
        assertThatDependencies(configurationName).contains(MODDING_COMPILE_DEPENDENCIES);
        assertThatDependencies(configurationName).doesNotContain(MODDING_RUNTIME_ONLY_DEPENDENCIES);
    }

    private void assertContainsModdingRuntimeDependencies(String configurationName) {
        var configuration = project.getConfigurations().getByName(configurationName);

        var dependentTasks = configuration.getBuildDependencies().getDependencies(null);
        assertThat(dependentTasks)
                .extracting(Task::getName)
                .containsOnly("createMinecraftArtifacts");

        assertThatDependencies(configurationName).contains(MODDING_COMPILE_DEPENDENCIES);
        assertThatDependencies(configurationName).contains(MODDING_RUNTIME_ONLY_DEPENDENCIES);
    }
}
