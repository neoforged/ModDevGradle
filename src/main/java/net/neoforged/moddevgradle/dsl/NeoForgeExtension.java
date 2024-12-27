package net.neoforged.moddevgradle.dsl;

import net.neoforged.moddevgradle.internal.ModDevPlugin;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;

import javax.inject.Inject;
import java.util.List;

/**
 * This is the top-level {@code neoForge} extension, used to configure the moddev plugin.
 */
public abstract class NeoForgeExtension extends ModDevExtension {
    public static final String NAME = "neoForge";

    private final Project project;
    private final UnitTest unitTest;

    @Inject
    public NeoForgeExtension(Project project, DataFileCollection accessTransformers, DataFileCollection interfaceInjectionData) {
        super(project, accessTransformers, interfaceInjectionData);
        this.project = project;
        unitTest = project.getObjects().newInstance(UnitTest.class);
        unitTest.getLoadedMods().convention(getMods());
    }

    /**
     * Enables modding on the main source set with the given NeoForge version.
     *
     * Shorthand for:
     * <code>
     *     enable { version = '...' }
     * </code>
     */
    public void setVersion(Object version) {
        enable(settings -> {
            settings.setVersion(version.toString());
        });
    }

    /**
     * Enables the Vanilla-only mode of ModDevGradle.
     *
     * Shorthand for:
     * <code>
     *     enable { neoFormVersion = '...' }
     * </code>
     */
    public void setNeoFormVersion(Object version) {
        enable(settings -> {
            settings.setNeoFormVersion(version.toString());
        });
    }

    public void enable(Action<ModdingVersionSettings> customizer) {
        var modDevPlugin = project.getPlugins().getPlugin(ModDevPlugin.class);

        var settings = project.getObjects().newInstance(ModdingVersionSettings.class);
        // By default, enable modding deps only for the main source set
        settings.getEnabledSourceSets().convention(project.provider(() -> {
            var sourceSets = ExtensionUtils.getSourceSets(project);
            return List.of(sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME));
        }));
        customizer.execute(settings);

        modDevPlugin.enable(project, settings, this);
    }

    public UnitTest getUnitTest() {
        return unitTest;
    }

    public void unitTest(Action<UnitTest> action) {
        action.execute(unitTest);
    }
}
