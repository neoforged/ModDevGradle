package net.neoforged.neoforgegradle.dsl;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class NeoForgeExtension {
    private final NamedDomainObjectContainer<Mod> mods;
    private final NamedDomainObjectContainer<Run> runs;

    @Inject
    public NeoForgeExtension(Project project) {
        mods = project.container(Mod.class);
        runs = project.container(Run.class);

        getEnableCache().convention(project.getProviders().gradleProperty("neoforge.cache").map(Boolean::valueOf).orElse(true));
        getVerbose().convention(project.getProviders().gradleProperty("neoforge.verbose").map(Boolean::valueOf).orElse(false));
    }

    /**
     * NeoForge version number.
     */
    public abstract Property<String> getVersion();

    /**
     * TODO: Allow overriding the NeoForm version used specifically or use only NeoForm.
     */
    public abstract Property<String> getNeoFormVersion();

    public abstract Property<Boolean> getVerbose();

    public abstract Property<Boolean> getEnableCache();

    public NamedDomainObjectSet<Mod> getMods() {
        return mods;
    }

    public NamedDomainObjectSet<Run> getRuns() {
        return runs;
    }
}
