package net.neoforged.moddevgradle.internal;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.TreeSet;
import javax.inject.Inject;
import net.neoforged.moddevgradle.internal.utils.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

abstract class WriteLegacyClasspath extends DefaultTask {
    @Inject
    public WriteLegacyClasspath() {}

    @Input
    abstract ListProperty<String> getEntries();

    void addEntries(Object... filesNotation) {
        var files = getProject().files(filesNotation);
        getEntries().addAll(getProject().provider(() -> {
            // Use a provider indirection to remove task dependencies.
            // Use file names only.
            return files.getFiles().stream()
                    .map(File::getAbsolutePath)
                    .toList();
        }));
    }

    @OutputFile
    abstract RegularFileProperty getLegacyClasspathFile();

    @TaskAction
    public void writeLegacyClasspath() throws IOException {
        var legacyClasspath = new StringBuilder();
        // Copy the entries to a tree set to ensure deterministic order if we have to debug classpath problems...
        for (var entry : new TreeSet<>(getEntries().get())) {
            legacyClasspath.append(entry).append(System.lineSeparator());
        }

        var destination = getLegacyClasspathFile().getAsFile().get().toPath();
        // BootStrapLauncher reads this file using UTF-8
        FileUtils.writeStringSafe(destination, legacyClasspath.toString(), StandardCharsets.UTF_8);
    }
}
