package net.neoforged.moddevgradle.legacyforge.dsl;

import net.neoforged.moddevgradle.dsl.DataFileCollection;
import net.neoforged.moddevgradle.dsl.ModDevExtension;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import net.neoforged.moddevgradle.legacyforge.internal.LegacyForgeModDevPlugin;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;

import javax.inject.Inject;
import java.util.List;

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
     * Enables modding for the main source set using the given Forge version.
     *
     * Shorthand for:
     * <code>
     *     enable { forgeVersion = '...' }
     * </code>
     */
    public void setVersion(Object version) {
        enable(settings -> {
            settings.setForgeVersion(version.toString());
        });
    }

    /**
     * Enables modding for the main source-set in Vanilla-mode.
     *
     * Shorthand for:
     * <code>
     *     enable { forgeVersion = '...' }
     * </code>
     */
    public void setMcpVersion(Object version) {
        enable(settings -> {
            settings.setMcpVersion(version.toString());
        });
    }

    public void enable(Action<LegacyForgeModdingSettings> customizer) {
        var plugin = project.getPlugins().getPlugin(LegacyForgeModDevPlugin.class);

        var settings = project.getObjects().newInstance(LegacyForgeModdingSettings.class);
        // By default, enable modding deps only for the main source set
        settings.getEnabledSourceSets().convention(project.provider(() -> {
            var sourceSets = ExtensionUtils.getSourceSets(project);
            return List.of(sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME));
        }));
        customizer.execute(settings);

        plugin.enable(project, settings, this);
    }
}
