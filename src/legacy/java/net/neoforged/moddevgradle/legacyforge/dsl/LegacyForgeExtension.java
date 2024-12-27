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
     * Shorthand for:
     * <code>
     *     enableModding { forgeVersion = '...' }
     * </code>
     */
    public void setVersion(Object version) {
        enableModding(settings -> {
            settings.setForgeVersion(version.toString());
        });
    }

    public void enableModding(Action<LegacyForgeModdingSettings> customizer) {
        var plugin = project.getPlugins().getPlugin(LegacyForgeModDevPlugin.class);

        var settings = project.getObjects().newInstance(LegacyForgeModdingSettings.class);
        // By default, enable modding deps only for the main source set
        settings.getEnabledSourceSets().convention(project.provider(() -> {
            var sourceSets = ExtensionUtils.getSourceSets(project);
            return List.of(sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME));
        }));
        customizer.execute(settings);

        plugin.enableModding(project, settings, this);
    }
}