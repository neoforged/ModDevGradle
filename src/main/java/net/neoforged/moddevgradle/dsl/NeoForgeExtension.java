package net.neoforged.moddevgradle.dsl;

import javax.inject.Inject;
import net.neoforged.moddevgradle.internal.ModDevPlugin;
import org.gradle.api.Action;
import org.gradle.api.Project;

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
     * enable { version = '...' }
     * </code>
     */
    public void setVersion(String version) {
        enable(settings -> {
            settings.setVersion(version);
        });
    }

    /**
     * Enables the Vanilla-only mode of ModDevGradle.
     *
     * Shorthand for:
     * <code>
     * enable { neoFormVersion = '...' }
     * </code>
     */
    public void setNeoFormVersion(String version) {
        enable(settings -> {
            settings.setNeoFormVersion(version);
        });
    }

    public void enable(Action<ModdingVersionSettings> customizer) {
        var modDevPlugin = project.getPlugins().getPlugin(ModDevPlugin.class);

        var settings = project.getObjects().newInstance(ModdingVersionSettings.class);
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
