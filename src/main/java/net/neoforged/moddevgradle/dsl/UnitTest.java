package net.neoforged.moddevgradle.dsl;

import net.neoforged.moddevgradle.internal.ModDevPlugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

import javax.inject.Inject;

/**
 * Used to enable and configure the JUnit integration.
 */
public abstract class UnitTest {
    private final Project project;

    @Inject
    public UnitTest(Project project) {
        this.project = project;
    }

    /**
     * Enables the integration.
     */
    public void enable() {
        project.getPlugins().getPlugin(ModDevPlugin.class).setupTestTask();
    }

    /**
     * The mod that will be loaded in JUnit tests.
     * The compiled classes from {@code src/test/java} and the resources from {@code src/test/resources}
     * will be added to that mod at runtime.
     */
    public abstract Property<ModModel> getTestedMod();

    /**
     * The mods to load when running unit tests. Defaults to all mods registered in the project.
     * This must contain {@link #getTestedMod()}.
     *
     * @see ModModel
     */
    public abstract SetProperty<ModModel> getLoadedMods();
}
