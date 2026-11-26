package net.neoforged.moddevgradle.functional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

public class VanillaRunFunctionalTest extends AbstractFunctionalTest {
    @Test
    public void testVanillaRunsUseDedicatedRuntimeClasspath() throws IOException {
        writeFile(settingsFile, "rootProject.name = 'vanilla-runs'");
        writeGroovyBuildScript(readTestResource("net/neoforged/moddevgradle/functional/vanilla-runs.gradle"));

        var result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir)
                .withArguments("assertVanillaRuns", "--stacktrace")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":assertVanillaRuns").getOutcome());
    }

    @Test
    public void testVanillaRunTaskGraphCanBeScheduled() throws IOException {
        writeFile(settingsFile, "rootProject.name = 'vanilla-runs'");
        writeGroovyBuildScript(readTestResource("net/neoforged/moddevgradle/functional/vanilla-runs.gradle"));

        var result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir)
                .withArguments("runVanillaClient", "--dry-run", "--stacktrace")
                .build();

        assertThat(result.getOutput()).contains(":runVanillaClient SKIPPED");
    }
}
