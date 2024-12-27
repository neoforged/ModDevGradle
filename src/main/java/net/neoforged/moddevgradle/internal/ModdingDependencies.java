package net.neoforged.moddevgradle.internal;

import net.neoforged.moddevgradle.internal.utils.VersionCapabilities;
import org.gradle.api.artifacts.ModuleDependency;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public record ModdingDependencies(
        @Nullable ModuleDependency neoForgeDependency,
        @Nullable String neoForgeDependencyNotation,
        @Nullable ModuleDependency neoFormDependency,
        @Nullable String neoFormDependencyNotation,
        ModuleDependency gameLibrariesDependency,
        @Nullable ModuleDependency modulePathDependency,
        @Nullable ModuleDependency runTypesConfigDependency,
        @Nullable ModuleDependency testFixturesDependency
) {

    public static ModdingDependencies create(ModuleDependency neoForge,
                                             String neoForgeNotation,
                                             ModuleDependency neoForm,
                                             String neoFormNotation,
                                             VersionCapabilities versionCapabilities) {

        ModuleDependency modulePathDependency = null;
        ModuleDependency runTypesDataDependency = null;
        ModuleDependency librariesDependency;
        if (neoForge != null) {
            runTypesDataDependency = neoForge.copy()
                    .capabilities(caps -> caps.requireCapability("net.neoforged:neoforge-moddev-config"));
            modulePathDependency = neoForge.copy()
                    .capabilities(caps -> caps.requireCapability("net.neoforged:neoforge-moddev-module-path"))
                    // TODO: this is ugly; maybe make the configuration transitive in neoforge, or fix the SJH dep.
                    .exclude(Map.of("group", "org.jetbrains", "module", "annotations"));
            librariesDependency = neoForge.copy()
                    .capabilities(c -> c.requireCapability("net.neoforged:neoforge-dependencies"));
        } else {
            librariesDependency = neoForm.copy()
                    .capabilities(c -> c.requireCapability("net.neoforged:neoform-dependencies"));
        }

        ModuleDependency testFixturesDependency = null;
        if (neoForge != null && versionCapabilities.testFixtures()) {
            testFixturesDependency = neoForge.copy()
                    .capabilities(caps -> caps.requireCapability("net.neoforged:neoforge-moddev-test-fixtures"));
        }

        return new ModdingDependencies(
                neoForge,
                neoForgeNotation,
                neoForm,
                neoFormNotation,
                librariesDependency,
                modulePathDependency,
                runTypesDataDependency,
                testFixturesDependency
        );
    }
}
