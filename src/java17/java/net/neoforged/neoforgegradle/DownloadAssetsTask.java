package net.neoforged.neoforgegradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;

abstract class DownloadAssetsTask extends DefaultTask {
    private final ExecOperations execOperations;

    @Inject
    public DownloadAssetsTask(ExecOperations execOperations) {
        this.execOperations = execOperations;
    }

    @Input
    abstract Property<String> getNeoForgeArtifact();

    @Classpath
    @InputFiles
    abstract ConfigurableFileCollection getNeoFormInABox();

    @OutputFile
    abstract RegularFileProperty getAssetPropertiesFile();

    @TaskAction
    public void createArtifacts() {
        var artifactId = getNeoForgeArtifact().get();

        execOperations.javaexec(execSpec -> {
            execSpec.classpath(getNeoFormInABox());
            execSpec.getMainClass().set("net.neoforged.neoforminabox.cli.Main");
            execSpec.args(
                    "download-assets",
                    "--neoforge", artifactId + ":userdev",
                    "--output-properties-to", getAssetPropertiesFile().get().getAsFile().getAbsolutePath()
            );
        });
    }
}
