package net.neoforged.moddevgradle.legacyforge.internal;

import java.util.Locale;
import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public enum MinecraftMappings implements Named {
    NAMED,
    SRG;

    public static final Attribute<MinecraftMappings> ATTRIBUTE = Attribute.of("net.neoforged.moddevgradle.legacy.minecraft_mappings", MinecraftMappings.class);

    @Override
    public String getName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
