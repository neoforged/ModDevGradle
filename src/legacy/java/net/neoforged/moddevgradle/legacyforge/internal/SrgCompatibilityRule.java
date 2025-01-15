package net.neoforged.moddevgradle.legacyforge.internal;

import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.CompatibilityCheckDetails;

public class SrgCompatibilityRule implements AttributeCompatibilityRule<MinecraftMappings> {
    @Override
    public void execute(CompatibilityCheckDetails<MinecraftMappings> details) {
        details.compatible();
    }
}
