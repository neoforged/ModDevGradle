package net.neoforged.moddevgradle.dsl;

import org.gradle.api.file.ConfigurableFileCollection;

/**
 * Holds data files (such as ATs) to be used or exposed.
 */
public abstract class DataFileCollection {
    /**
     * Add the given paths to the {@linkplain #getFiles() file collection}.
     * <p>
     * Using this method replaces any previously present default value.
     */
    public void from(final Object... paths) {
        getFiles().from(paths);
    }

    /**
     * Add the given paths to the {@linkplain #getPublished() published file collection}.
     * <p>
     * Using this method replaces any previously present default value.
     */
    public void publish(final Object... paths) {
        getPublished().from(paths);
    }

    /**
     * {@return the files this collection contains}
     */
    public abstract ConfigurableFileCollection getFiles();

    /**
     * {@return the files that should be published and that can be consumed by dependents}
     */
    public abstract ConfigurableFileCollection getPublished();
}
