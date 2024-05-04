package net.neoforged.neoforgegradle;

import org.gradle.api.provider.Property;

public abstract class NeoForgeExtension {
    abstract Property<String> getVersion();

    abstract Property<String> getNeoFormVersion();
}
