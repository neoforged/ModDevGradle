package net.neoforged.moddevgradle.tasks;

import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

/**
 * The primary task for creating the Minecraft artifacts that mods will be compiled against,
 * using the NFRT CLI.
 */
@DisableCachingByDefault(because = "Implements its own caching")
abstract class CreateMinecraftArtifactsTask extends NeoFormRuntimeTask {
    @Inject
    public CreateMinecraftArtifactsTask() {
        // When cache is disabled, the task is NEVER up-to-date to aid with debugging problems
        getOutputs().upToDateWhen(task -> ((CreateMinecraftArtifactsTask) task).getEnableCache().get());
        getEnableCache().convention(false);
        getUseEclipseCompiler().convention(false);
        getAnalyzeCacheMisses().convention(false);
    }

    @InputFiles
    abstract ConfigurableFileCollection getAccessTransformers();

    @InputFiles
    abstract ConfigurableFileCollection getInterfaceInjectionData();

    @Input
    @Optional
    abstract Property<Boolean> getValidateAccessTransformers();

    @Input
    abstract Property<Boolean> getParchmentEnabled();

    @InputFiles
    abstract ConfigurableFileCollection getParchmentData();

    @Input
    abstract Property<String> getParchmentConflictResolutionPrefix();

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

    @OutputFiles
    abstract MapProperty<String, File> getAdditionalResults();

    /**
     * Gradle dependency notation for the NeoForge userdev artifact.
     * Either this or {@link #getNeoFormArtifact()} must be specified.
     */
    @Input
    @Optional
    abstract Property<String> getNeoForgeArtifact();

    /**
     * Gradle dependency notation for the NeoForm data artifact.
     * Either this or {@link #getNeoForgeArtifact()} must be specified.
     */
    @Input
    @Optional
    abstract Property<String> getNeoFormArtifact();

    /**
     * Enables use of the cache.
     */
    @Internal
    abstract Property<Boolean> getEnableCache();

    @Internal
    abstract Property<Boolean> getAnalyzeCacheMisses();

    @Input
    abstract Property<Boolean> getUseEclipseCompiler();

    @TaskAction
    public void createArtifacts() {
        var args = new ArrayList<String>();
        args.add("run");

        for (var accessTransformer : getAccessTransformers().getFiles()) {
            args.add("--access-transformer");
            args.add(accessTransformer.getAbsolutePath());
        }

        for (var interfaceInjectionFile : getInterfaceInjectionData().getFiles()) {
            args.add("--interface-injection-data");
            args.add(interfaceInjectionFile.getAbsolutePath());
        }

        if (getValidateAccessTransformers().getOrElse(false)) {
            args.add("--validate-access-transformers");
        }

        if (getParchmentEnabled().get()) {
            var parchmentData = getParchmentData().getFiles();
            if (parchmentData.size() == 1) {
                args.add("--parchment-data");
                args.add(parchmentData.iterator().next().getAbsolutePath());
            } else if (parchmentData.size() > 1) {
                throw new GradleException("More than one parchment data file was specified: " + parchmentData);
            }

            var conflictResolutionPrefix = getParchmentConflictResolutionPrefix().getOrElse("");
            if (getParchmentConflictResolutionPrefix().isPresent() && !conflictResolutionPrefix.isBlank()) {
                args.add("--parchment-conflict-prefix");
                args.add(conflictResolutionPrefix);
            }
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
            Collections.addAll(args, "--neoforge", getNeoForgeArtifact().get());
        }
        if (getNeoFormArtifact().isPresent()) {
            Collections.addAll(args, "--neoform", getNeoFormArtifact().get());
        }
        if (!getNeoFormArtifact().isPresent() && !getNeoForgeArtifact().isPresent()) {
            throw new GradleException("You need to specify at least 'version' or 'neoFormVersion' in the 'neoForge' block of your build script.");
        }

        Collections.addAll(
                args,
                "--dist", "joined",
                "--write-result", "clientResources:" + getResourcesArtifact().get().getAsFile().getAbsolutePath()
        );

        getAdditionalResults().get().forEach((name, file) ->
                Collections.addAll(args, "--write-result", name + ":" + file.getAbsolutePath()));

        // NOTE: When we use NeoForm standalone, the result-ids also change
        if (getNeoForgeArtifact().isPresent()) {
            Collections.addAll(
                    args,
                    "--write-result", "compiledWithNeoForge:" + getCompiledArtifact().get().getAsFile().getAbsolutePath(),
                    "--write-result", "sourcesWithNeoForge:" + getSourcesArtifact().get().getAsFile().getAbsolutePath(),
                    "--write-result", "sourcesAndCompiledWithNeoForge:" + getCompiledWithSourcesArtifact().get().getAsFile().getAbsolutePath()
            );
        } else {
            Collections.addAll(
                    args,
                    "--write-result", "compiled:" + getCompiledArtifact().get().getAsFile().getAbsolutePath(),
                    "--write-result", "sources:" + getSourcesArtifact().get().getAsFile().getAbsolutePath(),
                    "--write-result", "sourcesAndCompiled:" + getCompiledWithSourcesArtifact().get().getAsFile().getAbsolutePath()
            );
        }

        run(args);
    }
}
