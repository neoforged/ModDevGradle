package net.neoforged.neoforgegradle.dsl;

import org.gradle.api.Action;
import org.gradle.api.plugins.JavaPluginExtension;

public interface JarJar extends JarJarFeature {
    /**
     * Configure jarJar for a library feature with the given name, as created by {@link JavaPluginExtension#registerFeature(String,Action)}.
     * Creates a featureJarJar task and configuration for the feature if they are missing.
     * @param featureName the name of the feature to configure
     * @return the configuration for jarJar for the feature
     */
    JarJarFeature forFeature(String featureName);
}
