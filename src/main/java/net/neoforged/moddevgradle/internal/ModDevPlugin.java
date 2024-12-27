package net.neoforged.moddevgradle.internal;

import net.neoforged.minecraftdependencies.MinecraftDependenciesPlugin;
import net.neoforged.moddevgradle.dsl.ModdingVersionSettings;
import net.neoforged.moddevgradle.dsl.NeoForgeExtension;
import net.neoforged.moddevgradle.internal.jarjar.JarJarPlugin;
import net.neoforged.moddevgradle.internal.utils.VersionCapabilities;
import net.neoforged.nfrtgradle.NeoFormRuntimePlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * The main plugin class.
 */
public class ModDevPlugin implements Plugin<Project> {
    private static final Logger LOG = LoggerFactory.getLogger(ModDevPlugin.class);

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(JavaLibraryPlugin.class);
        project.getPlugins().apply(NeoFormRuntimePlugin.class);
        project.getPlugins().apply(MinecraftDependenciesPlugin.class);
        project.getPlugins().apply(JarJarPlugin.class);

        // Do not apply the repositories automatically if they have been applied at the settings-level.
        // It's still possible to apply them manually, though.
        if (!project.getGradle().getPlugins().hasPlugin(RepositoriesPlugin.class)) {
            project.getPlugins().apply(RepositoriesPlugin.class);
        } else {
            LOG.info("Not enabling NeoForged repositories since they were applied at the settings level");
        }

        var dataFileCollections = DataFileCollectionFactory.createDefault(project);
        var extension = project.getExtensions().create(
                NeoForgeExtension.NAME,
                NeoForgeExtension.class,
                dataFileCollections.accessTransformers().extension(),
                dataFileCollections.interfaceInjectionData().extension()
        );
        IdeIntegration.of(project, Branding.MDG).runTaskOnProjectSync(extension.getIdeSyncTasks());
    }

    public void enableModding(
            Project project,
            ModdingVersionSettings settings,
            NeoForgeExtension extension
    ) {
        var neoForgeVersion = settings.getNeoForgeVersion();
        var neoFormVersion = settings.getNeoFormVersion();
        if (neoForgeVersion == null && neoFormVersion == null) {
            throw new IllegalArgumentException("You must specify at least a NeoForge or a NeoForm version for vanilla-only mode");
        }

        var dependencyFactory = project.getDependencyFactory();

        ModuleDependency neoForgeModule = null;
        ModuleDependency modulePathDependency = null;
        ModuleDependency runTypesDataDependency = null;
        ModuleDependency testFixturesDependency = null;
        String moddingPlatformDataDependencyNotation = null;
        if (neoForgeVersion != null) {
            neoForgeModule = dependencyFactory.create("net.neoforged:neoforge:" + neoForgeVersion);
            moddingPlatformDataDependencyNotation = "net.neoforged:neoforge:" + neoForgeVersion + ":userdev";
            runTypesDataDependency = neoForgeModule.copy()
                    .capabilities(caps -> caps.requireCapability("net.neoforged:neoforge-moddev-config"));
            modulePathDependency = neoForgeModule.copy()
                    .capabilities(caps -> caps.requireCapability("net.neoforged:neoforge-moddev-module-path"))
                    // TODO: this is ugly; maybe make the configuration transitive in neoforge, or fix the SJH dep.
                    .exclude(Map.of("group", "org.jetbrains", "module", "annotations"));
            testFixturesDependency = neoForgeModule.copy()
                    .capabilities(caps -> caps.requireCapability("net.neoforged:neoforge-moddev-test-fixtures"));
        }

        ModuleDependency neoFormModule = null;
        String recompilableMinecraftWorkflowDataDependencyNotation = null;
        if (neoFormVersion != null) {
            neoFormModule = dependencyFactory.create("net.neoforged:neoform:" + neoFormVersion);
            recompilableMinecraftWorkflowDataDependencyNotation = "net.neoforged:neoform:" + neoFormVersion + "@zip";
        }

        // When a NeoForge version is specified, we use the dependencies published by that, and otherwise
        // we fall back to a potentially specified NeoForm version, which allows us to run in "Vanilla" mode.
        ModuleDependency neoForgeModDevLibrariesDependency;
        ArtifactNamingStrategy artifactNamingStrategy;
        if (neoForgeModule != null) {
            neoForgeModDevLibrariesDependency = neoForgeModule.copy()
                    .capabilities(c -> c.requireCapability("net.neoforged:neoforge-dependencies"));

            artifactNamingStrategy = ArtifactNamingStrategy.createDefault("neoforge-" + neoForgeVersion);
        } else {
            neoForgeModDevLibrariesDependency = neoFormModule.copy()
                    .capabilities(c -> c.requireCapability("net.neoforged:neoform-dependencies"));
            artifactNamingStrategy = ArtifactNamingStrategy.createDefault("vanilla-" + neoFormVersion);
        }

        var configurations = project.getConfigurations();

        var versionCapabilities = neoForgeVersion != null ? VersionCapabilities.ofNeoForgeVersion(neoForgeVersion)
                : VersionCapabilities.ofNeoFormVersion(neoFormVersion);

        var artifacts = ModDevArtifactsWorkflow.create(
                project,
                Branding.MDG,
                extension,
                neoForgeModule,
                moddingPlatformDataDependencyNotation,
                neoFormModule,
                recompilableMinecraftWorkflowDataDependencyNotation,
                neoForgeModDevLibrariesDependency,
                artifactNamingStrategy,
                configurations.getByName(DataFileCollectionFactory.CONFIGURATION_ACCESS_TRANSFORMERS),
                configurations.getByName(DataFileCollectionFactory.CONFIGURATION_INTERFACE_INJECTION_DATA),
                versionCapabilities
        );

        for (var sourceSet : settings.getEnabledSourceSets().get()) {
            artifacts.addToSourceSet(sourceSet.getName());
        }

        ModDevRunWorkflow.create(
                project,
                Branding.MDG,
                artifacts,
                modulePathDependency,
                runTypesDataDependency,
                testFixturesDependency,
                neoForgeModDevLibrariesDependency,
                extension.getRuns(),
                versionCapabilities
        );
    }
}
