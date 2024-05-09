package net.neoforged.neoforgegradle;

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

    abstract Property<String> getVersion();

    abstract Property<String> getNeoFormVersion();

    abstract Property<Boolean> getVerbose();

    abstract Property<Boolean> getEnableCache();

    public NamedDomainObjectSet<Mod> getMods() {
        return mods;
    }

    public NamedDomainObjectSet<Run> getRuns() {
        return runs;
    }
}
