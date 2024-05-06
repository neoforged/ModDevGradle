package net.neoforged.neoforgegradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.TreeSet;

abstract class WriteLegacyClasspath extends DefaultTask {
    @Inject
    public WriteLegacyClasspath() {}

    @InputFiles
    abstract ConfigurableFileCollection getEntries();

    @OutputFile
    abstract RegularFileProperty getLegacyClasspathFile();

    @TaskAction
    public void writeLegacyClasspath() throws IOException {
        var legacyClasspath = new StringBuilder();
        // Copy the entries to a tree set to ensure deterministic order if we have to debug classpath problems...
        for (var entry : new TreeSet<>(getEntries().getFiles())) {
            legacyClasspath.append(entry.getAbsolutePath()).append(System.lineSeparator());
        }

        Files.writeString(getLegacyClasspathFile().getAsFile().get().toPath(), legacyClasspath.toString(), StandardOpenOption.CREATE);
    }
}
