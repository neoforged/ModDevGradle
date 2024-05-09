package net.neoforged.neoforgegradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.JarOutputStream;

abstract class CreateMinecraftArtifactsTask extends DefaultTask {
    private final ExecOperations execOperations;

    @Inject
    public CreateMinecraftArtifactsTask(ExecOperations execOperations) {
        this.execOperations = execOperations;
    }

    @Input
    abstract Property<String> getNeoForgeArtifact();

    @InputFile
    abstract RegularFileProperty getArtifactManifestFile();

    @Classpath
    @InputFiles
    abstract ConfigurableFileCollection getNeoFormInABox();

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

    @TaskAction
    public void createArtifacts() throws IOException {
        var artifactId = getNeoForgeArtifact().get();

        execOperations.javaexec(execSpec -> {
            execSpec.classpath(getNeoFormInABox());
            execSpec.args(
                    "run",
                    "--neoforge", artifactId + ":userdev",
                    "--artifact-manifest", getArtifactManifestFile().get().getAsFile().getAbsolutePath(),
                    "--dist", "joined",
                    "--write-result", "compiled:" + getCompiledArtifact().get().getAsFile().getAbsolutePath(),
                    "--write-result", "sources:" + getSourcesArtifact().get().getAsFile().getAbsolutePath(),
                    "--write-result", "clientResources:" + getResourcesArtifact().get().getAsFile().getAbsolutePath()
            );
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
