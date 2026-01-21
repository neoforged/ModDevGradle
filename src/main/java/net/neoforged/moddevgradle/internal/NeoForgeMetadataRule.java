package net.neoforged.moddevgradle.internal;

import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataRule;

public class NeoForgeMetadataRule implements ComponentMetadataRule {
    @Override
    public void execute(ComponentMetadataContext context) {
        // TODO: maybe use maybeAddVariant?
        context.getDetails().addVariant(
                "modDevApiElements_neoFormOnly",
                "modDevApiElements",
                variantMetadata -> {
                    variantMetadata.withDependencies(depsMetadata -> {
                        // Only the NeoForm dependency is endorsing strict versions.
                        depsMetadata.removeIf(depMetadata -> !depMetadata.isEndorsingStrictVersions());
                    });
                    variantMetadata.withCapabilities(capsMetadata -> {
                        var caps = capsMetadata.getCapabilities();
                        if (caps.size() != 1) {
                            throw new RuntimeException("Could not adapt NeoForge metadata: expected exactly one capability for modDevApiElements, found " + caps.size());
                        }
                        var cap = caps.get(0);
                        if (!cap.getGroup().equals("net.neoforged") || !cap.getName().equals("neoforge-dependencies")) {
                            throw new RuntimeException("Could not adapt NeoForge metadata: expected capability net.neoforged:neoforge-dependencies for modDevApiElements, found " + cap.getGroup() + ":" + cap.getName());
                        }
                        capsMetadata.removeCapability("net.neoforged", "neoforge-dependencies");
                        capsMetadata.addCapability("net.neoforged", "neoforge-dependencies_neoFormOnly", cap.getVersion());
                    });
                }
        );
    }
}
