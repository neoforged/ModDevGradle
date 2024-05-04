package net.neoforged.neoforgegradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;

abstract class PrepRepoTask extends DefaultTask {
    @InputFiles
    abstract RegularFileProperty getRepoFolder();

    @Inject
    public PrepRepoTask() {
    }

    @TaskAction
    public void prepRepo() throws IOException {
        var repoFolder = getRepoFolder().get().getAsFile().toPath();

        var jarFile = repoFolder.resolve("minecraft/minecraft-joined/local/minecraft-joined-local.jar");
        if (!Files.exists(jarFile)) {
            Files.write(jarFile, new byte[0]);
        }

        var pomFile = repoFolder.resolve("minecraft/minecraft-joined/local/minecraft-joined-local.pom");
        if (!Files.exists(jarFile)) {
            Files.writeString(pomFile, """
                    <project xmlns="http://maven.apache.org/POM/4.0.0"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                        <modelVersion>4.0.0</modelVersion>

                        <groupId>minecraft</groupId>
                        <artifactId>minecraft-joined</artifactId>
                        <version>local</version>
                        <packaging>jar</packaging>
                    </project>
                                       """);
        }
    }
}
