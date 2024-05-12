package net.neoforged.neoforgegradle.dsl;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.util.List;

public abstract class NeoForgeExtension {
    private final NamedDomainObjectContainer<ModModel> mods;
    private final NamedDomainObjectContainer<RunModel> runs;

    @Inject
    public NeoForgeExtension(Project project) {
        mods = project.container(ModModel.class);
        runs = project.container(RunModel.class);

        getEnableCache().convention(project.getProviders().gradleProperty("neoforge.cache").map(Boolean::valueOf).orElse(true));
        getVerbose().convention(project.getProviders().gradleProperty("neoforge.verbose").map(Boolean::valueOf).orElse(false));

        getAccessTransformers().convention(project.provider(() -> {
            // TODO Can we scan the source sets for the main source sets resource dir?
            // Only return this when it actually exists
            var defaultPath = "src/main/resources/META-INF/accesstransformer.cfg";
            if (!project.file(defaultPath).exists()) {
                return List.of();
            }
            return List.of(defaultPath);
        }));
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

    public abstract ListProperty<String> getAccessTransformers();

    public NamedDomainObjectSet<ModModel> getMods() {
        return mods;
    }

    public NamedDomainObjectSet<RunModel> getRuns() {
        return runs;
    }
}
