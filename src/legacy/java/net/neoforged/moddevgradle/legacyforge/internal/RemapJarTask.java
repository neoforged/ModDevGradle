package net.neoforged.moddevgradle.legacyforge.internal;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.tasks.Jar;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.IOException;

/**
 * Task used to remap a jar using AutoRenamingTool.
 */
abstract class RemapJarTask extends Jar {

    @Nested
    protected abstract RemapParameters getParameters();

    @Inject
    public RemapJarTask() {
        super();
    }

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

    @TaskAction
    public void remap() throws IOException {
        getParameters().execute(getExecOperations(), getInput().getAsFile().get(), getArchiveFile().get().getAsFile(), getLibraries());
    }
}
