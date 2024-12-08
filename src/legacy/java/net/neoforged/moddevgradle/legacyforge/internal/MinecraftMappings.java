package net.neoforged.moddevgradle.legacyforge.internal;

import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;

import java.util.Locale;

public enum MinecraftMappings implements Named {
    NAMED,
    SRG;

    public static final Attribute<MinecraftMappings> ATTRIBUTE = Attribute.of("net.neoforged.moddevgradle.legacy.minecraft_mappings", MinecraftMappings.class);

    @Override
    public String getName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
