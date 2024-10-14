package net.neoforged.moddevgradle.legacyforge.dsl;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.Nullable;

public abstract class LegacyForgeModdingSettings {
    @Nullable
    private String neoForgeVersion;

    private String forgeVersion;

    @Nullable
    private String mcpVersion;

    public @Nullable String getNeoForgeVersion() {
        return neoForgeVersion;
    }

    public @Nullable String getForgeVersion() {
        return forgeVersion;
    }

    public @Nullable String getMcpVersion() {
        return mcpVersion;
    }

    /**
     * NeoForge version number. You have to set either this, {@link #setForgeVersion} or {@link #setMcpVersion}.
     * Only NeoForge for Minecraft 1.20.1 is supported when using this plugin.
     */
    public void setNeoForgeVersion(String version) {
        this.neoForgeVersion = version;
    }

    /**
     * Minecraft Forge version. You have to set either this, {@link #setNeoForgeVersion} or {@link #setMcpVersion}.
     */
    public void setForgeVersion(String version) {
        this.forgeVersion = version;
    }

    /**
     * You can set this property to a version of <a href="https://maven.neoforged.net/#/releases/de/oceanlabs/mcp/mcp">MCP</a>
     * to either override the version used in the version of Forge you set, or to compile against
     * Vanilla artifacts that have no Forge code added.
     */
    public void setMcpVersion(String version) {
        this.mcpVersion = version;
    }

    /**
     * Contains the list of source sets for which access to Minecraft classes should be configured.
     * Defaults to the main source set, but can also be set to an empty list.
     */
    public abstract ListProperty<SourceSet> getEnabledSourceSets();
}
