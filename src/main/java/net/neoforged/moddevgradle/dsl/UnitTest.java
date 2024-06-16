package net.neoforged.moddevgradle.dsl;

import net.neoforged.moddevgradle.internal.ModDevPlugin;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

import javax.inject.Inject;

/**
 * Used to enable and configure the JUnit integration.
 */
public abstract class UnitTest {
    private final Project project;
    // The "main" source set
    private final SourceSet parentSourceSet;
    // The "test" source set
    public final Property<SourceSet> sourceSet;
    public final Property<String> testTask;

    @Inject
    public UnitTest(Project project, SourceSet parentSourceSet) {
        this.project = project;
        this.parentSourceSet = parentSourceSet;
        this.sourceSet = project.getObjects().property(SourceSet.class);
        this.sourceSet.convention(ExtensionUtils.getSourceSets(project).getByName("test"));
        this.testTask = project.getObjects().property(String.class);
        testTask.convention(JavaPlugin.TEST_TASK_NAME);
    }

    /**
     * Enables the integration.
     */
    public void enable() {
        project.getPlugins().getPlugin(ModDevPlugin.class).setupTesting(parentSourceSet, this);
    }

    /**
     * The mod that will be loaded in JUnit tests.
     * The compiled classes from {@code src/test/java} and the resources from {@code src/test/resources}
     * will be added to that mod at runtime.
     */
    public abstract Property<ModModel> getTestedMod();
}
