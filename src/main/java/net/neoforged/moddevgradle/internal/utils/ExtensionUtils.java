package net.neoforged.moddevgradle.internal.utils;

import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.SourceSetContainer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ExtensionUtils {
    private ExtensionUtils() {
    }

    public static <T> T getExtension(final ExtensionAware holder, final String name, final Class<T> expectedType) {
        final T extension = findExtension(holder.getExtensions(), name, expectedType);
        if (extension == null) {
            throw new IllegalStateException("Could not find extension " + name + " on " + holder);
        }
        return extension;
    }

    public static <T> T getExtension(final ExtensionContainer container, final String name, final Class<T> expectedType) {
        final T extension = findExtension(container, name, expectedType);
        if (extension == null) {
            throw new IllegalStateException("Could not find extension " + name + " on " + container);
        }
        return extension;
    }

    public static <T> T findExtension(final ExtensionAware holder, final String name, final Class<T> expectedType) {
        return findExtension(holder.getExtensions(), name, expectedType);
    }

    @Nullable
    public static <T> T findExtension(final ExtensionContainer container, final String name, final Class<T> expectedType) {
        final Object extension = container.findByName(name);
        if (extension == null) {
            return null;
        }
        if (!expectedType.isInstance(extension)) {
            throw new IllegalStateException("Extension " + name + " on " + container + " is not of type " + expectedType.getName() + " but of " + extension.getClass());
        }
        return expectedType.cast(extension);
    }

    public static SourceSetContainer getSourceSets(final Project project) {
        return getExtension(project, "sourceSets", SourceSetContainer.class);
    }

    @Nullable
    public static SourceSetContainer findSourceSets(final Project project) {
        return findExtension(project, "sourceSets", SourceSetContainer.class);
    }
}
