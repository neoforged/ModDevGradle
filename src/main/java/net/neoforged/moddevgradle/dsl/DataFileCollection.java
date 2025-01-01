package net.neoforged.moddevgradle.dsl;

import java.util.function.Consumer;
import javax.inject.Inject;
import org.gradle.api.file.ConfigurableFileCollection;

/**
 * Holds data files (such as ATs) to be used or exposed.
 */
public abstract class DataFileCollection {
    private final Consumer<Object> publishArtifactCallback;

    @Inject
    public DataFileCollection(Consumer<Object> publishArtifactCallback) {
        this.publishArtifactCallback = publishArtifactCallback;
    }

    /**
     * Add the given paths to the {@linkplain #getFiles() file collection}.
     * <p>
     * Please note that {@code src/main/resources/META-INF/accesstransformer.cfg} is automatically
     * included for access transformers, if it exists.
     */
    public void from(Object... paths) {
        getFiles().from(paths);
    }

    /**
     * Configures the given files to be published alongside this project.
     * This can include files that are also passed to {@link #from}, but is not required to.
     * For allowed parameters, see {@link org.gradle.api.artifacts.dsl.ArtifactHandler}.
     */
    public void publish(Object artifactNotation) {
        publishArtifactCallback.accept(artifactNotation);
    }

    /**
     * {@return the files this collection contains}
     */
    public abstract ConfigurableFileCollection getFiles();
}
