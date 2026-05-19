package net.neoforged.moddevgradle.internal;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import net.neoforged.minecraftdependencies.MinecraftDependenciesPlugin;
import net.neoforged.moddevgradle.dsl.ModDevExtension;
import net.neoforged.moddevgradle.dsl.ModdingVersionSettings;
import net.neoforged.moddevgradle.dsl.NeoForgeExtension;
import net.neoforged.moddevgradle.internal.jarjar.JarJarPlugin;
import net.neoforged.moddevgradle.internal.utils.VersionCapabilitiesInternal;
import net.neoforged.nfrtgradle.NeoFormRuntimeExtension;
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

        var configurations = project.getConfigurations();

        var dependencies = neoForge != null ? ModdingDependencies.create(
                neoForge,
                neoForgeNotation,
                neoForm,
                neoFormNotation,
                versionCapabilities)
                : ModdingDependencies.createVanillaOnly(neoForm, neoFormNotation);

        if (neoForgeVersion != null) {
            populateNeoForgeRepositoryFilter(project, neoForgeVersion);
        }

        ArtifactNamingStrategy artifactNamingStrategy;
        // It's helpful to be able to differentiate the Vanilla jar and the NeoForge jar in classic multiloader setups.
        if (neoForge == null) {
            artifactNamingStrategy = ArtifactNamingStrategy.createVanilla(neoFormVersion);
        } else if (versionCapabilities.needsNeoForgeInMinecraftJar()) {
            artifactNamingStrategy = ArtifactNamingStrategy.createNeoForge(versionCapabilities, "neoforge", neoForgeVersion);
        } else {
            artifactNamingStrategy = ArtifactNamingStrategy.createVanillaPatched(neoForgeVersion);
        }

        var artifacts = ModDevArtifactsWorkflow.create(
                project,
                settings.getEnabledSourceSets(),
                Branding.MDG,
                extension,
                dependencies,
                artifactNamingStrategy,
                configurations.getByName(DataFileCollections.CONFIGURATION_ACCESS_TRANSFORMERS),
                configurations.getByName(DataFileCollections.CONFIGURATION_INTERFACE_INJECTION_DATA),
                versionCapabilities,
                settings.isDisableRecompilation());

        ModDevRunWorkflow.create(
                project,
                Branding.MDG,
                artifacts,
                extension.getRuns());
    }

    /**
     * Downloads the Gradle Module Metadata (.module file) for the given NeoForge version
     * directly via HTTP to discover which game library modules it transitively depends on.
     * Registers them in {@link NeoForgedRepositoryFilter} and then applies the content
     * filter to the NeoForge repository.
     * <p>
     * The HTTP download bypasses Gradle's dependency resolution so the repository content
     * descriptor stays unlocked and can receive its first {@code content()} call.
     */
    private static void populateNeoForgeRepositoryFilter(Project project, String neoForgeVersion) {
        // Regex to extract group:module pairs from Gradle Module Metadata JSON.
        var depPattern = Pattern.compile("\"group\":\\s*\"([^\"]+)\",\\s*\"module\":\\s*\"([^\"]+)\"");

        // 1. Discover game library modules from the NeoForge artifact metadata.
        fetchModuleDependencies("net/neoforged/neoforge/" + neoForgeVersion
                + "/neoforge-" + neoForgeVersion + ".module", depPattern);

        // 2. Discover build tool modules from the NeoForm Runtime metadata.
        //    NFRT ships external tools (DiffPatch, AutoRenamingTool, etc.) whose
        //    transitive dependencies are also rehosted on the NeoForged Maven.
        var nfrtVersion = NeoFormRuntimeExtension.getVersion(project);
        fetchModuleDependencies("net/neoforged/neoform-runtime/" + nfrtVersion
                + "/neoform-runtime-" + nfrtVersion + ".module", depPattern);

        // Apply the content filter now — before any dependency resolution uses the
        // NeoForge repository.
        try {
            RepositoriesPlugin.applyContentFilter(project);
        } catch (Exception e) {
            LOG.warn("Failed to apply NeoForge repository content filter: {}", e.getMessage());
        }
    }

    private static void fetchModuleDependencies(String path, Pattern depPattern) {
        try {
            var moduleUrl = URI.create(
                    "https://maven.neoforged.net/releases/" + path).toURL();

            try (var stream = moduleUrl.openStream()) {
                var content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                var matcher = depPattern.matcher(content);
                while (matcher.find()) {
                    var group = matcher.group(1);
                    var module = matcher.group(2);
                    if ("*".equals(group) || "*".equals(module)) {
                        continue;
                    }
                    NeoForgedRepositoryFilter.addGameLibrary(group, module);
                }
            }
        } catch (Exception e) {
            LOG.warn(
                    "Failed to discover dependencies from {}: {}",
                    path, e.getMessage());
        }
    }
}
