package net.neoforged.moddevgradle.dsl;

/**
 * Describes the capabilities of the currently chosen version of Minecraft.
 */
public interface VersionCapabilities {
    /**
     * The Java version used by this version of Minecraft.
     */
    int javaVersion();

    /**
     * Whether this version of Minecraft uses separate data-generation runs for client and server data.
     */
    boolean splitDataRuns();

    /**
     * Whether the NeoForge version for this version of Minecraft supports mod-loading in unit tests.
     */
    boolean testFixtures();

    /**
     * Whether the NeoForge version requires the use of the {@code additionalRuntimeClasspath} configuration to
     * add libraries that don't declare a {@code FMLModType} in their manifest to the runtime classpath.
     */
    boolean legacyClasspath();
}
