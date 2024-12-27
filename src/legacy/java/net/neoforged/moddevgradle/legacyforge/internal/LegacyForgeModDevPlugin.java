package net.neoforged.moddevgradle.legacyforge.internal;

import net.neoforged.minecraftdependencies.MinecraftDependenciesPlugin;
import net.neoforged.moddevgradle.internal.ArtifactNamingStrategy;
import net.neoforged.moddevgradle.internal.Branding;
import net.neoforged.moddevgradle.internal.DataFileCollectionFactory;
import net.neoforged.moddevgradle.internal.jarjar.JarJarPlugin;
import net.neoforged.moddevgradle.internal.LegacyForgeFacade;
import net.neoforged.moddevgradle.internal.ModDevArtifactsWorkflow;
import net.neoforged.moddevgradle.internal.ModDevRunWorkflow;
import net.neoforged.moddevgradle.internal.RepositoriesPlugin;
import net.neoforged.moddevgradle.internal.WorkflowArtifact;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import net.neoforged.moddevgradle.internal.utils.VersionCapabilities;
import net.neoforged.moddevgradle.legacyforge.dsl.LegacyForgeExtension;
import net.neoforged.moddevgradle.legacyforge.dsl.LegacyForgeModdingSettings;
import net.neoforged.moddevgradle.legacyforge.dsl.MixinExtension;
import net.neoforged.moddevgradle.legacyforge.dsl.ObfuscationExtension;
import net.neoforged.nfrtgradle.NeoFormRuntimePlugin;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.jvm.tasks.Jar;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;
import java.util.stream.Stream;

@ApiStatus.Internal
public class LegacyForgeModDevPlugin implements Plugin<Project> {
    private static final Logger LOG = LoggerFactory.getLogger(LegacyForgeModDevPlugin.class);

    public static final String MIXIN_EXTENSION = "mixin";
    public static final String OBFUSCATION_EXTENSION = "obfuscation";
    public static final String LEGACYFORGE_EXTENSION = "legacyForge";

    public static final Attribute<Boolean> REMAPPED = Attribute.of("net.neoforged.moddevgradle.legacy.remapped", Boolean.class);

    public static final String CONFIGURATION_TOOL_ART = "autoRenamingToolRuntime";
    public static final String CONFIGURATION_TOOL_INSTALLERTOOLS = "installerToolsRuntime";

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(JavaLibraryPlugin.class);
        project.getPlugins().apply(NeoFormRuntimePlugin.class);
        project.getPlugins().apply(MinecraftDependenciesPlugin.class);
        project.getPlugins().apply(JarJarPlugin.class);

        // TODO: Introduce a LegacyRepositoryPLugin to still allow repo management in settings.gradle
        // Do not apply the repositories automatically if they have been applied at the settings-level.
        // It's still possible to apply them manually, though.
        if (!project.getGradle().getPlugins().hasPlugin(RepositoriesPlugin.class)) {
            project.getPlugins().apply(RepositoriesPlugin.class);
        } else {
            LOG.info("Not enabling NeoForged repositories since they were applied at the settings level");
        }

        project.getRepositories().maven(repo -> {
            repo.setName("MinecraftForge");
            repo.setUrl(URI.create("https://maven.minecraftforge.net/"));
        });

        // This module is for supporting NeoForge 1.20.1, which is technically the same as Legacy Forge 1.20.1
        project.getDependencies().getComponents().withModule("net.neoforged:forge", LegacyForgeMetadataTransform.class);
        project.getDependencies().getComponents().withModule("net.minecraftforge:forge", LegacyForgeMetadataTransform.class);
        project.getDependencies().getComponents().withModule("de.oceanlabs.mcp:mcp_config", McpMetadataTransform.class);
        // For legacy versions we need to relax the strict version requirements imposed by the minecraft-dependencies,
        // since Forge upgrades especially log4j2, but we have no way of fixing its metadata fully (besides doing it statically).
        project.getDependencies().getComponents().withModule("net.neoforged:minecraft-dependencies", NonStrictDependencyTransform.class);

        var depFactory = project.getDependencyFactory();
        var autoRenamingToolRuntime = project.getConfigurations().create(CONFIGURATION_TOOL_ART, spec -> {
            spec.setDescription("The AutoRenamingTool CLI tool");
            spec.setCanBeConsumed(false);
            spec.setCanBeResolved(true);
            spec.setTransitive(false);
            spec.getDependencies().add(depFactory.create("net.neoforged:AutoRenamingTool:2.0.4:all"));
        });
        var installerToolsRuntime = project.getConfigurations().create(CONFIGURATION_TOOL_INSTALLERTOOLS, spec -> {
            spec.setDescription("The InstallerTools CLI tool");
            spec.setCanBeConsumed(false);
            spec.setCanBeResolved(true);
            spec.setTransitive(false);
            spec.getDependencies().add(depFactory.create("net.neoforged.installertools:installertools:2.1.10:fatjar"));
        });

        // This collection is used to share the files added by mixin with the obfuscation extension
        var extraMixinMappings = project.files();
        var obf = project.getExtensions().create(OBFUSCATION_EXTENSION, ObfuscationExtension.class, project, autoRenamingToolRuntime, installerToolsRuntime, extraMixinMappings);
        project.getExtensions().create(MIXIN_EXTENSION, MixinExtension.class, project, obf.getNamedToSrgMappings(), extraMixinMappings);

        configureDependencyRemapping(project, obf);

        var dataFileCollections = DataFileCollectionFactory.createDefault(project);
        project.getExtensions().create(
                LEGACYFORGE_EXTENSION,
                LegacyForgeExtension.class,
                project,
                dataFileCollections.accessTransformers().extension(),
                dataFileCollections.interfaceInjectionData().extension()
        );
    }

    public void enable(Project project, LegacyForgeModdingSettings settings, LegacyForgeExtension extension) {
        var depFactory = project.getDependencyFactory();

        var forgeVersion = settings.getForgeVersion();
        var neoForgeVersion = settings.getNeoForgeVersion();
        var mcpVersion = settings.getMcpVersion();

        ModuleDependency platformModule = null;
        ModuleDependency recompilableMinecraftWorkflowDependency = null;
        String recompilableMinecraftDataDependencyNotation = null;
        ModuleDependency modulePathDependency = null;
        ModuleDependency runTypesDataDependency = null;
        ModuleDependency librariesDependency;
        String moddingPlatformDataDependencyNotation = null;
        ArtifactNamingStrategy artifactNamingStrategy;
        VersionCapabilities versionCapabilities;
        if (forgeVersion != null || neoForgeVersion != null) {
            // All settings are mutually exclusive
            if (forgeVersion != null && neoForgeVersion != null || mcpVersion != null) {
                throw new InvalidUserCodeException("Specifying a Forge version is mutually exclusive with NeoForge or MCP");
            }

            String groupId = forgeVersion != null ? "net.minecraftforge" : "net.neoforged";

            platformModule = depFactory.create(groupId + ":forge:" + forgeVersion);
            moddingPlatformDataDependencyNotation = groupId + ":forge:" + forgeVersion + ":userdev";
            runTypesDataDependency = platformModule.copy()
                    .capabilities(caps -> caps.requireCapability("net.neoforged:neoforge-moddev-config"));
            modulePathDependency = platformModule.copy()
                    .capabilities(caps -> caps.requireCapability("net.neoforged:neoforge-moddev-module-path"))
                    // TODO: this is ugly; maybe make the configuration transitive in neoforge, or fix the SJH dep.
                    .exclude(Map.of("group", "org.jetbrains", "module", "annotations"));
            librariesDependency = platformModule.copy()
                    .capabilities(c -> c.requireCapability("net.neoforged:neoforge-dependencies"));

            var artifactPrefix = "forge-" + forgeVersion;
            // We have to ensure that client resources are named "client-extra" and *do not* contain forge-<version>
            // otherwise FML might pick up the client resources as the main Minecraft jar.
            artifactNamingStrategy = (artifact) -> {
                if (artifact == WorkflowArtifact.CLIENT_RESOURCES) {
                    return "client-extra-" + forgeVersion + ".jar";
                } else {
                    return artifactPrefix + artifact.defaultSuffix + ".jar";
                }
            };

            versionCapabilities = VersionCapabilities.ofForgeVersion(forgeVersion);
        } else if (mcpVersion != null) {
            recompilableMinecraftDataDependencyNotation = "de.oceanlabs.mcp:mcp_config:" + mcpVersion + "@zip";
            recompilableMinecraftWorkflowDependency = depFactory.create("de.oceanlabs.mcp:mcp_config:" + mcpVersion);
            librariesDependency = recompilableMinecraftWorkflowDependency.copy()
                    .capabilities(c -> c.requireCapability("net.neoforged:neoform-dependencies"));
            artifactNamingStrategy = ArtifactNamingStrategy.createDefault("vanilla-" + mcpVersion);

            versionCapabilities = VersionCapabilities.ofMinecraftVersion(mcpVersion);
        } else {
            throw new InvalidUserCodeException("You must specify a Forge, NeoForge or MCP version");
        }

        var configurations = project.getConfigurations();

        var artifacts = ModDevArtifactsWorkflow.create(
                project,
                Branding.MDG,
                extension,
                platformModule,
                moddingPlatformDataDependencyNotation,
                recompilableMinecraftWorkflowDependency,
                recompilableMinecraftDataDependencyNotation,
                librariesDependency,
                artifactNamingStrategy,
                configurations.getByName(DataFileCollectionFactory.CONFIGURATION_ACCESS_TRANSFORMERS),
                configurations.getByName(DataFileCollectionFactory.CONFIGURATION_INTERFACE_INJECTION_DATA),
                versionCapabilities
        );

        var runs = ModDevRunWorkflow.create(
                project,
                Branding.MDG,
                artifacts,
                modulePathDependency,
                runTypesDataDependency,
                null /* no support for test fixtures */,
                librariesDependency,
                extension.getRuns(),
                versionCapabilities
        );

        for (var sourceSet : settings.getEnabledSourceSets().get()) {
            artifacts.addToSourceSet(sourceSet.getName());
        }

        // Configure the mixin and obfuscation extensions
        var mixin = ExtensionUtils.getExtension(project, MIXIN_EXTENSION, MixinExtension.class);
        var obf = ExtensionUtils.getExtension(project, OBFUSCATION_EXTENSION, ObfuscationExtension.class);

        // We use this directory to store intermediate files used during moddev
        var namedToIntermediate = artifacts.requestAdditionalMinecraftArtifact("namedToIntermediaryMapping", "namedToIntermediate.tsrg");
        obf.getNamedToSrgMappings().set(namedToIntermediate);
        var intermediateToNamed = artifacts.requestAdditionalMinecraftArtifact("intermediaryToNamedMapping", "intermediateToNamed.srg");
        var mappingsCsv = artifacts.requestAdditionalMinecraftArtifact("csvMapping", "intermediateToNamed.zip");
        obf.getSrgToNamedMappings().set(mappingsCsv);

        extension.getRuns().configureEach(run -> {
            LegacyForgeFacade.configureRun(project, run);

            // Mixin needs the intermediate (SRG) -> named (Mojang, MCP) mapping file in SRG (TSRG is not supported) to be able to ignore the refmaps of dependencies
            run.getSystemProperties().put("mixin.env.remapRefMap", "true");
            run.getSystemProperties().put("mixin.env.refMapRemappingFile", intermediateToNamed.map(f -> f.getAsFile().getAbsolutePath()));

            run.getProgramArguments().addAll(mixin.getConfigs().map(cfgs -> cfgs.stream().flatMap(config -> Stream.of("--mixin.config", config)).toList()));
        });

        var reobfJar = obf.reobfuscate(
                project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class),
                project.getExtensions().getByType(SourceSetContainer.class).getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        );

        project.getTasks().named("assemble", assemble -> assemble.dependsOn(reobfJar));

        // Forge expects the mapping csv files on the root classpath
        artifacts.runtimeDependencies()
                .getDependencies().add(project.getDependencyFactory().create(project.files(mappingsCsv)));

        // Forge expects to find the Forge and client-extra jar on the legacy classpath
        // Newer FML versions also search for it on the java.class.path.
        // MDG already adds cilent-extra, but the forge jar is missing.
        runs.getAdditionalClasspath()
                .extendsFrom(artifacts.runtimeDependencies())
                .exclude(Map.of("group", "net.neoforged", "module", "DevLaunch"));

        var remapDeps = project.getConfigurations().create("remappingDependencies", spec -> {
            spec.setDescription("An internal configuration that contains the Minecraft dependencies, used for remapping mods");
            spec.setCanBeConsumed(false);
            spec.setCanBeDeclared(false);
            spec.setCanBeResolved(true);
            spec.extendsFrom(artifacts.runtimeDependencies());
        });

        project.getDependencies().registerTransform(RemappingTransform.class, params -> {
            params.parameters(parameters -> {
                obf.configureSrgToNamedOperation(parameters.getRemapOperation());
                parameters.getMinecraftDependencies().from(remapDeps);
            });
            params.getFrom()
                    .attribute(REMAPPED, false)
                    .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            params.getTo()
                    .attribute(REMAPPED, true)
                    .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
        });
    }

    private static void configureDependencyRemapping(Project project, ObfuscationExtension obf) {
        project.getDependencies().attributesSchema(schema -> schema.attribute(REMAPPED));
        project.getDependencies().getArtifactTypes().named("jar", a -> a.getAttributes().attribute(REMAPPED, false));

        obf.createRemappingConfiguration(project.getConfigurations().getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME));
        obf.createRemappingConfiguration(project.getConfigurations().getByName(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME));
        obf.createRemappingConfiguration(project.getConfigurations().getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME));
        obf.createRemappingConfiguration(project.getConfigurations().getByName(JavaPlugin.API_CONFIGURATION_NAME));
        obf.createRemappingConfiguration(project.getConfigurations().getByName(JavaPlugin.COMPILE_ONLY_API_CONFIGURATION_NAME));
    }
}
