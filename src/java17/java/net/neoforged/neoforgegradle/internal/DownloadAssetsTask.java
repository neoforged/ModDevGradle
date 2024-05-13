package net.neoforged.neoforgegradle.internal;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.util.List;

abstract class DownloadAssetsTask extends NeoFormTask {
    @Inject
    public DownloadAssetsTask() {
    }

    @Input
    abstract Property<String> getNeoForgeArtifact();

    @OutputFile
    abstract RegularFileProperty getAssetPropertiesFile();

    @TaskAction
    public void createArtifacts() {
        var artifactId = getNeoForgeArtifact().get();

        run(List.of(
                "download-assets",
                "--neoforge", artifactId + ":userdev",
                "--output-properties-to", getAssetPropertiesFile().get().getAsFile().getAbsolutePath()
        ));
    }
}
