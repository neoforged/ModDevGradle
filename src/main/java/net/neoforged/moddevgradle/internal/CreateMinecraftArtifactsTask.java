package net.neoforged.moddevgradle.internal;

import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

@DisableCachingByDefault(because = "Implements its own caching")
abstract class CreateMinecraftArtifactsTask extends NeoFormRuntimeTask {
    @Inject
    public CreateMinecraftArtifactsTask() {
        // When cache is disabled, the task is NEVER up-to-date to aid with debugging problems
        getOutputs().upToDateWhen(task -> ((CreateMinecraftArtifactsTask) task).getEnableCache().get());
    }

    @InputFile
    @Optional
    abstract RegularFileProperty getArtifactManifestFile();

    /**
     * Enable verbose NeoForm-in-a-box output
     */
    @Internal
    abstract Property<Boolean> getVerbose();

    @Input
    abstract Property<String> getNeoForgeArtifact();

    @InputFiles
    abstract ConfigurableFileCollection getAccessTransformers();

    @InputFiles
    abstract ConfigurableFileCollection getParchmentData();

    @OutputFile
    abstract RegularFileProperty getCompiledWithSourcesArtifact();

    @OutputFile
    abstract RegularFileProperty getCompiledArtifact();

    @OutputFile
    abstract RegularFileProperty getSourcesArtifact();

    /**
     * Also known as "client-extra". Contains the non-class files from the original Minecraft jar (excluding META-INF)
     */
    @OutputFile
    abstract RegularFileProperty getResourcesArtifact();

    /**
     * Enables use of the cache.
     */
    @Internal
    abstract Property<Boolean> getEnableCache();

    @Input
    abstract Property<Boolean> getUseEclipseCompiler();

    @Input
    abstract Property<Boolean> getAnalyzeCacheMisses();

    @TaskAction
    public void createArtifacts() throws IOException {
        var artifactId = getNeoForgeArtifact().get();

        var args = new ArrayList<String>();
        Collections.addAll(
                args,
                "run"
        );

        if (getVerbose().get()) {
            args.add("--verbose");
        }

        if (!getEnableCache().get()) {
            args.add("--disable-cache");
        }

        var accessTransformers = getAccessTransformers().getFiles();
        for (var accessTransformer : accessTransformers) {
            args.add("--access-transformer");
            args.add(accessTransformer.getAbsolutePath());
        }

        var parchmentData = getParchmentData().getFiles();
        if (parchmentData.size() == 1) {
            args.add("--parchment-data");
            args.add(parchmentData.iterator().next().getAbsolutePath());
        } else if (parchmentData.size() > 1) {
            throw new GradleException("More than one parchment data file were specified: " + parchmentData);
        }

        if (getUseEclipseCompiler().get()) {
            args.add("--use-eclipse-compiler");
        }

        if (getAnalyzeCacheMisses().get()) {
            args.add("--analyze-cache-misses");
        }

        if (getArtifactManifestFile().isPresent()) {
            args.add("--artifact-manifest");
            args.add(getArtifactManifestFile().get().getAsFile().getAbsolutePath());
            args.add("--warn-on-artifact-manifest-miss");
        }

        // NOTE: When we use NeoForm standalone, the result-ids also change
        Collections.addAll(
                args,
                "--neoforge", artifactId + ":userdev",
                "--dist", "joined",
                "--write-result", "compiledWithNeoForge:" + getCompiledArtifact().get().getAsFile().getAbsolutePath(),
                "--write-result", "sourcesWithNeoForge:" + getSourcesArtifact().get().getAsFile().getAbsolutePath(),
                "--write-result", "clientResources:" + getResourcesArtifact().get().getAsFile().getAbsolutePath(),
                "--write-result", "sourcesAndCompiledWithNeoForge:" + getCompiledWithSourcesArtifact().get().getAsFile().getAbsolutePath()
        );

        run(args);
    }
}
