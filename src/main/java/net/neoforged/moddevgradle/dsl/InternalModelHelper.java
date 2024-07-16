package net.neoforged.moddevgradle.dsl;

import net.neoforged.moddevgradle.internal.utils.StringUtils;
import org.gradle.api.artifacts.Configuration;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Used to prevent accidental leakage of internal methods into build script DSLs.
 */
@ApiStatus.Internal
public final class InternalModelHelper {
    public InternalModelHelper() {
    }

    public static Configuration getModConfiguration(final ModModel modModel) {
        return modModel.getConfiguration();
    }

    public static String nameOfRun(final RunModel run, @Nullable final String prefix, @Nullable final String suffix) {
        return StringUtils.uncapitalize((prefix == null ? "" : prefix)
                                        + StringUtils.capitalize(run.getName())
                                        + (suffix == null ? "" : StringUtils.capitalize(suffix)));
    }
}
