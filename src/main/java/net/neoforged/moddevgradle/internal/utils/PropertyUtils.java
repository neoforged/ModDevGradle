package net.neoforged.moddevgradle.internal.utils;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.provider.Provider;

public final class PropertyUtils {
    private PropertyUtils() {
    }

    public static Provider<String> getStringProperty(final Project project, final String propertyName) {
        return project.getProviders().gradleProperty(propertyName);
    }

    public static Provider<Boolean> getBooleanProperty(final Project project, final String propertyName) {
        return project.getProviders().gradleProperty(propertyName)
                .map(new Transformer<Boolean, String>() {
                    @Override
                    public Boolean transform(String value) {
                        try {
                            return Boolean.valueOf(value);
                        } catch (final Exception e) {
                            throw new GradleException("Gradle Property " + propertyName + " is not set to a boolean value: '" + value + "'");
                        }
                    }
                });
    }

}
