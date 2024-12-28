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
}
