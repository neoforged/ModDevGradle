package net.neoforged.moddevgradle.internal;

import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
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
import org.jetbrains.annotations.Nullable;
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

        // Always apply at least the stable baseline filter to the NeoForged
        // repository. When a NeoForge version is selected we also discover
        // additional game-library modules from its metadata.
        populateNeoForgeRepositoryFilter(project, neoForgeVersion);

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

    // 10-second timeout for the .module HTTP requests (both connect and read).
    private static final int MODULE_FETCH_TIMEOUT_MS = 10_000;

    /**
     * Discovers additional modules from the Gradle Module Metadata of the selected
     * NeoForge version (if any) and the NeoForm Runtime, then applies the content
     * filter to the NeoForge repository.
     * <p>
     * When the NeoForged repository was configured at the settings level there is
     * no project-scoped repository to apply the filter to — this method returns
     * early without touching the network.
     * <p>
     * Dynamic modules are collected in a local set and passed directly to
     * {@link NeoForgedRepositoryFilter#filter} — no static mutable state is shared
     * across projects, which is safe with parallel project configuration.
     */
    private static void populateNeoForgeRepositoryFilter(Project project,
            @Nullable String neoForgeVersion) {
        // When the repository is configured at the settings level there is no
        // project-scoped repository to install the filter on; the settings-level
        // filter is already in place and cannot be augmented per-project.
        if (RepositoriesPlugin.getNeoForgeRepository(project) == null) {
            return;
        }

        var dynamicModules = new HashSet<String>();

        if (neoForgeVersion != null) {
            var depPattern = Pattern.compile("\"group\":\\s*\"([^\"]+)\",\\s*\"module\":\\s*\"([^\"]+)\"");
            fetchModuleDependencies(project, "net/neoforged/neoforge/" + neoForgeVersion
                    + "/neoforge-" + neoForgeVersion + ".module", depPattern, dynamicModules);
            // NFRT metadata fetch uses the same pattern.
            try {
                var nfrtVersion = NeoFormRuntimeExtension.getVersion(project);
                fetchModuleDependencies(project, "net/neoforged/neoform-runtime/" + nfrtVersion
                        + "/neoform-runtime-" + nfrtVersion + ".module", depPattern, dynamicModules);
            } catch (Exception e) {
                LOG.warn("Failed to resolve NFRT version for dynamic filter discovery: {}", e.getMessage());
            }
        }

        // Apply the content filter now — before any dependency resolution uses
        // the NeoForge repository. Each enable() call passes its own local set,
        // so parallel project configuration is safe.
        RepositoriesPlugin.applyContentFilter(project, dynamicModules);
    }

    /**
     * Downloads a single {@code .module} file from the NeoForged Maven and extracts
     * {@code group:module} pairs from it.
     * <p>
     * Uses {@link URLConnection} directly rather than Gradle dependency resolution
     * because Gradle locks the repository content descriptor on first use, which
     * would prevent us from installing the content filter afterward. Respects
     * {@code --offline} and applies explicit connect/read timeouts.
     */
    private static void fetchModuleDependencies(Project project, String path,
            Pattern depPattern, Set<String> dynamicModules) {
        if (project.getGradle().getStartParameter().isOffline()) {
            return;
        }

        try {
            var moduleUrl = URI.create(
                    "https://maven.neoforged.net/releases/" + path).toURL();

            var connection = moduleUrl.openConnection();
            connection.setConnectTimeout(MODULE_FETCH_TIMEOUT_MS);
            connection.setReadTimeout(MODULE_FETCH_TIMEOUT_MS);

            try (var stream = connection.getInputStream()) {
                var content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                var matcher = depPattern.matcher(content);
                while (matcher.find()) {
                    var group = matcher.group(1);
                    var module = matcher.group(2);
                    if ("*".equals(group) || "*".equals(module)) {
                        continue;
                    }
                    dynamicModules.add(group + ":" + module);
                }
            }
        } catch (Exception e) {
            LOG.warn(
                    "Failed to discover dependencies from {}: {}",
                    path, e.getMessage());
        }
    }
}
