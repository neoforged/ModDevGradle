package net.neoforged.moddevgradle.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CreateLaunchMetadataTest {
    @TempDir
    Path tempDir;

    @Test
    void testWritesStableLoadableProperties() throws IOException {
        var task = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()
                .getTasks()
                .create("createLaunchMetadata", CreateLaunchMetadata.class);

        var classpathArgsFile = writeInputFile("classpath args.txt");
        var vmArgsFile = writeInputFile("vm args.txt");
        var programArgsFile = writeInputFile("program args.txt");
        var metadataFile = tempDir.resolve("launch metadata.properties");

        task.getWorkingDirectory().set(tempDir.resolve("game dir").toString());
        task.getJavaExecutable().set(tempDir.resolve("java/bin/java").toString());
        task.getClasspathArgsFile().fileValue(classpathArgsFile.toFile());
        task.getVmArgsFile().fileValue(vmArgsFile.toFile());
        task.getProgramArgsFile().fileValue(programArgsFile.toFile());
        task.getMainClass().set("com.example.Main");
        task.getModFoldersArgument().set("-Dfml.modFolders=example%%" + tempDir.resolve("mods dir"));
        task.getEnvironment().put("ALPHA", "one");
        task.getEnvironment().put("SPECIAL KEY", " leading space = value: unicode \u2603\nnext line");
        task.getMetadataFile().fileValue(metadataFile.toFile());

        task.createMetadata();

        var properties = new Properties();
        try (var input = Files.newInputStream(metadataFile)) {
            properties.load(input);
        }

        assertThat(properties)
                .containsEntry("workingDirectory", tempDir.resolve("game dir").toString())
                .containsEntry("javaExecutable", tempDir.resolve("java/bin/java").toString())
                .containsEntry("classpathArgsFile", classpathArgsFile.toAbsolutePath().toString())
                .containsEntry("vmArgsFile", vmArgsFile.toAbsolutePath().toString())
                .containsEntry("programArgsFile", programArgsFile.toAbsolutePath().toString())
                .containsEntry("mainClass", "com.example.Main")
                .containsEntry("modFolders", "-Dfml.modFolders=example%%" + tempDir.resolve("mods dir"))
                .containsEntry("environment.ALPHA", "one")
                .containsEntry("environment.SPECIAL KEY", " leading space = value: unicode \u2603\nnext line");

        assertThat(Files.readAllLines(metadataFile, StandardCharsets.ISO_8859_1))
                .extracting(CreateLaunchMetadataTest::propertyKey)
                .containsExactly(
                        "classpathArgsFile",
                        "environment.ALPHA",
                        "environment.SPECIAL\\ KEY",
                        "javaExecutable",
                        "mainClass",
                        "modFolders",
                        "programArgsFile",
                        "vmArgsFile",
                        "workingDirectory");
    }

    private Path writeInputFile(String name) throws IOException {
        var file = tempDir.resolve(name);
        Files.writeString(file, name);
        return file;
    }

    private static String propertyKey(String line) {
        return line.substring(0, line.indexOf('='));
    }
}
