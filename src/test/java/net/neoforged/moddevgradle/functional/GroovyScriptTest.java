package net.neoforged.moddevgradle.functional;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GroovyScriptTest extends AbstractFunctionalTest {

    @Test
    public void testApplyInEmptyProject() throws IOException {
        writeFile(settingsFile, "rootProject.name = 'hello-world'");
        String buildFileContent = """
                plugins {
                    id "net.neoforged.moddev"
                }
                """;
        writeFile(buildFile, buildFileContent);

        BuildResult result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir)
                .withArguments("tasks", "--all")
                .build();

        assertThat(result.getOutput()).contains("createMinecraftArtifacts");
        assertEquals(TaskOutcome.SUCCESS, result.task(":tasks").getOutcome());
    }

}
