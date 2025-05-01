package net.neoforged.moddevgradle.legacyforge.internal;

import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface MinecraftMappings extends Named {
    String NAMED = "named";
    String SRG = "srg";
    String UNKNOWN = "unknown";

    // TODO: not v2 :P
    Attribute<MinecraftMappings> ATTRIBUTE = Attribute.of("net.neoforged.moddevgradle.legacy.minecraft_mappings.v2", MinecraftMappings.class);
}
