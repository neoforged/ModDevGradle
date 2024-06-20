package net.neoforged.moddevgradle.internal;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.util.List;

/**
 * Use the NFRT CLI to download the asset index and assets for the Minecraft version used by the
 * underlying NeoForge/NeoForm configuration.
 */
abstract class DownloadAssetsTask extends NeoFormRuntimeEngineTask {
    @Inject
    public DownloadAssetsTask() {
    }

    @OutputFile
    abstract RegularFileProperty getAssetPropertiesFile();

    @TaskAction
    public void createArtifacts() {
        run(List.of(
                "download-assets",
                "--output-properties-to", getAssetPropertiesFile().get().getAsFile().getAbsolutePath()
        ));
    }
}
