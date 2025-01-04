package net.neoforged.moddevgradle.legacyforge.tasks;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.inject.Inject;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.process.ExecOperations;

/**
 * Task used to remap a jar using AutoRenamingTool.
 */
public abstract class RemapJar extends Jar {
    @Nested
    public abstract RemapOperation getRemapOperation();

    /**
     * The libraries to use for inheritance data during the renaming process.
     */
    @Optional
    @InputFiles
    public abstract ConfigurableFileCollection getLibraries();

    @InputFile
    public abstract RegularFileProperty getInput();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    public RemapJar() {
        getRemapOperation().getLogFile().set(new File(getTemporaryDir(), "console.log"));
    }

    @TaskAction
    @Override
    public void copy() {
        try {
            getRemapOperation().execute(getExecOperations(), getInput().getAsFile().get(), getArchiveFile().get().getAsFile(), getLibraries());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
