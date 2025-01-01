package net.neoforged.moddevgradle.internal;

import net.neoforged.minecraftdependencies.MinecraftDependenciesPlugin;
import net.neoforged.moddevgradle.dsl.ModDevExtension;
import net.neoforged.moddevgradle.dsl.ModdingVersionSettings;
import net.neoforged.moddevgradle.dsl.NeoForgeExtension;
import net.neoforged.moddevgradle.internal.jarjar.JarJarPlugin;
import net.neoforged.moddevgradle.internal.utils.VersionCapabilitiesInternal;
import net.neoforged.nfrtgradle.NeoFormRuntimePlugin;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        var dataFileCollections = DataFileCollections.create(project);
        project.getExtensions().create(
                NeoForgeExtension.NAME,
                NeoForgeExtension.class,
                dataFileCollections.accessTransformers().extension(),
                dataFileCollections.interfaceInjectionData().extension());
    }

    public void enable(
            Project project,
            ModdingVersionSettings settings,
            ModDevExtension extension) {
        var neoForgeVersion = settings.getVersion();
        var neoFormVersion = settings.getNeoFormVersion();
        if (neoForgeVersion == null && neoFormVersion == null) {
            throw new InvalidUserCodeException("You must specify at least a NeoForge or a NeoForm version for vanilla-only mode");
        }

        var dependencyFactory = project.getDependencyFactory();

        ModuleDependency neoForge = null;
        String neoForgeNotation = null;
        if (neoForgeVersion != null) {
            neoForge = dependencyFactory.create("net.neoforged:neoforge:" + neoForgeVersion);
            neoForgeNotation = "net.neoforged:neoforge:" + neoForgeVersion + ":userdev";
        }

        ModuleDependency neoForm = null;
        String neoFormNotation = null;
        if (neoFormVersion != null) {
            neoForm = dependencyFactory.create("net.neoforged:neoform:" + neoFormVersion);
            neoFormNotation = "net.neoforged:neoform:" + neoFormVersion + "@zip";
        }

        var versionCapabilities = neoForgeVersion != null ? VersionCapabilitiesInternal.ofNeoForgeVersion(neoForgeVersion)
                : VersionCapabilitiesInternal.ofNeoFormVersion(neoFormVersion);

        ArtifactNamingStrategy artifactNamingStrategy;
        // It's helpful to be able to differentiate the Vanilla jar and the NeoForge jar in classic multiloader setups.
        if (neoForge != null) {
            artifactNamingStrategy = ArtifactNamingStrategy.createNeoForge(versionCapabilities, "neoforge", neoForgeVersion);
        } else {
            artifactNamingStrategy = ArtifactNamingStrategy.createVanilla(neoFormVersion);
        }

        var configurations = project.getConfigurations();

        var dependencies = neoForge != null ? ModdingDependencies.create(neoForge, neoForgeNotation, neoForm, neoFormNotation, versionCapabilities)
                : ModdingDependencies.createVanillaOnly(neoForm, neoFormNotation);

        var artifacts = ModDevArtifactsWorkflow.create(
                project,
                settings.getEnabledSourceSets(),
                Branding.MDG,
                extension,
                dependencies,
                artifactNamingStrategy,
                configurations.getByName(DataFileCollections.CONFIGURATION_ACCESS_TRANSFORMERS),
                configurations.getByName(DataFileCollections.CONFIGURATION_INTERFACE_INJECTION_DATA),
                versionCapabilities);

        ModDevRunWorkflow.create(
                project,
                Branding.MDG,
                artifacts,
                extension.getRuns());
    }
}
