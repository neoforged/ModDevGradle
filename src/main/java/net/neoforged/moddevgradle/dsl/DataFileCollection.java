package net.neoforged.moddevgradle.dsl;

import org.gradle.api.file.ConfigurableFileCollection;

/**
 * A DSL model that can hold data files (such as ATs) to be used or exposed.
 */
public abstract class DataFileCollection {

    /**
     * Add the given paths to the file collection.
     * <p>
     * This method replaces any values set conventionally.
     */
    public void from(Object... paths) {
        getFiles().from(paths);
    }

    /**
     * Add the given paths to the published files collection.
     * <p>
     * This method replaces any values set conventionally.
     */
    public void publish(Object... paths) {
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
