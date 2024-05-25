package net.neoforged.moddevgradle.internal.utils;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;

public final class PropertyUtils {
    private PropertyUtils() {
    }

    public static Provider<String> getStringProperty(Project project, String propertyName) {
        return project.getProviders().gradleProperty(propertyName);
    }

    public static Provider<Boolean> getBooleanProperty(Project project, String propertyName) {
        return project.getProviders().gradleProperty(propertyName)
                .map(value -> {
                    try {
                        return Boolean.valueOf(value);
                    } catch (Exception e) {
                        throw new GradleException("Gradle Property " + propertyName + " is not set to a boolean value: '" + value + "'");
                    }
                });
    }

}
