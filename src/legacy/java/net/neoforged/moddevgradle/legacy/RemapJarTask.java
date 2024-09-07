package net.neoforged.moddevgradle.legacy;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.IOException;

/**
 * Task used to remap a jar using AutoRenamingTool.
 */
public abstract class RemapJarTask extends DefaultTask {

    @Nested
    protected abstract RemapParameters getParameters();

    @Inject
    public RemapJarTask() {
        super();
        getArchiveFile().convention(getProject().provider(() -> {
            var path = getArchiveBaseName().get() + getArchiveVersion().filter(s -> !s.isBlank()).map(v -> "-" + v).getOrElse("") + getArchiveClassifier()
                    .filter(s -> !s.isBlank()).map(c -> "-" + c).getOrElse("") + ".jar";
            return getDestinationDirectory().file(path);
        }).flatMap(regularFileProvider -> regularFileProvider));
    }

    /**
     * The libraries to use for inheritance data during the renaming process.
     */
    @Optional
    @InputFiles
    public abstract ConfigurableFileCollection getLibraries();

    @InputFile
    public abstract RegularFileProperty getInput();

    @Internal
    public abstract Property<String> getArchiveBaseName();

    @Internal
    public abstract Property<String> getArchiveVersion();

    @Internal
    public abstract Property<String> getArchiveClassifier();

    @Internal
    public abstract DirectoryProperty getDestinationDirectory();

    @OutputFile
    public abstract RegularFileProperty getArchiveFile();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @TaskAction
    public void remap() throws IOException {
        getParameters().execute(getExecOperations(), getInput().getAsFile().get(), getArchiveFile().getAsFile().get(), getLibraries());
    }
}
