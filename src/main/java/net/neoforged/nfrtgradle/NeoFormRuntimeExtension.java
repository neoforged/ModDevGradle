package net.neoforged.nfrtgradle;

import javax.inject.Inject;
import net.neoforged.moddevgradle.internal.utils.PropertyUtils;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;

/**
 * Configures aspects of the NeoForm Runtime (NFRT), which is used by this plugin to produce
 * the Minecraft artifacts for compiling and mods.
 */
public abstract class NeoFormRuntimeExtension {
    public static final String NAME = "neoFormRuntime";

    private static final String DEFAULT_NFRT_VERSION = "1.0.42";

    @Inject
    public NeoFormRuntimeExtension(Project project) {
        getVersion().convention(PropertyUtils.getStringProperty(project, "neoForge.neoFormRuntime.version").orElse(DEFAULT_NFRT_VERSION));
        getUseEclipseCompiler().convention(PropertyUtils.getBooleanProperty(project, "neoForge.neoFormRuntime.useEclipseCompiler").orElse(false));
        getEnableCache().convention(PropertyUtils.getBooleanProperty(project, "neoForge.neoFormRuntime.enableCache").orElse(true));
        getVerbose().convention(PropertyUtils.getBooleanProperty(project, "neoForge.neoFormRuntime.verbose").orElse(false));
        getAnalyzeCacheMisses().convention(PropertyUtils.getBooleanProperty(project, "neoForge.neoFormRuntime.analyzeCacheMisses").orElse(false));
    }

    /**
     * Overrides the version of NFRT to use. This is an advanced feature. This plugin will default to a
     * compatible version.
     * <p>
     * <b>Gradle property:</b> {@code neoForge.neoFormRuntime.version}.
     */
    public abstract Property<String> getVersion();

    /**
     * Enable use of the Eclipse compiler to recompile the Minecraft assets.
     * <p>
     * While producing generally similar results, the Eclipse compiler can use multiple cores during recompilation,
     * speeding up the process if you have many CPU cores.
     * <p>
     * <b>Default:</b> {@code false}<br>
     * <b>Gradle property:</b> {@code neoForge.neoFormRuntime.useEclipseCompiler}.
     */
    public abstract Property<Boolean> getUseEclipseCompiler();

    /**
     * Controls whether NFRT uses its cache at all.
     * <p>
     * <b>Default:</b> {@code true}<br>
     * <b>Gradle property:</b> {@code neoForge.neoFormRuntime.enableCache}.
     */
    public abstract Property<Boolean> getEnableCache();

    /**
     * Enables verbose logging for NFRT tasks (such as createMinecraftArtifacts).
     * <p>
     * <b>Default:</b> {@code false}<br>
     * <b>Gradle property:</b> {@code neoForge.neoFormRuntime.verbose}.
     */
    public abstract Property<Boolean> getVerbose();

    /**
     * Enables additional logging in NFRT when an operation cannot reuse any of the previously cached intermediate results.
     * This has a performance impact, since it involves scanning the cache directory for similar results.
     * <p>
     * <b>Default:</b> {@code false}<br>
     * <b>Gradle property:</b> {@code neoForge.neoFormRuntime.analyzeCacheMisses}.
     */
    public abstract Property<Boolean> getAnalyzeCacheMisses();
}
