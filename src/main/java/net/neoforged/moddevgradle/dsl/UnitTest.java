package net.neoforged.moddevgradle.dsl;

import net.neoforged.moddevgradle.internal.ModDevPlugin;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
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

        getGameDirectory().convention(project.getLayout().getBuildDirectory().dir("minecraft-junit"));
    }

    /**
     * Enables the integration.
     */
    public void enable() {
        project.getPlugins().getPlugin(ModDevPlugin.class).setupTesting();
    }

    /**
     * The mod that will be loaded in JUnit tests.
     */
    public abstract Property<ModModel> getTestedMod();

    /**
     * The working directory for the unit test.
     */
    public abstract DirectoryProperty getGameDirectory();
}
