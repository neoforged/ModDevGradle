package net.neoforged.moddevgradle.dsl;

import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import net.neoforged.moddevgradle.internal.utils.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.Dependencies;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.slf4j.event.Level;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Model of a run. Each run will generate a corresponding IDE run and {@code runXxx} gradle task.
 */
public abstract class RunModel implements Named, Dependencies {
    private static final Pattern VALID_RUN_NAME = Pattern.compile("[a-zA-Z][\\w-]*");

    private final String name;

    private final Configuration configuration;

    /**
     * The name of Gradle tasks that should be run before running this run.
     */
    private List<TaskProvider<?>> tasksBefore = new ArrayList<>();

    @Inject
    public RunModel(final String name, final Project project) {
        this.name = name;
        if (!VALID_RUN_NAME.matcher(name).matches()) {
            throw new GradleException("Run name '" + name + "' is invalid! It must match " + VALID_RUN_NAME.pattern());
        }

        getMods().convention(project.getExtensions().getByType(NeoForgeExtension.class).getMods());

        getGameDirectory().convention(project.getLayout().getProjectDirectory().dir("run"));

        configuration = project.getConfigurations().create(InternalModelHelper.nameOfRun(this, "", "additionalRuntimeClasspath"), new Action<Configuration>() {
            @Override
            public void execute(Configuration configuration) {
                configuration.setCanBeResolved(false);
                configuration.setCanBeConsumed(false);
            }
        });

        getLogLevel().convention(Level.INFO);

        // Build a nicer name for the IDE run configuration
        final boolean isSubProject = project.getRootProject() != project;
        String ideName = StringUtils.capitalize(name);
        if (isSubProject) {
            ideName = project.getName() + " - " + ideName;
        }
        getIdeName().convention(ideName);

        getSourceSet().convention(ExtensionUtils.getSourceSets(project).getByName(SourceSet.MAIN_SOURCE_SET_NAME));
    }

    @Override
    public final String getName() {
        return name;
    }

    /**
     * Name for the run configuration in the IDE.
     */
    public abstract Property<String> getIdeName();

    /**
     * Directory that the game will run in. Defaults to {@code run/}.
     */
    public abstract DirectoryProperty getGameDirectory();

    /**
     * Additional environment variables.
     */
    public abstract MapProperty<String, String> getEnvironment();

    /**
     * Shorthand to set a single environment variable.
     */
    public void environment(final String key, final String value) {
        getEnvironment().put(key, value);
    }

    /**
     * Additional system properties to add to the JVM arguments.
     */
    public abstract MapProperty<String, String> getSystemProperties();

    /**
     * Shorthand to set a single system property.
     */
    public void systemProperty(final String key, final String value) {
        getSystemProperties().put(key, value);
    }

    /**
     * Allows overriding the main class for this run.
     */
    public abstract Property<String> getMainClass();

    /**
     * Additional program arguments to add to the run configuration.
     */
    public abstract ListProperty<String> getProgramArguments();

    /**
     * Shorthand to add a single program argument.
     */
    public void programArgument(final String arg) {
        getProgramArguments().add(arg);
    }

    /**
     * Additional JVM arguments to be added to the run configuration.
     */
    public abstract ListProperty<String> getJvmArguments();

    /**
     * Shorthand to add a single JVM argument.
     */
    public void jvmArgument(final String arg) {
        getJvmArguments().add(arg);
    }

    /**
     * The mods for this run. Defaults to all mods registered in the project.
     *
     * @see ModModel
     */
    public abstract SetProperty<ModModel> getMods();

    /**
     * Sets the run configuration type from NeoForge that should be used.
     */
    public abstract Property<String> getType();

    /**
     * Equivalent to setting {@code type = "client"}.
     */
    public void client() {
        getType().set("client");
    }

    /**
     * Equivalent to setting {@code type = "data"}.
     */
    public void data() {
        getType().set("data");
    }

    /**
     * Equivalent to setting {@code type = "server"}.
     */
    public void server() {
        getType().set("server");
    }

    /**
     * Gets the names of Gradle tasks that should be run before running this run.
     */
    public final List<TaskProvider<?>> getTasksBefore() {
        return tasksBefore;
    }

    /**
     * Sets the names of Gradle tasks that should be run before running this run.
     * This also slows down running through your IDE since it will first execute Gradle to run the requested
     * tasks, and then run the actual game.
     */
    public void setTasksBefore(final List<TaskProvider<?>> taskNames) {
        this.tasksBefore = new ArrayList<>(Objects.requireNonNull(taskNames, "taskNames"));
    }

    /**
     * Configures the given Task to be run before launching the game.
     * This also slows down running through your IDE since it will first execute Gradle to run the requested
     * tasks, and then run the actual game.
     */
    public void taskBefore(final TaskProvider<?> task) {
        this.tasksBefore.add(task);
    }

    /**
     * Configures the given Task to be run before launching the game.
     * This also slows down running through your IDE since it will first execute Gradle to run the requested
     * tasks, and then run the actual game.
     */
    public void taskBefore(final Task task) {
        this.tasksBefore.add(task.getProject().getTasks().named(task.getName()));
    }

    public final Configuration getAdditionalRuntimeClasspathConfiguration() {
        return configuration;
    }

    /**
     * Changes the games log-level.
     */
    public abstract Property<Level> getLogLevel();

    /**
     * Sets the source set to be used as the main classpath of this run.
     * Defaults to the {@code main} source set.
     * Eclipse does not support having multiple different classpaths per project beyond a separate unit-testing
     * classpath.
     */
    public abstract Property<SourceSet> getSourceSet();

    @Override
    public final String toString() {
        return "Run[" + getName() + "]";
    }
}
