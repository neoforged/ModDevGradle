package net.neoforged.moddevgradle.internal;

import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Use the NFRT CLI to download the asset index and assets for the Minecraft version used by the
 * underlying NeoForge/NeoForm configuration.
 */
abstract class DownloadAssetsTask extends NeoFormRuntimeTask {
    @Inject
    public DownloadAssetsTask() {
    }

    /**
     * Gradle dependency notation for the NeoForm data artifact, from which a Minecraft version will be derived.
     * <p>
     * To determine the minecraft version, the following properties will be checked in-order and the first one will be used:
     * <ol>
     *   <li>{@link #getMinecraftVersion()}</li>
     *   <li>{@link #getNeoFormArtifact()}</li>
     *   <li>this property</li>
     * </ol>
     */
    @Input
    @Optional
    abstract Property<String> getNeoForgeArtifact();

    /**
     * Gradle dependency notation for the NeoForm data artifact, from which a Minecraft version will be derived.
     * <p>
     * To determine the minecraft version, the following properties will be checked in-order and the first one will be used:
     * <ol>
     *   <li>{@link #getMinecraftVersion()}</li>
     *   <li>this property</li>
     *   <li>{@link #getNeoForgeArtifact()}</li>
     * </ol>
     */
    @Input
    @Optional
    abstract Property<String> getNeoFormArtifact();

    /**
     * The Minecraft version to download the assets for.
     * <p>
     * To determine the minecraft version, the following properties will be checked in-order and the first one will be used:
     * <ol>
     *   <li>this property</li>
     *   <li>{@link #getNeoFormArtifact()}</li>
     *   <li>{@link #getNeoForgeArtifact()}</li>
     * </ol>
     */
    @Input
    @Optional
    abstract Property<String> getMinecraftVersion();

    /**
     * A properties file will be written to this location which can be read by the runtime tasks
     * to determine where the asset index and asset root are located.
     */
    @OutputFile
    abstract RegularFileProperty getAssetPropertiesFile();

    @TaskAction
    public void createArtifacts() {

        var args = new ArrayList<String>();
        Collections.addAll(args,
                "download-assets",
                "--write-properties", getAssetPropertiesFile().get().getAsFile().getAbsolutePath()
        );

        // Only one should be specified, we try to use the best one.
        if (getMinecraftVersion().isPresent()) {
            Collections.addAll(args, "--minecraft-version", getMinecraftVersion().get());
        } else if (getNeoFormArtifact().isPresent()) {
            Collections.addAll(args, "--neoform", getNeoFormArtifact().get());
        } else if (getNeoForgeArtifact().isPresent()) {
            Collections.addAll(args, "--neoforge", getNeoForgeArtifact().get());
        } else {
            throw new GradleException("One of minecraftVersion, neoFormArtifact or neoForgeArtifact must be specified to download assets.");
        }

        run(args);
    }
}
