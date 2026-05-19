package net.neoforged.moddevgradle.internal;

import java.net.URI;
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

    /**
     * Discovers additional modules from the Gradle Module Metadata of the selected
     * NeoForge version (if any) and the NeoForm Runtime, then applies the content
     * filter to the NeoForge repository.
     * <p>
     * Dynamic modules are collected in a local set and passed directly to
     * {@link NeoForgedRepositoryFilter#filter} — no static mutable state is shared
     * across projects, which is safe with parallel project configuration.
     * <p>
     * NFRT metadata is always fetched regardless of whether a NeoForge version was
     * selected, since NFRT can introduce new direct dependencies over time even in
     * vanilla-only mode.
     */
    private static void populateNeoForgeRepositoryFilter(Project project,
            @Nullable String neoForgeVersion) {
        var dynamicModules = new HashSet<String>();
        var depPattern = Pattern.compile("\"group\":\\s*\"([^\"]+)\",\\s*\"module\":\\s*\"([^\"]+)\"");

        if (neoForgeVersion != null) {
            // Discover game library modules from the NeoForge artifact metadata.
            fetchModuleDependencies("net/neoforged/neoforge/" + neoForgeVersion
                    + "/neoforge-" + neoForgeVersion + ".module", depPattern, dynamicModules);
        }

        // Always discover build tool modules from the NeoForm Runtime metadata.
        // NFRT ships external tools (DiffPatch, AutoRenamingTool, etc.) whose
        // transitive dependencies are rehosted on the NeoForged Maven and may
        // change across NFRT releases.
        try {
            var nfrtVersion = NeoFormRuntimeExtension.getVersion(project);
            fetchModuleDependencies("net/neoforged/neoform-runtime/" + nfrtVersion
                    + "/neoform-runtime-" + nfrtVersion + ".module", depPattern, dynamicModules);
        } catch (Exception e) {
            LOG.warn("Failed to resolve NFRT version for dynamic filter discovery: {}", e.getMessage());
        }

        // Apply the content filter now — before any dependency resolution uses
        // the NeoForge repository. Each enable() call passes its own local set,
        // so parallel project configuration is safe.
        RepositoriesPlugin.applyContentFilter(project, dynamicModules);
    }

    private static void fetchModuleDependencies(String path, Pattern depPattern,
            Set<String> dynamicModules) {
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
