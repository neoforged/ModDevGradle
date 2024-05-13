package net.neoforged.neoforgegradle.dsl;

public interface JarJarFeature {
    /**
     * Enable the jarJar default configuration, unless already disabled
     */
    void enable();

    /**
     * Disable the jarJar default configuration
     */
    void disable();

    /**
     *
     * @param disable or un-disable the jarJar default configuration; allows reversing {@link #disable()}.
     */
    void disable(boolean disable);

    /**
     * {@return whether the jarJar task should by default copy the contents and manifest of the jar task}
     */
    boolean getDefaultSourcesDisabled();

    /**
     * Stop the jarJar task from copying the contents and manifest of the jar task
     */
    void disableDefaultSources();

    /**
     * Set whether the jarJar task should copy the contents and manifest of the jar task
     * @param value whether to disable the default sources
     */
    void disableDefaultSources(boolean value);
}
