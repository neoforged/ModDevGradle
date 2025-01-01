package net.neoforged.moddevgradle.dsl;

import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.Nullable;

public abstract class ModdingVersionSettings {
    @Nullable
    private String version;

    @Nullable
    private String neoFormVersion;

    private Set<SourceSet> enabledSourceSets = new HashSet<>();

    @Inject
    public ModdingVersionSettings(Project project) {
        // By default, enable modding deps only for the main source set
        var sourceSets = ExtensionUtils.getSourceSets(project);
        var mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        enabledSourceSets.add(mainSourceSet);
    }

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
    public Set<SourceSet> getEnabledSourceSets() {
        return enabledSourceSets;
    }

    public void setEnabledSourceSets(Set<SourceSet> enabledSourceSets) {
        this.enabledSourceSets = enabledSourceSets;
    }
}
