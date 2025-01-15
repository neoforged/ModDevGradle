package net.neoforged.moddevgradle.legacyforge.internal;

import javax.inject.Inject;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataRule;

public class SrgMappingsRule implements ComponentMetadataRule {
    private final MinecraftMappings srgMappings;

    @Inject
    public SrgMappingsRule(MinecraftMappings srgMappings) {
        this.srgMappings = srgMappings;
    }

    @Override
    public void execute(ComponentMetadataContext context) {
        context.getDetails().allVariants(variant -> {
            if (variant.getAttributes().contains(MinecraftMappings.ATTRIBUTE)) {
                return;
            }

            variant.getAttributes().attribute(MinecraftMappings.ATTRIBUTE, srgMappings);
        });
    }
}
