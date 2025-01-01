package net.neoforged.moddevgradle.internal;

import java.util.Map;
import net.neoforged.moddevgradle.internal.utils.VersionCapabilitiesInternal;
import org.gradle.api.artifacts.ModuleDependency;
import org.jetbrains.annotations.Nullable;

public record ModdingDependencies(
        @Nullable ModuleDependency neoForgeDependency,
        @Nullable String neoForgeDependencyNotation,
        @Nullable ModuleDependency neoFormDependency,
        @Nullable String neoFormDependencyNotation,
        ModuleDependency gameLibrariesDependency,
        @Nullable ModuleDependency modulePathDependency,
        @Nullable ModuleDependency runTypesConfigDependency,
        @Nullable ModuleDependency testFixturesDependency) {
    public static ModdingDependencies create(ModuleDependency neoForge,
            String neoForgeNotation,
            @Nullable ModuleDependency neoForm,
            @Nullable String neoFormNotation,
            VersionCapabilitiesInternal versionCapabilities) {
        var runTypesDataDependency = neoForge.copy()
                .capabilities(caps -> caps.requireCapability("net.neoforged:neoforge-moddev-config"));
        var modulePathDependency = neoForge.copy()
                .capabilities(caps -> caps.requireCapability("net.neoforged:neoforge-moddev-module-path"))
                // TODO: this is ugly; maybe make the configuration transitive in neoforge, or fix the SJH dep.
                .exclude(Map.of("group", "org.jetbrains", "module", "annotations"));
        var librariesDependency = neoForge.copy()
                .capabilities(c -> c.requireCapability("net.neoforged:neoforge-dependencies"));

        ModuleDependency testFixturesDependency = null;
        if (versionCapabilities.testFixtures()) {
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
                testFixturesDependency);
    }

    public static ModdingDependencies createVanillaOnly(ModuleDependency neoForm, String neoFormNotation) {
        var librariesDependency = neoForm.copy()
                .capabilities(c -> c.requireCapability("net.neoforged:neoform-dependencies"));

        return new ModdingDependencies(
                null,
                null,
                neoForm,
                neoFormNotation,
                librariesDependency,
                null,
                null,
                null);
    }
}
