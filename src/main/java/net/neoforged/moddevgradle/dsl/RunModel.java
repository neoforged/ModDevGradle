package net.neoforged.moddevgradle.dsl;

import net.neoforged.moddevgradle.internal.utils.StringUtils;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.Dependencies;
import org.gradle.api.artifacts.dsl.DependencyCollector;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
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
    }

    @Override
    public String getName() {
        return name;
    }

    public abstract DirectoryProperty getGameDirectory();

    public abstract MapProperty<String, String> getSystemProperties();

    public void systemProperty(String key, String value) {
        getSystemProperties().put(key, value);
    }

    /**
     * Allows overriding the main class for this run.
     */
    public abstract Property<String> getMainClass();

    public abstract ListProperty<String> getProgramArguments();

    public void programArgument(String arg) {
        getProgramArguments().add(arg);
    }

    public abstract ListProperty<String> getJvmArguments();

    public void jvmArgument(String arg) {
        getJvmArguments().add(arg);
    }

    public abstract SetProperty<ModModel> getMods();

    public abstract Property<String> getType();

    public void client() {
        getType().set("client");
    }

    public void data() {
        getType().set("data");
    }

    public void server() {
        getType().set("server");
    }

    public Configuration getAdditionalRuntimeClasspathConfiguration() {
        return configuration;
    }

    public abstract DependencyCollector getAdditionalRuntimeClasspath();

    public abstract Property<Level> getLogLevel();

    @Override
    public String toString() {
        return "Run[" + getName() + "]";
    }
}
