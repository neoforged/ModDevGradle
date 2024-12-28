package net.neoforged.moddevgradle.internal;

import net.neoforged.moddevgradle.AbstractProjectBuilderTest;
import net.neoforged.moddevgradle.dsl.NeoForgeExtension;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        extension.setVersion("1.2.3");
        var e = assertThrows(InvalidUserCodeException.class, () -> extension.setVersion("1.2.3"));
        assertThat(e).hasMessage("You cannot enable modding in the same project twice.");
    }

    @Test
    void testEnableVanillaOnlyMode() {
        extension.setNeoFormVersion("1.2.3");

        assertThatDependencies(mainSourceSet.getCompileClasspathConfigurationName())
                .containsOnly(
                        "build/moddev/artifacts/vanilla-1.2.3.jar",
                        "net.neoforged:neoform:1.2.3[net.neoforged:neoform-dependencies]"
                );
        assertThatDependencies(mainSourceSet.getRuntimeClasspathConfigurationName())
                .containsOnly(
                        "build/moddev/artifacts/vanilla-1.2.3.jar",
                        "build/moddev/artifacts/vanilla-1.2.3-client-extra-aka-minecraft-resources.jar",
                        "net.neoforged:neoform:1.2.3[net.neoforged:neoform-dependencies]"
                );
    }

    @Test
    void testEnableForTestSourceSetOnly() {
        extension.enable(settings -> {
            settings.setVersion("1.2.3");
            settings.setEnabledSourceSets(Set.of(testSourceSet));
        });

        // Both the compile and runtime classpath of the main source set had no dependencies added
        assertThatDependencies(mainSourceSet.getCompileClasspathConfigurationName()).isEmpty();
        assertThatDependencies(mainSourceSet.getRuntimeClasspathConfigurationName()).isEmpty();

        // While the test classpath should have modding dependencies
        assertContainsModdingCompileDependencies("1.2.3", testSourceSet.getCompileClasspathConfigurationName());
        assertContainsModdingRuntimeDependencies("1.2.3", testSourceSet.getRuntimeClasspathConfigurationName());
    }

    @Test
    void testAddModdingDependenciesTo() {
        extension.setVersion("1.2.3");

        // Initially, only the main source set should have the dependencies
        assertContainsModdingCompileDependencies("1.2.3", mainSourceSet.getCompileClasspathConfigurationName());
        assertContainsModdingRuntimeDependencies("1.2.3", mainSourceSet.getRuntimeClasspathConfigurationName());
        assertThatDependencies(testSourceSet.getCompileClasspathConfigurationName()).isEmpty();
        assertThatDependencies(testSourceSet.getRuntimeClasspathConfigurationName()).isEmpty();

        // Now add it to the test source set too
        extension.addModdingDependenciesTo(testSourceSet);

        assertContainsModdingCompileDependencies("1.2.3", testSourceSet.getCompileClasspathConfigurationName());
        assertContainsModdingRuntimeDependencies("1.2.3", testSourceSet.getRuntimeClasspathConfigurationName());
    }

    private void assertContainsModdingCompileDependencies(String version, String configurationName) {
        assertThatDependencies(configurationName)
                .containsOnly(
                        "build/moddev/artifacts/neoforge-" + version + ".jar",
                        "net.neoforged:neoforge:" + version + "[net.neoforged:neoforge-dependencies]"
                );
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
                        "net.neoforged:neoforge:" + version + "[net.neoforged:neoforge-dependencies]"
                );
    }
}
