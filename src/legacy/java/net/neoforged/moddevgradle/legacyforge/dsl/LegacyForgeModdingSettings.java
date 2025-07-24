package net.neoforged.moddevgradle.legacyforge.dsl;

import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.Nullable;

public abstract class LegacyForgeModdingSettings {
    @Nullable
    private String neoForgeVersion;

    @Nullable
    private String forgeVersion;

    @Nullable
    private String mcpVersion;

    private Set<SourceSet> enabledSourceSets = new HashSet<>();

    private boolean obfuscateJar = true;

    @Inject
    public LegacyForgeModdingSettings(Project project) {
        // By default, enable modding deps only for the main source set
        var sourceSets = ExtensionUtils.getSourceSets(project);
        var mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        enabledSourceSets.add(mainSourceSet);
    }

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
    public Set<SourceSet> getEnabledSourceSets() {
        return enabledSourceSets;
    }

    public void setEnabledSourceSets(Set<SourceSet> enabledSourceSets) {
        this.enabledSourceSets = enabledSourceSets;
    }

    /**
     * {@return true if default reobfuscation task should be created}
     */
    public boolean isObfuscateJar() {
        return obfuscateJar;
    }

    public void setObfuscateJar(boolean obfuscateJar) {
        this.obfuscateJar = obfuscateJar;
    }
}
