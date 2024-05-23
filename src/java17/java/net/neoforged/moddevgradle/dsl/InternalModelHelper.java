package net.neoforged.moddevgradle.dsl;

import org.gradle.api.artifacts.Configuration;
import org.jetbrains.annotations.ApiStatus;

/**
 * Used to prevent accidental leakage of internal methods into build script DSLs.
 */
@ApiStatus.Internal
public class InternalModelHelper {
    public InternalModelHelper() {
    }

    public static Configuration getModConfiguration(ModModel modModel) {
        return modModel.getConfiguration();
    }
}
