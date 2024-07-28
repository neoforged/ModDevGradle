package net.neoforged.moddevgradle.dsl;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.file.ConfigurableFileCollection;

import javax.inject.Inject;
import java.util.function.Consumer;

/**
 * Holds data files (such as ATs) to be used or exposed.
 */
public abstract class DataFileCollection {
    private final Project project;
    private final Configuration configuration;
    private final DependencyFactory depFactory;
    private final Consumer<Object> publishArtifactCallback;

    @Inject
    public DataFileCollection(Project project, Configuration configuration, Consumer<Object> publishArtifactCallback) {
        this.project = project;
        this.configuration = configuration;
        depFactory = project.getDependencyFactory();
        this.publishArtifactCallback = publishArtifactCallback;
        configuration.withDependencies(dependencies -> dependencies.add(depFactory.create(getFiles())));
    }

    /**
     * Add the given paths to the {@linkplain #getFiles() file collection}.
     * <p>
     * Using this method replaces any previously present default value.
     */
    public void from(Object... paths) {
        configuration.getDependencies().add(project.getDependencyFactory().create(project.files(paths)));
    }

    /**
     * Configures the given files to be published alongside this project.
     */
    public void publish(Object artifactNotation) {
        publishArtifactCallback.accept(artifactNotation);
    }

    /**
     * {@return the files this collection contains}
     */
    public abstract ConfigurableFileCollection getFiles();
}
