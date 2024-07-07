package net.neoforged.moddevgradle.dsl;

import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import net.neoforged.moddevgradle.internal.utils.StringUtils;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.Dependencies;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.SourceSet;
import org.slf4j.event.Level;

import javax.inject.Inject;

/**
 * Model of a run. Each run will generate a corresponding IDE run and {@code runXxx} gradle task.
 */
public abstract class RunModel implements Named, Dependencies {
    private final String name;
    /**
     * Sanitized name: converted to upper camel case and with invalid characters removed.
     */
    final String baseName;

    private final Configuration configuration;

    @Inject
    public RunModel(String name, Project project) {
        this.name = name;
        this.baseName = StringUtils.toCamelCase(name, false);
        getMods().convention(project.getExtensions().getByType(NeoForgeExtension.class).getMods());

        getGameDirectory().convention(project.getLayout().getProjectDirectory().dir("run"));

        configuration = project.getConfigurations().create(InternalModelHelper.nameOfRun(this, "", "additionalRuntimeClasspath"), configuration -> {
            configuration.setCanBeResolved(false);
            configuration.setCanBeConsumed(false);
        });

        getLogLevel().convention(Level.INFO);

        // Build a nicer name for the IDE run configuration
        boolean isSubProject = project.getRootProject() != project;
        var ideName = StringUtils.capitalize(name);
        if (isSubProject) {
            ideName = project.getName() + " - " + ideName;
        }
        getIdeName().convention(ideName);

        getSourceSet().convention(ExtensionUtils.getSourceSets(project).getByName(SourceSet.MAIN_SOURCE_SET_NAME));
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Name for the run configuration in the IDE.
     */
    public abstract Property<String> getIdeName();

    public abstract DirectoryProperty getGameDirectory();

    public abstract MapProperty<String, String> getEnvironment();

    public void environment(String key, String value) {
        getEnvironment().put(key, value);
    }

    /**
     * Additional system properties to add to the JVM arguments.
     */
    public abstract MapProperty<String, String> getSystemProperties();

    public void systemProperty(String key, String value) {
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

    public void programArgument(String arg) {
        getProgramArguments().add(arg);
    }

    /**
     * Additional JVM arguments to be added to the run configuration.
     */
    public abstract ListProperty<String> getJvmArguments();

    public void jvmArgument(String arg) {
        getJvmArguments().add(arg);
    }

    public abstract SetProperty<ModModel> getMods();

    /**
     * Sets the run configuration type from NeoForge that should be used.
     */
    public abstract Property<String> getType();

    /**
     * Equivalent to setting {@code type = "client"}
     */
    public void client() {
        getType().set("client");
    }

    /**
     * Equivalent to setting {@code type = "data"}
     */
    public void data() {
        getType().set("data");
    }

    /**
     * Equivalent to setting {@code type = "server"}
     */
    public void server() {
        getType().set("server");
    }

    public Configuration getAdditionalRuntimeClasspathConfiguration() {
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
    public String toString() {
        return "Run[" + getName() + "]";
    }
}
