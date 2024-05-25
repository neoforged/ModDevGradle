package net.neoforged.moddevgradle.boot;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.plugins.PluginAware;
import org.gradle.util.GradleVersion;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This is just a trampoline to perform the Java and Gradle version check, and is compiled for Java 8 in the real Jar.
 */
public abstract class TrampolinePlugin<T extends PluginAware> implements Plugin<T> {
    private static final int MIN_JAVA_VERSION = 17;
    private static final GradleVersion MIN_GRADLE_VERSION = GradleVersion.version("8.7");

    private final String pluginClassName;

    TrampolinePlugin(String pluginClassName) {
        this.pluginClassName = pluginClassName;
    }

    @SuppressWarnings("unchecked")
    @Override
    public final void apply(T target) {
        int javaMajorVersion = getJavaMajorVersion();

        if (javaMajorVersion < MIN_JAVA_VERSION) {
            throw new GradleException("To use the NeoForge plugin, please run Gradle with Java " + MIN_JAVA_VERSION + " or newer. You are currently running on Java " + javaMajorVersion + " (" + System.getProperty("java.specification.version") + ").");
        }

        if (GradleVersion.current().compareTo(MIN_GRADLE_VERSION) < 0) {
            throw new GradleException("To use the NeoForge plugin, please use at least " + MIN_GRADLE_VERSION
                                      + ". You are currently using " + GradleVersion.current() + ".");
        }

        try {
            Class<? extends Plugin<?>> pluginClass = (Class<? extends Plugin<?>>) Class.forName(pluginClassName);
            target.getPlugins().apply(pluginClass);
        } catch (ClassNotFoundException e) {
            throw new GradleException("Failed to find main plugin class.", e);
        }
    }

    private int getJavaMajorVersion() {
        String specVersion = System.getProperty("java.specification.version");
        if (specVersion == null) {
            return 0;
        }

        Pattern firstNumber = Pattern.compile("^(\\d+)\\D*");
        Matcher matcher = firstNumber.matcher(specVersion);
        if (!matcher.find()) {
            return 0;
        }

        return Integer.parseInt(matcher.group(1));
    }

    @Override
    public String toString() {
        return "Trampoline for " + pluginClassName;
    }
}
