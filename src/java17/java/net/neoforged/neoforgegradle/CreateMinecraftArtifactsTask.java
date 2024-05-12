package net.neoforged.neoforgegradle;

import org.gradle.api.DefaultTask;
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
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

abstract class CreateMinecraftArtifactsTask extends DefaultTask {
    private final ExecOperations execOperations;
    private final JavaToolchainService javaToolchainService;

    @Inject
    public CreateMinecraftArtifactsTask(ExecOperations execOperations, JavaToolchainService javaToolchainService) {
        this.execOperations = execOperations;
        this.javaToolchainService = javaToolchainService;
    }

    @Input
    abstract Property<String> getNeoForgeArtifact();

    @InputFile
    abstract RegularFileProperty getArtifactManifestFile();

    @Classpath
    @InputFiles
    abstract ConfigurableFileCollection getCompileClasspath();

    @Classpath
    @InputFiles
    abstract ConfigurableFileCollection getNeoFormInABox();

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
     * Enable verbose NeoForm-in-a-box output
     */
    @Internal
    abstract Property<Boolean> getVerbose();

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

        var launcher = javaToolchainService.launcherFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(21)));

        execOperations.javaexec(execSpec -> {
            // See https://github.com/gradle/gradle/issues/28959
            execSpec.jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8");
            execSpec.executable(launcher.get().getExecutablePath().getAsFile());
            execSpec.classpath(getNeoFormInABox());
            execSpec.args(args);
        });

        if (getDummyArtifact().isPresent()) {
            var dummyFile = getDummyArtifact().getAsFile().get();
            dummyFile.delete();
            try (var output = new FileOutputStream(dummyFile);
                 var jarOut = new JarOutputStream(output)) {
            }
        }
    }
}
