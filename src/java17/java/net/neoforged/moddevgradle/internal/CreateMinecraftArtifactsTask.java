package net.neoforged.moddevgradle.internal;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

abstract class CreateMinecraftArtifactsTask extends NeoFormRuntimeTask {
    @Inject
    public CreateMinecraftArtifactsTask() {
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

    @Classpath
    @InputFiles
    abstract ConfigurableFileCollection getCompileClasspath();

    @InputFiles
    abstract ConfigurableFileCollection getAccessTransformers();

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
     * Dummy file used to cause a dependency of configuration -> task.
     */
    @OutputFile
    @Optional
    abstract RegularFileProperty getDummyArtifact();

    /**
     * Enables use of the cache.
     */
    @Internal
    abstract Property<Boolean> getEnableCache();

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

        var compileClasspath = getCompileClasspath().getFiles();
        if (!compileClasspath.isEmpty()) {
            args.add("--compile-classpath");
            args.add(compileClasspath.stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator)));
        }

        var accessTransformers = getAccessTransformers().getFiles();
        for (var accessTransformer : accessTransformers) {
            args.add("--access-transformer");
            args.add(accessTransformer.getAbsolutePath());
        }

        Collections.addAll(
                args,
                "--neoforge", artifactId + ":userdev",
                "--artifact-manifest", getArtifactManifestFile().get().getAsFile().getAbsolutePath(),
                "--dist", "joined",
                "--write-result", "compiled:" + getCompiledArtifact().get().getAsFile().getAbsolutePath(),
                "--write-result", "sources:" + getSourcesArtifact().get().getAsFile().getAbsolutePath(),
                "--write-result", "clientResources:" + getResourcesArtifact().get().getAsFile().getAbsolutePath()
        );

        run(args);

        if (getDummyArtifact().isPresent()) {
            var dummyFile = getDummyArtifact().getAsFile().get();
            dummyFile.delete();
            try (var output = new FileOutputStream(dummyFile)) {
                new JarOutputStream(output).close();
            }
        }
    }
}
