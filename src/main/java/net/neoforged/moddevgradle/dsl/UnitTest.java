package net.neoforged.moddevgradle.dsl;

import net.neoforged.moddevgradle.internal.ModDevPlugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;

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
        project.getPlugins().getPlugin(ModDevPlugin.class).setupTesting();
    }

    /**
     * The mod that will be loaded in JUnit tests.
     * The compiled classes from {@code src/test/java} and the resources from {@code src/test/resources}
     * will be added to that mod at runtime.
     */
    public abstract Property<ModModel> getTestedMod();
}
