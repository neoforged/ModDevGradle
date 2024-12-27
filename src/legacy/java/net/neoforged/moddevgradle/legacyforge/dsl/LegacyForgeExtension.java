package net.neoforged.moddevgradle.legacyforge.dsl;

import net.neoforged.moddevgradle.dsl.DataFileCollection;
import net.neoforged.moddevgradle.dsl.ModdingVersionSettings;
import net.neoforged.moddevgradle.dsl.NeoForgeExtension;
import net.neoforged.moddevgradle.dsl.UnitTest;
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
public abstract class LegacyForgeExtension extends NeoForgeExtension {
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
    @Override
    public void setVersion(Object version) {
        enableLegacyModding(settings -> {
            settings.setForgeVersion(version.toString());
        });
    }

    public void enableLegacyModding(Action<LegacyForgeModdingSettings> customizer) {
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

    @Override
    public void enableModding(Action<ModdingVersionSettings> customizer) {
        throw new RuntimeException("enableModding cannot be used with the legacy plugin. Use enableLegacyModding instead.");
    }

    @Override
    public UnitTest getUnitTest() {
        throw new RuntimeException("Unit testing cannot be used with the legacy plugin because old FML versions don't support it.");
    }
}
