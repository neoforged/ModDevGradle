package net.neoforged.moddevgradle.internal;

import net.neoforged.moddevgradle.internal.utils.VersionCapabilitiesInternal;
import org.jetbrains.annotations.ApiStatus;

@FunctionalInterface
@ApiStatus.Internal
public interface ArtifactNamingStrategy {
    static ArtifactNamingStrategy createVanilla(String version) {
        return (artifact) -> {
            return "vanilla-%s%s.jar".formatted(version, artifact.defaultSuffix);
        };
    }

    static ArtifactNamingStrategy createNeoForge(VersionCapabilitiesInternal versionCapabilities, String loader, String version) {
        return (artifact) -> {
            if (artifact != WorkflowArtifact.CLIENT_RESOURCES || versionCapabilities.modLocatorRework()) {
                return "%s-%s%s.jar".formatted(loader, version, artifact.defaultSuffix);
            } else {
                // We have to ensure that client resources are named "client-extra" and *do not* contain forge-<version>
                // otherwise FML might pick up the client resources as the main Minecraft jar.
                return "client-extra-" + version + ".jar";
            }
        };
    }

    String getFilename(WorkflowArtifact artifact);
}
