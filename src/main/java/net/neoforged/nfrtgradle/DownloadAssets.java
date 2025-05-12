package net.neoforged.nfrtgradle;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.ApiStatus;

/**
 * Use the NFRT CLI to download the asset index and assets for the Minecraft version used by the
 * underlying NeoForge/NeoForm configuration.
 */
@DisableCachingByDefault(because = "Implements its own caching and the output file is system specific")
@ApiStatus.NonExtendable
public abstract class DownloadAssets extends NeoFormRuntimeTask {
    @Inject
    public DownloadAssets() {
        getOutputs().upToDateWhen(DownloadAssets::checkAssetValidity);
    }

    /**
     * Gradle dependency notation for the NeoForm data artifact, from which a Minecraft version will be derived.
     * <p>
     * To determine the Minecraft version, the following properties will be checked in-order and the first one will be used:
     * <ol>
     * <li>{@link #getMinecraftVersion()}</li>
     * <li>{@link #getNeoFormArtifact()}</li>
     * <li>this property</li>
     * </ol>
     */
    @Input
    @Optional
    public abstract Property<String> getNeoForgeArtifact();

    /**
     * Gradle dependency notation for the NeoForm data artifact, from which a Minecraft version will be derived.
     * <p>
     * To determine the Minecraft version, the following properties will be checked in-order and the first one will be used:
     * <ol>
     * <li>{@link #getMinecraftVersion()}</li>
     * <li>this property</li>
     * <li>{@link #getNeoForgeArtifact()}</li>
     * </ol>
     */
    @Input
    @Optional
    public abstract Property<String> getNeoFormArtifact();

    /**
     * The Minecraft version to download the assets for.
     * <p>
     * To determine the Minecraft version, the following properties will be checked in-order and the first one will be used:
     * <ol>
     * <li>this property</li>
     * <li>{@link #getNeoFormArtifact()}</li>
     * <li>{@link #getNeoForgeArtifact()}</li>
     * </ol>
     */
    @Input
    @Optional
    public abstract Property<String> getMinecraftVersion();

    /**
     * A properties file will be written to this location which can be read by the runtime tasks
     * to determine where the asset index and asset root are located.
     * <p>
     * See {@code --write-properties} parameter of the {@code download-asset} command in NFRT.
     */
    @OutputFile
    @Optional
    public abstract RegularFileProperty getAssetPropertiesFile();

    /**
     * A properties file will be written to this location which can be read by the runtime tasks
     * to determine where the asset index and asset root are located.
     * <p>
     * See {@code --write-json} parameter of the {@code download-asset} command in NFRT.
     */
    @OutputFile
    @Optional
    public abstract RegularFileProperty getAssetJsonFile();

    @TaskAction
    public void downloadAssets() {
        var args = new ArrayList<String>();
        Collections.addAll(args, "download-assets");
        if (getAssetPropertiesFile().isPresent()) {
            Collections.addAll(args, "--write-properties", getAssetPropertiesFile().get().getAsFile().getAbsolutePath());
        }
        if (getAssetJsonFile().isPresent()) {
            Collections.addAll(args, "--write-json", getAssetJsonFile().get().getAsFile().getAbsolutePath());
        }

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

    private static boolean checkAssetValidity(Task task) {
        var downloadTask = (DownloadAssets) task;
        var logger = downloadTask.getLogger();

        var assetReferenceFile = downloadTask.getAssetPropertiesFile();
        if (assetReferenceFile.isPresent()) {
            var file = assetReferenceFile.getAsFile().get();
            if (!file.exists()) {
                logger.info("Asset reference file {} does not exist.", file);
                return false;
            }
            DownloadedAssetsReference assetReference;
            try {
                assetReference = DownloadedAssetsReference.loadProperties(file);
            } catch (Exception e) {
                logger.error("Failed to read downloaded asset index: {}", file, e);
                return false;
            }
            return validateAssetReference(assetReference, logger);
        }

        return true;
    }

    private static boolean validateAssetReference(DownloadedAssetsReference assetReference, Logger logger) {
        File assetRoot = new File(assetReference.assetsRoot());
        if (!assetRoot.isDirectory()) {
            logger.info("Downloaded assets directory has gone missing: {}", assetRoot);
            return false;
        }
        File assetIndex = new File(assetRoot, "indexes/" + assetReference.assetIndex() + ".json");
        if (!assetIndex.isFile()) {
            logger.info("Downloaded assets index has gone missing: {}", assetIndex);
            return false;
        }
        return true;
    }
}
