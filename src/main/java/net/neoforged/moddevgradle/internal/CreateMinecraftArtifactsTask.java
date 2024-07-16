package net.neoforged.moddevgradle.internal;

import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

/**
 * The primary task for creating the Minecraft artifacts that mods will be compiled against,
 * using the NFRT CLI.
 */
@DisableCachingByDefault(because = "Implements its own caching")
abstract class CreateMinecraftArtifactsTask extends NeoFormRuntimeEngineTask {
    @Inject
    public CreateMinecraftArtifactsTask() {
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

    @TaskAction
    public void createArtifacts() {
        final ArrayList<String> args = new ArrayList<String>();
        args.add("run");

        for (final File accessTransformer : getAccessTransformers().getFiles()) {
            args.add("--access-transformer");
            args.add(accessTransformer.getAbsolutePath());
        }

        for (final File interfaceInjectionFile : getInterfaceInjectionData().getFiles()) {
            args.add("--interface-injection-data");
            args.add(interfaceInjectionFile.getAbsolutePath());
        }

        if (getValidateAccessTransformers().getOrElse(false)) {
            args.add("--validate-access-transformers");
        }

        if (getParchmentEnabled().get()) {
            final Set<File> parchmentData = getParchmentData().getFiles();
            if (parchmentData.size() == 1) {
                args.add("--parchment-data");
                args.add(parchmentData.iterator().next().getAbsolutePath());
            } else if (parchmentData.size() > 1) {
                throw new GradleException("More than one parchment data file was specified: " + parchmentData);
            }

            final String conflictResolutionPrefix = getParchmentConflictResolutionPrefix().getOrElse("");
            if (getParchmentConflictResolutionPrefix().isPresent() && !conflictResolutionPrefix.isBlank()) {
                args.add("--parchment-conflict-prefix");
                args.add(conflictResolutionPrefix);
            }
        }

        Collections.addAll(
                args,
                "--dist", "joined",
                "--write-result", "clientResources:" + getResourcesArtifact().get().getAsFile().getAbsolutePath()
        );

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
