package net.neoforged.moddevgradle.dsl;

import net.neoforged.moddevgradle.internal.utils.PropertyUtils;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class NeoFormRuntime {
    private static final String DEFAULT_NFRT_VERSION = "0.1.56";

    @Inject
    public NeoFormRuntime(Project project) {
        getUseEclipseCompiler().convention(PropertyUtils.getBooleanProperty(project, "neoForge.neoFormRuntime.useEclipseCompiler").orElse(false));
        getVersion().convention(PropertyUtils.getStringProperty(project, "neoForge.neoFormRuntime.version").orElse(DEFAULT_NFRT_VERSION));
        getEnableCache().convention(PropertyUtils.getBooleanProperty(project, "neoForge.neoFormRuntime.enableCache").orElse(true));
        getVerbose().convention(PropertyUtils.getBooleanProperty(project, "neoForge.neoFormRuntime.verbose").orElse(false));
        getAnalyzeCacheMisses().convention(PropertyUtils.getBooleanProperty(project, "neoForge.neoFormRuntime.analyzeCacheMisses").orElse(false));
    }

    /**
     * Version of NFRT to use.
     */
    public abstract Property<String> getVersion();

    /**
     * Enable use of the Eclipse compiler to recompile the Minecraft assets.
     */
    public abstract Property<Boolean> getUseEclipseCompiler();

    public abstract Property<Boolean> getEnableCache();

    public abstract Property<Boolean> getVerbose();

    public abstract Property<Boolean> getAnalyzeCacheMisses();

}
