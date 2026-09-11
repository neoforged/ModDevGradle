package net.neoforged.moddevgradle.tasks;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import net.neoforged.moddevgradle.internal.utils.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Writes stable, machine-readable metadata for launching a configured Minecraft run.
 */
public abstract class CreateLaunchMetadata extends DefaultTask {
    @Input
    public abstract Property<String> getWorkingDirectory();

    @Input
    public abstract Property<String> getJavaExecutable();

    @InputFile
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract RegularFileProperty getClasspathArgsFile();

    @InputFile
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract RegularFileProperty getVmArgsFile();

    @InputFile
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract RegularFileProperty getProgramArgsFile();

    @Input
    public abstract Property<String> getMainClass();

    @Input
    public abstract Property<String> getModFoldersArgument();

    @Input
    public abstract MapProperty<String, String> getEnvironment();

    @OutputFile
    public abstract RegularFileProperty getMetadataFile();

    @TaskAction
    public void createMetadata() throws IOException {
        var metadata = new TreeMap<String, String>();
        metadata.put("workingDirectory", getWorkingDirectory().get());
        metadata.put("javaExecutable", getJavaExecutable().get());
        metadata.put("classpathArgsFile", getClasspathArgsFile().get().getAsFile().getAbsolutePath());
        metadata.put("vmArgsFile", getVmArgsFile().get().getAsFile().getAbsolutePath());
        metadata.put("programArgsFile", getProgramArgsFile().get().getAsFile().getAbsolutePath());
        metadata.put("mainClass", getMainClass().get());
        metadata.put("modFolders", getModFoldersArgument().get());

        for (var entry : getEnvironment().get().entrySet()) {
            metadata.put("environment." + entry.getKey(), entry.getValue());
        }

        var lines = new ArrayList<String>(metadata.size());
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            lines.add(formatProperty(entry));
        }

        var destination = getMetadataFile().get().getAsFile().toPath();
        Files.createDirectories(destination.getParent());
        FileUtils.writeLinesSafe(destination, lines, StandardCharsets.ISO_8859_1);
    }

    private static String formatProperty(Map.Entry<String, String> entry) {
        var properties = new Properties();
        properties.setProperty(entry.getKey(), entry.getValue());

        var output = new ByteArrayOutputStream();
        try {
            properties.store(output, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return output.toString(StandardCharsets.ISO_8859_1)
                .lines()
                .filter(line -> !line.startsWith("#"))
                .findFirst()
                .orElseThrow();
    }
}
