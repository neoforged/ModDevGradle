package net.neoforged.moddevgradle.dsl;

import net.neoforged.moddevgradle.internal.utils.StringUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Used to prevent accidental leakage of internal methods into build script DSLs.
 */
@ApiStatus.Internal
public class InternalModelHelper {
    public InternalModelHelper() {}

    public static String nameOfRun(RunModel run, @Nullable String prefix, @Nullable String suffix) {
        return StringUtils.uncapitalize((prefix == null ? "" : prefix)
                + StringUtils.capitalize(run.getName())
                + (suffix == null ? "" : StringUtils.capitalize(suffix)));
    }
}
