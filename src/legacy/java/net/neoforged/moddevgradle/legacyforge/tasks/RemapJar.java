package net.neoforged.moddevgradle.legacyforge.tasks;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.IOException;

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
    }

    @TaskAction
    public void remap() throws IOException {
        getRemapOperation().execute(getExecOperations(), getInput().getAsFile().get(), getArchiveFile().get().getAsFile(), getLibraries());
    }
}
