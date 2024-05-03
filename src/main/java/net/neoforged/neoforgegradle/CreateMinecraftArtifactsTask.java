package net.neoforged.neoforgegradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

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

    @InputFile
    abstract RegularFileProperty getNeoFormInABox();

    @OutputFile
    abstract RegularFileProperty getCompiledArtifact();

    @OutputFile
    abstract RegularFileProperty getSourcesArtifact();

    @TaskAction
    public void createArtifacts() {
        var artifactId = getNeoForgeArtifact().get();

        execOperations.javaexec(execSpec -> {
            // Executable jars can have only _one_ jar on the classpath.
            execSpec.classpath(getNeoFormInABox().getAsFile());
            execSpec.args(
                    "--neoforge", artifactId + ":userdev",
                    "--artifact-manifest", getArtifactManifestFile().get().getAsFile().getAbsolutePath(),
                    "--dist", "joined",
                    "--write-result", "compiled:" + getCompiledArtifact().get().getAsFile().getAbsolutePath(),
                    "--write-result", "sources:" + getSourcesArtifact().get().getAsFile().getAbsolutePath()
            );
        });
    }
}
