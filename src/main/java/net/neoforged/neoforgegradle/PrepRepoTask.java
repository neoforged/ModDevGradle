package net.neoforged.neoforgegradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;

abstract class PrepRepoTask extends DefaultTask {
    @OutputFile
    abstract RegularFileProperty getPomFile();

    @Inject
    public PrepRepoTask() {
    }

    @TaskAction
    public void prepRepo() throws IOException {
        var pomFile = getPomFile().get().getAsFile().toPath();
        Files.createDirectories(pomFile.getParent());
        if (!Files.exists(pomFile)) {
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
