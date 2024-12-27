package net.neoforged.moddevgradle.dsl;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.Nullable;

public abstract class ModdingVersionSettings {
    @Nullable
    private String version;

    @Nullable
    private String neoFormVersion;

    public @Nullable String getVersion() {
        return version;
    }

    public @Nullable String getNeoFormVersion() {
        return neoFormVersion;
    }

    /**
     * NeoForge version number. You have to set either this or {@link #setNeoFormVersion}.
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * You can set this property to a version of <a href="https://projects.neoforged.net/neoforged/neoform">NeoForm</a>
     * to either override the version used in the version of NeoForge you set, or to compile against
     * Vanilla artifacts that have no NeoForge code added.
     */
    public void setNeoFormVersion(String version) {
        this.neoFormVersion = version;
    }

    /**
     * Contains the list of source sets for which access to Minecraft classes should be configured.
     * Defaults to the main source set, but can also be set to an empty list.
     */
    public abstract ListProperty<SourceSet> getEnabledSourceSets();
}
