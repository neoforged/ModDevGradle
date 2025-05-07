package net.neoforged.moddevgradle.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.neoforged.minecraftdependencies.MinecraftDistribution;
import net.neoforged.moddevgradle.dsl.ModDevExtension;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import net.neoforged.moddevgradle.internal.utils.VersionCapabilitiesInternal;
import net.neoforged.nfrtgradle.CreateMinecraftArtifacts;
import net.neoforged.nfrtgradle.DownloadAssets;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * The workflow needed to produce artifacts and assets for compiling and running a mod.
 */
@ApiStatus.Internal
public record ModDevArtifactsWorkflow(
        Project project,
        ModdingDependencies dependencies,
        VersionCapabilitiesInternal versionCapabilities,
        TaskProvider<CreateMinecraftArtifacts> createArtifacts,
        Provider<? extends Dependency> minecraftClassesDependency,
        TaskProvider<DownloadAssets> downloadAssets,
        Configuration runtimeDependencies,
        Configuration compileDependencies,
        Provider<Directory> modDevBuildDir,
        Provider<Directory> artifactsBuildDir) {

    private static final String EXTENSION_NAME = "__internal_modDevArtifactsWorkflow";
    public static ModDevArtifactsWorkflow get(Project project) {
        var result = ExtensionUtils.findExtension(project, EXTENSION_NAME, ModDevArtifactsWorkflow.class);
        if (result == null) {
            throw new InvalidUserCodeException("Mod development has not been enabled yet for project " + project);
        }
        return result;
    }

    public static ModDevArtifactsWorkflow create(Project project,
            Collection<SourceSet> enabledSourceSets,
            Branding branding,
            ModDevExtension extension,
            ModdingDependencies moddingDependencies,
            ArtifactNamingStrategy artifactNamingStrategy,
            Configuration accessTransformers,
            Configuration interfaceInjectionData,
            VersionCapabilitiesInternal versionCapabilities) {
        if (project.getExtensions().findByName(EXTENSION_NAME) != null) {
            throw new InvalidUserCodeException("You cannot enable modding in the same project twice.");
        }

        var ideIntegration = IdeIntegration.of(project, branding);

        // We use this directory to store intermediate files used during moddev
        var modDevBuildDir = project.getLayout().getBuildDirectory().dir("moddev");
        var artifactsBuildDir = project.getLayout().getBuildDirectory().dir("moddev/artifacts");

        var createManifestConfigurations = configureArtifactManifestConfigurations(
                project,
                moddingDependencies.neoForgeDependency(),
                moddingDependencies.neoFormDependency());

        var dependencyFactory = project.getDependencyFactory();
        var configurations = project.getConfigurations();
        var tasks = project.getTasks();
        var javaExtension = ExtensionUtils.getExtension(project, "java", JavaPluginExtension.class);

        // Users can theoretically compile their mods at higher java versions than used by Minecraft,
        // but it's more important to default the common user to the right Java version.
        var javaToolchainService = ExtensionUtils.getExtension(project, "javaToolchains", JavaToolchainService.class);

        // Try to give people at least a fighting chance to run on the correct java version
        var toolchainSpec = javaExtension.getToolchain();
        try {
            toolchainSpec.getLanguageVersion().convention(JavaLanguageVersion.of(versionCapabilities.javaVersion()));
        } catch (IllegalStateException e) {
            // We tried our best, but the java version was already resolved and is thus finalized
            // this can occur if any dependency resolution happens since it reads this version for the attributes
        }

        var parchment = extension.getParchment();
        var parchmentData = configurations.create("parchmentData", spec -> {
            spec.setDescription("Data used to add parameter names and javadoc to Minecraft sources");
            spec.setCanBeResolved(true);
            spec.setCanBeConsumed(false);
            spec.setTransitive(false); // Expect a single result
            spec.getDependencies().addLater(parchment.getParchmentArtifact().map(dependencyFactory::create));
        });

        // it has to contain client-extra to be loaded by FML, and it must be added to the legacy CP
        var createArtifacts = tasks.register("createMinecraftArtifacts", CreateMinecraftArtifacts.class, task -> {
            task.setGroup(branding.internalTaskGroup());
            task.setDescription("Creates the NeoForge and Minecraft artifacts by invoking NFRT.");
            for (var configuration : createManifestConfigurations) {
                task.addArtifactsToManifest(configuration);
            }

            // NFRT needs access to a JDK of the right version to be able to correctly decompile and recompile the code
            task.getToolsJavaExecutable().set(javaToolchainService
                    .launcherFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(versionCapabilities.javaVersion())))
                    .map(javaLauncher -> javaLauncher.getExecutablePath().getAsFile().getAbsolutePath()));

            task.getAccessTransformers().from(accessTransformers);
            // If AT validation is enabled, add the user-supplied AT paths as files to be validated,
            // they're also part of the normal AT collection, so if AT validation is disabled, just return an empty list.
            task.getValidatedAccessTransformers().from(
                    extension.getValidateAccessTransformers().map(validate -> {
                        if (validate) {
                            return extension.getAccessTransformers().getFiles();
                        } else {
                            return project.files();
                        }
                    }));
            task.getInterfaceInjectionData().from(interfaceInjectionData);
            task.getParchmentData().from(parchmentData);
            task.getParchmentEnabled().set(parchment.getEnabled());
            task.getParchmentConflictResolutionPrefix().set(parchment.getConflictResolutionPrefix());

            Function<WorkflowArtifact, Provider<RegularFile>> artifactPathStrategy = artifact -> artifactsBuildDir.map(dir -> dir.file(artifactNamingStrategy.getFilename(artifact)));

            task.getCompiledArtifact().set(artifactPathStrategy.apply(WorkflowArtifact.COMPILED));
            task.getCompiledWithSourcesArtifact().set(artifactPathStrategy.apply(WorkflowArtifact.COMPILED_WITH_SOURCES));
            task.getSourcesArtifact().set(artifactPathStrategy.apply(WorkflowArtifact.SOURCES));
            task.getResourcesArtifact().set(artifactPathStrategy.apply(WorkflowArtifact.CLIENT_RESOURCES));

            task.getNeoForgeArtifact().set(moddingDependencies.neoForgeDependencyNotation());
            task.getNeoFormArtifact().set(moddingDependencies.neoFormDependencyNotation());
            task.getAdditionalResults().putAll(extension.getAdditionalMinecraftArtifacts());
        });
        ideIntegration.runTaskOnProjectSync(createArtifacts);

        var downloadAssets = tasks.register("downloadAssets", DownloadAssets.class, task -> {
            // Not in the internal group in case someone wants to "preload" the asset before they go offline
            task.setGroup(branding.publicTaskGroup());
            task.setDescription("Downloads the Minecraft assets and asset index needed to run a Minecraft client or generate client-side resources.");
            // While downloadAssets does not require *all* of the dependencies, it does need NeoForge/NeoForm to benefit
            // from any caching/overrides applied to these dependencies in Gradle
            for (var configuration : createManifestConfigurations) {
                task.addArtifactsToManifest(configuration);
            }
            task.getAssetPropertiesFile().set(modDevBuildDir.map(dir -> dir.file("minecraft_assets.properties")));
            task.getNeoForgeArtifact().set(moddingDependencies.neoForgeDependencyNotation());
            task.getNeoFormArtifact().set(moddingDependencies.neoFormDependencyNotation());
        });

        // For IntelliJ, we attach a combined sources+classes artifact which enables an "Attach Sources..." link for IJ users
        // Otherwise, attaching sources is a pain for IJ users.
        Provider<? extends Dependency> minecraftClassesDependency;
        if (ideIntegration.shouldUseCombinedSourcesAndClassesArtifact()) {
            minecraftClassesDependency = createArtifacts.map(task -> project.files(task.getCompiledWithSourcesArtifact())).map(dependencyFactory::create);
        } else {
            minecraftClassesDependency = createArtifacts.map(task -> project.files(task.getCompiledArtifact())).map(dependencyFactory::create);
        }

        // Name of the configuration in which we place the required dependencies to develop mods for use in the runtime-classpath.
        // We cannot use "runtimeOnly", since the contents of that are published.
        var runtimeDependencies = configurations.create("modDevRuntimeDependencies", config -> {
            config.setDescription("The runtime dependencies to develop a mod for, including Minecraft classes and modding platform classes.");
            config.setCanBeResolved(false);
            config.setCanBeConsumed(false);

            config.getDependencies().addLater(minecraftClassesDependency);
            config.getDependencies().addLater(createArtifacts.map(task -> project.files(task.getResourcesArtifact())).map(dependencyFactory::create));
            // Technically, the Minecraft dependencies do not strictly need to be on the classpath because they are pulled from the legacy class path.
            // However, we do it anyway because this matches production environments, and allows launch proxies such as DevLogin to use Minecraft's libraries.
            config.getDependencies().add(moddingDependencies.gameLibrariesDependency());
        });

        // Configuration in which we place the required dependencies to develop mods for use in the compile-classpath.
        // While compile only is not published, we also use a configuration here to be consistent.
        var compileDependencies = configurations.create("modDevCompileDependencies", config -> {
            config.setDescription("The compile-time dependencies to develop a mod, including Minecraft and modding platform classes.");
            config.setCanBeResolved(false);
            config.setCanBeConsumed(false);
            config.getDependencies().addLater(minecraftClassesDependency);
            config.getDependencies().add(moddingDependencies.gameLibrariesDependency());
        });

        // For IDEs that support it, link the source/binary artifacts if we use separated ones
        if (!ideIntegration.shouldUseCombinedSourcesAndClassesArtifact()) {
            ideIntegration.attachSources(
                    Map.of(
                            createArtifacts.get().getCompiledArtifact(),
                            createArtifacts.get().getSourcesArtifact()));
        }

        var result = new ModDevArtifactsWorkflow(
                project,
                moddingDependencies,
                versionCapabilities,
                createArtifacts,
                minecraftClassesDependency,
                downloadAssets,
                runtimeDependencies,
                compileDependencies,
                modDevBuildDir,
                artifactsBuildDir);

        project.getExtensions().add(ModDevArtifactsWorkflow.class, EXTENSION_NAME, result);

        for (var sourceSets : enabledSourceSets) {
            result.addToSourceSet(sourceSets);
        }

        return result;
    }

    /**
     * Collects all dependencies needed by the NeoFormRuntime
     */
    private static List<Configuration> configureArtifactManifestConfigurations(
            Project project,
            @Nullable ModuleDependency moddingPlatformDependency,
            @Nullable ModuleDependency recompilableMinecraftWorkflowDependency) {
        var configurations = project.getConfigurations();

        var configurationPrefix = "neoFormRuntimeDependencies";

        var result = new ArrayList<Configuration>();

        // Gradle prevents us from having dependencies with "incompatible attributes" in the same configuration.
        // What constitutes incompatible cannot be overridden on a per-configuration basis.
        var neoForgeClassesAndData = configurations.create(configurationPrefix + "NeoForgeClasses", spec -> {
            spec.setDescription("Dependencies needed for running NeoFormRuntime for the selected NeoForge/NeoForm version (NeoForge classes)");
            spec.setCanBeConsumed(false);
            spec.setCanBeResolved(true);
            if (moddingPlatformDependency != null) {
                spec.getDependencies().add(moddingPlatformDependency.copy()
                        .capabilities(caps -> caps.requireCapability("net.neoforged:neoforge-moddev-bundle")));
            }

            // This dependency is used when the NeoForm version is overridden or when we run in Vanilla-only mode
            if (recompilableMinecraftWorkflowDependency != null) {
                spec.getDependencies().add(recompilableMinecraftWorkflowDependency.copy()
                        .capabilities(caps -> caps.requireCapability("net.neoforged:neoform")));
            }
        });
        result.add(neoForgeClassesAndData);

        if (moddingPlatformDependency != null) {
            var neoForgeSources = configurations.create(configurationPrefix + "NeoForgeSources", spec -> {
                spec.setDescription("Dependencies needed for running NeoFormRuntime for the selected NeoForge/NeoForm version (NeoForge sources)");
                spec.setCanBeConsumed(false);
                spec.setCanBeResolved(true);
                spec.getDependencies().add(moddingPlatformDependency);
                spec.attributes(attributes -> {
                    setNamedAttribute(project, attributes, Category.CATEGORY_ATTRIBUTE, Category.DOCUMENTATION);
                    setNamedAttribute(project, attributes, DocsType.DOCS_TYPE_ATTRIBUTE, DocsType.SOURCES);
                });
            });
            result.add(neoForgeSources);
        }

        // Compile-time dependencies used by NeoForm, NeoForge and Minecraft.
        // Also includes any classes referenced by compiled Minecraft code (used by decompilers, renamers, etc.)
        var compileClasspath = configurations.create(configurationPrefix + "CompileClasspath", spec -> {
            spec.setDescription("Dependencies needed for running NeoFormRuntime for the selected NeoForge/NeoForm version (Classpath)");
            spec.setCanBeConsumed(false);
            spec.setCanBeResolved(true);
            if (moddingPlatformDependency != null) {
                spec.getDependencies().add(moddingPlatformDependency.copy()
                        .capabilities(caps -> caps.requireCapability("net.neoforged:neoforge-dependencies")));
            }
            if (recompilableMinecraftWorkflowDependency != null) {
                // This dependency is used when the NeoForm version is overridden or when we run in Vanilla-only mode
                spec.getDependencies().add(recompilableMinecraftWorkflowDependency.copy()
                        .capabilities(caps -> caps.requireCapability("net.neoforged:neoform-dependencies")));
            }
            spec.attributes(attributes -> {
                setNamedAttribute(project, attributes, Usage.USAGE_ATTRIBUTE, Usage.JAVA_API);
                setNamedAttribute(project, attributes, MinecraftDistribution.ATTRIBUTE, MinecraftDistribution.CLIENT);
            });
        });
        result.add(compileClasspath);

        // Runtime-time dependencies used by NeoForm, NeoForge and Minecraft.
        var runtimeClasspath = configurations.create(configurationPrefix + "RuntimeClasspath", spec -> {
            spec.setDescription("Dependencies needed for running NeoFormRuntime for the selected NeoForge/NeoForm version (Classpath)");
            spec.setCanBeConsumed(false);
            spec.setCanBeResolved(true);
            if (moddingPlatformDependency != null) {
                spec.getDependencies().add(moddingPlatformDependency); // Universal Jar
                spec.getDependencies().add(moddingPlatformDependency.copy()
                        .capabilities(caps -> caps.requireCapability("net.neoforged:neoforge-dependencies")));
            }
            // This dependency is used when the NeoForm version is overridden or when we run in Vanilla-only mode
            if (recompilableMinecraftWorkflowDependency != null) {
                spec.getDependencies().add(recompilableMinecraftWorkflowDependency.copy()
                        .capabilities(caps -> caps.requireCapability("net.neoforged:neoform-dependencies")));
            }
            spec.attributes(attributes -> {
                setNamedAttribute(project, attributes, Usage.USAGE_ATTRIBUTE, Usage.JAVA_RUNTIME);
                setNamedAttribute(project, attributes, MinecraftDistribution.ATTRIBUTE, MinecraftDistribution.CLIENT);
            });
        });
        result.add(runtimeClasspath);

        return result;
    }

    /**
     * Adds the compile-time and runtime-dependencies needed to compile mod code to the source-set of the given name.
     */
    public void addToSourceSet(SourceSet sourceSet) {
        var configurations = project.getConfigurations();
        var sourceSets = ExtensionUtils.getSourceSets(project);
        if (!sourceSets.contains(sourceSet)) {
            throw new GradleException("Cannot add to the source set in another project: " + sourceSet);
        }

        configurations.getByName(sourceSet.getRuntimeClasspathConfigurationName()).extendsFrom(runtimeDependencies);
        configurations.getByName(sourceSet.getCompileClasspathConfigurationName()).extendsFrom(compileDependencies);
    }

    public Provider<RegularFile> requestAdditionalMinecraftArtifact(String id, String filename) {
        return requestAdditionalMinecraftArtifact(id, artifactsBuildDir.map(dir -> dir.file(filename)));
    }

    public Provider<RegularFile> requestAdditionalMinecraftArtifact(String id, Provider<RegularFile> path) {
        createArtifacts.configure(task -> task.getAdditionalResults().put(id, path.map(RegularFile::getAsFile)));
        return project.getLayout().file(
                createArtifacts.flatMap(task -> task.getAdditionalResults().getting(id)));
    }

    private static <T extends Named> void setNamedAttribute(Project project, AttributeContainer attributes, Attribute<T> attribute, String value) {
        attributes.attribute(attribute, project.getObjects().named(attribute.getType(), value));
    }
}
