package net.neoforged.moddevgradle.internal;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * This task creates a properties file for NFRT that maps artifact ids found in
 * NeoForge/NeoForm configuration data to existing files on disk.
 */
abstract class CreateArtifactManifestTask extends DefaultTask {
    @Input
    abstract SetProperty<ArtifactManifestEntry> getNeoForgeModDevArtifacts();

    @OutputFile
    abstract RegularFileProperty getManifestFile();

    @Inject
    public CreateArtifactManifestTask() {
    }

    @TaskAction
    public void writeManifest() throws IOException {
        var artifactsManifest = new Properties();

        for (var artifact : getNeoForgeModDevArtifacts().get()) {
            artifactsManifest.setProperty(artifact.artifactId(), artifact.file().getAbsolutePath());
        }

        try (var out = new FileOutputStream(getManifestFile().get().getAsFile())) {
            artifactsManifest.store(out, "");
        }
    }
}
