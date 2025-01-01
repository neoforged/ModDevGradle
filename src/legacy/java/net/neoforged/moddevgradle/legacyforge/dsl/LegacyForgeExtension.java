package net.neoforged.moddevgradle.legacyforge.dsl;

import javax.inject.Inject;
import net.neoforged.moddevgradle.dsl.DataFileCollection;
import net.neoforged.moddevgradle.dsl.ModDevExtension;
import net.neoforged.moddevgradle.legacyforge.internal.LegacyForgeModDevPlugin;
import org.gradle.api.Action;
import org.gradle.api.Project;

/**
 * This is the top-level {@code legacyForge} extension, used to configure the moddev plugin.
 */
public abstract class LegacyForgeExtension extends ModDevExtension {
    private final Project project;

    @Inject
    public LegacyForgeExtension(Project project,
            DataFileCollection accessTransformers,
            DataFileCollection interfaceInjectionData) {
        super(project, accessTransformers, interfaceInjectionData);
        this.project = project;
    }

    /**
     * Enables modding for the main source-set using the given Forge version.
     * <p>
     * Shorthand for:
     * <code>
     * enable { forgeVersion = '...' }
     * </code>
     */
    public void setVersion(String version) {
        enable(settings -> {
            settings.setForgeVersion(version);
        });
    }

    /**
     * Enables modding for the main source-set in Vanilla-mode.
     * <p>
     * Shorthand for:
     * <code>
     * enable { mcpVersion = '...' }
     * </code>
     */
    public void setMcpVersion(String version) {
        enable(settings -> {
            settings.setMcpVersion(version);
        });
    }

    public void enable(Action<LegacyForgeModdingSettings> customizer) {
        var plugin = project.getPlugins().getPlugin(LegacyForgeModDevPlugin.class);

        var settings = project.getObjects().newInstance(LegacyForgeModdingSettings.class);
        customizer.execute(settings);

        plugin.enable(project, settings, this);
    }
}
