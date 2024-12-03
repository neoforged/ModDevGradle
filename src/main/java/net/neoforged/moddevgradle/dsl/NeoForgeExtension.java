package net.neoforged.moddevgradle.dsl;

import net.neoforged.moddevgradle.internal.ModDevExtension;
import net.neoforged.moddevgradle.internal.ModDevPlugin;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
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

    @Deprecated(forRemoval = true)
    public void setVersion(Object any) {
        throw new InvalidUserCodeException("Please use enableModding { neoForgeVersion = ... } instead of the version property.");
    }

    public void enableModding(Action<ModdingVersionSettings> customizer) {
        var modDevPlugin = project.getPlugins().getPlugin(ModDevPlugin.class);

        var settings = project.getObjects().newInstance(ModdingVersionSettings.class);
        // By default, enable modding deps only for the main source set
        settings.getEnabledSourceSets().convention(project.provider(() -> {
            var sourceSets = ExtensionUtils.getSourceSets(project);
            return List.of(sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME));
        }));
        customizer.execute(settings);

        modDevPlugin.enableModding(project, settings, this);
    }

    public UnitTest getUnitTest() {
        return unitTest;
    }

    public void unitTest(Action<UnitTest> action) {
        action.execute(unitTest);
    }
}
