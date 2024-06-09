package net.neoforged.moddevgradle.internal;

import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Extends the basic taks for running NFRT with the properties that apply to all subcommands
 * working with NeoForge or NeoForm data.
 */
abstract public class NeoFormRuntimeEngineTask extends NeoFormRuntimeTask {
    public NeoFormRuntimeEngineTask() {
        // When cache is disabled, the task is NEVER up-to-date to aid with debugging problems
        getOutputs().upToDateWhen(task -> ((NeoFormRuntimeEngineTask) task).getEnableCache().get());
        getEnableCache().convention(false);
        getUseEclipseCompiler().convention(false);
        getAnalyzeCacheMisses().convention(false);
        getVerbose().convention(false);
    }

    /**
     * Points to the NeoForge Userdev artifact.
     * Either this or {@link #getNeoFormArtifact()} must be specified.
     */
    @Input
    @Optional
    abstract Property<String> getNeoForgeArtifact();

    /**
     * Points to the NeoForm Config artifact.
     * Either this or {@link #getNeoForgeArtifact()} must be specified.
     */
    @Input
    @Optional
    abstract Property<String> getNeoFormArtifact();

    /**
     * Enable verbose output for the NFRT engine.
     */
    @Internal
    abstract Property<Boolean> getVerbose();

    @InputFile
    @Optional
    abstract RegularFileProperty getArtifactManifestFile();

    /**
     * Enables use of the cache.
     */
    @Internal
    abstract Property<Boolean> getEnableCache();

    @Internal
    abstract Property<Boolean> getAnalyzeCacheMisses();

    @Input
    abstract Property<Boolean> getUseEclipseCompiler();

    @Override
    protected void run(List<String> args) {
        args = new ArrayList<>(args);

        if (getVerbose().get()) {
            args.add("--verbose");
        }

        if (getArtifactManifestFile().isPresent()) {
            args.add("--artifact-manifest");
            args.add(getArtifactManifestFile().get().getAsFile().getAbsolutePath());
            args.add("--warn-on-artifact-manifest-miss");
        }

        if (!getEnableCache().get()) {
            args.add("--disable-cache");
        }

        if (getAnalyzeCacheMisses().get()) {
            args.add("--analyze-cache-misses");
        }

        if (getUseEclipseCompiler().get()) {
            args.add("--use-eclipse-compiler");
        }

        // Note that it is possible to specify both
        if (getNeoForgeArtifact().isPresent()) {
            Collections.addAll(args, "--neoforge", getNeoForgeArtifact().get() + ":userdev");
        }
        if (getNeoFormArtifact().isPresent()) {
            Collections.addAll(args, "--neoform", getNeoFormArtifact().get());
        }
        if (!getNeoFormArtifact().isPresent() && !getNeoForgeArtifact().isPresent()) {
            throw new GradleException("You need to specify at least 'version' or 'neoFormVersion' in the 'neoForge' block of your build script.");
        }

        super.run(args);
    }
}
