package net.neoforged.moddevgradle.internal;

import org.jetbrains.annotations.ApiStatus;

@FunctionalInterface
@ApiStatus.Internal
public interface ArtifactNamingStrategy {
    static ArtifactNamingStrategy createDefault(String artifactFilenamePrefix) {
        return (artifact) -> {
            // It's helpful to be able to differentiate the Vanilla jar and the NeoForge jar in classic multiloader setups.
            return artifactFilenamePrefix + artifact.defaultSuffix + ".jar";
        };
    }

    String getFilename(WorkflowArtifact artifact);
}
