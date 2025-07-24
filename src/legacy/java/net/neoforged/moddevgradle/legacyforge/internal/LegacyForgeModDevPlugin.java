package net.neoforged.moddevgradle.legacyforge.internal;

import java.util.stream.Stream;
import javax.inject.Inject;
import net.neoforged.minecraftdependencies.MinecraftDependenciesPlugin;
import net.neoforged.moddevgradle.internal.ArtifactNamingStrategy;
import net.neoforged.moddevgradle.internal.Branding;
import net.neoforged.moddevgradle.internal.DataFileCollections;
import net.neoforged.moddevgradle.internal.ModDevArtifactsWorkflow;
import net.neoforged.moddevgradle.internal.ModDevRunWorkflow;
import net.neoforged.moddevgradle.internal.ModdingDependencies;
import net.neoforged.moddevgradle.internal.jarjar.JarJarPlugin;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import net.neoforged.moddevgradle.internal.utils.VersionCapabilitiesInternal;
import net.neoforged.moddevgradle.legacyforge.dsl.LegacyForgeExtension;
import net.neoforged.moddevgradle.legacyforge.dsl.LegacyForgeModdingSettings;
import net.neoforged.moddevgradle.legacyforge.dsl.MixinExtension;
import net.neoforged.moddevgradle.legacyforge.dsl.ObfuscationExtension;
import net.neoforged.nfrtgradle.NeoFormRuntimePlugin;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.jvm.tasks.Jar;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApiStatus.Internal
public class LegacyForgeModDevPlugin implements Plugin<Project> {
    private static final Logger LOG = LoggerFactory.getLogger(LegacyForgeModDevPlugin.class);

    public static final String MIXIN_EXTENSION = "mixin";
    public static final String OBFUSCATION_EXTENSION = "obfuscation";
    public static final String LEGACYFORGE_EXTENSION = "legacyForge";

    public static final String CONFIGURATION_TOOL_ART = "autoRenamingToolRuntime";
    public static final String CONFIGURATION_TOOL_INSTALLERTOOLS = "installerToolsRuntime";

    private final MinecraftMappings namedMappings;
    private final MinecraftMappings srgMappings;

    @Inject
    public LegacyForgeModDevPlugin(ObjectFactory objectFactory) {
        namedMappings = objectFactory.named(MinecraftMappings.class, MinecraftMappings.NAMED);
        srgMappings = objectFactory.named(MinecraftMappings.class, MinecraftMappings.SRG);
    }

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(JavaLibraryPlugin.class);
        project.getPlugins().apply(NeoFormRuntimePlugin.class);
        project.getPlugins().apply(MinecraftDependenciesPlugin.class);
        project.getPlugins().apply(JarJarPlugin.class);

        // Do not apply the repositories automatically if they have been applied at the settings-level.
        // It's still possible to apply them manually, though.
        if (!project.getGradle().getPlugins().hasPlugin(LegacyRepositoriesPlugin.class)) {
            project.getPlugins().apply(LegacyRepositoriesPlugin.class);
        } else {
            LOG.info("Not enabling legacy repositories since they were applied at the settings level");
        }

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
            spec.getDependencies().add(depFactory.create("net.neoforged.installertools:installertools:3.0.4:fatjar"));
        });

        // This collection is used to share the files added by mixin with the obfuscation extension
        var extraMixinMappings = project.files();
        var obf = project.getExtensions().create(OBFUSCATION_EXTENSION, ObfuscationExtension.class, project, autoRenamingToolRuntime, installerToolsRuntime, extraMixinMappings);
        project.getExtensions().create(MIXIN_EXTENSION, MixinExtension.class, project, obf.getNamedToSrgMappings(), extraMixinMappings);

        configureDependencyRemapping(project, obf);

        var dataFileCollections = DataFileCollections.create(project);
        project.getExtensions().create(
                LEGACYFORGE_EXTENSION,
                LegacyForgeExtension.class,
                project,
                dataFileCollections.accessTransformers().extension(),
                dataFileCollections.interfaceInjectionData().extension());
    }

    public void enable(Project project, LegacyForgeModdingSettings settings, LegacyForgeExtension extension) {
        var depFactory = project.getDependencyFactory();

        var forgeVersion = settings.getForgeVersion();
        var neoForgeVersion = settings.getNeoForgeVersion();
        var mcpVersion = settings.getMcpVersion();

        ModdingDependencies dependencies;
        ArtifactNamingStrategy artifactNamingStrategy;
        VersionCapabilitiesInternal versionCapabilities;
        if (forgeVersion != null || neoForgeVersion != null) {
            // All settings are mutually exclusive
            if (forgeVersion != null && neoForgeVersion != null || mcpVersion != null) {
                throw new InvalidUserCodeException("Specifying a Forge version is mutually exclusive with NeoForge or MCP");
            }

            var version = forgeVersion != null ? forgeVersion : neoForgeVersion;
            versionCapabilities = VersionCapabilitiesInternal.ofForgeVersion(version);
            artifactNamingStrategy = ArtifactNamingStrategy.createNeoForge(versionCapabilities, "forge", version);

            String groupId = forgeVersion != null ? "net.minecraftforge" : "net.neoforged";
            var neoForge = depFactory.create(groupId + ":forge:" + version);
            var neoForgeNotation = groupId + ":forge:" + version + ":userdev";
            dependencies = ModdingDependencies.create(neoForge, neoForgeNotation, null, null, versionCapabilities);
        } else if (mcpVersion != null) {
            versionCapabilities = VersionCapabilitiesInternal.ofMinecraftVersion(mcpVersion);
            artifactNamingStrategy = ArtifactNamingStrategy.createVanilla(mcpVersion);

            var neoForm = depFactory.create("de.oceanlabs.mcp:mcp_config:" + mcpVersion);
            var neoFormNotation = "de.oceanlabs.mcp:mcp_config:" + mcpVersion + "@zip";
            dependencies = ModdingDependencies.createVanillaOnly(neoForm, neoFormNotation);
        } else {
            throw new InvalidUserCodeException("You must specify a Forge, NeoForge or MCP version");
        }

        var configurations = project.getConfigurations();

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

        var runs = ModDevRunWorkflow.create(
                project,
                Branding.MDG,
                artifacts,
                extension.getRuns());

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
            // Old BSL versions before 2022 (i.e. on 1.18.2) did not export any packages, causing DevLaunch to be unable to access the main method
            run.getJvmArguments().addAll("--add-exports", "cpw.mods.bootstraplauncher/cpw.mods.bootstraplauncher=ALL-UNNAMED");

            // Mixin needs the intermediate (SRG) -> named (Mojang, MCP) mapping file in SRG (TSRG is not supported) to be able to ignore the refmaps of dependencies
            run.getSystemProperties().put("mixin.env.remapRefMap", "true");
            run.getSystemProperties().put("mixin.env.refMapRemappingFile", intermediateToNamed.map(f -> f.getAsFile().getAbsolutePath()));

            run.getProgramArguments().addAll(mixin.getConfigs().map(cfgs -> cfgs.stream().flatMap(config -> Stream.of("--mixin.config", config)).toList()));
        });

        if (settings.isObfuscateJar()) {
            var reobfJar = obf.reobfuscate(
                    project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class),
                    project.getExtensions().getByType(SourceSetContainer.class).getByName(SourceSet.MAIN_SOURCE_SET_NAME));

            project.getTasks().named("assemble", assemble -> assemble.dependsOn(reobfJar));
        }

        // Forge expects the mapping csv files on the root classpath
        artifacts.runtimeDependencies()
                .getDependencies().add(project.getDependencyFactory().create(project.files(mappingsCsv)));

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
                    .attribute(MinecraftMappings.ATTRIBUTE, srgMappings)
                    .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            params.getTo()
                    .attribute(MinecraftMappings.ATTRIBUTE, namedMappings)
                    .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
        });
    }

    private void configureDependencyRemapping(Project project, ObfuscationExtension obf) {
        // JarJar cross-project dependencies are packaged into the final jar and should be remapped
        // We must however do this without affecting external dependencies since those are usually already in the
        // right namespace.
        // For cross-project dependencies, both the named (with the named attribute) and obfuscated (with no attribute)
        // variants are available. Requesting the srg attribute seemingly excludes the named variant from the selection.
        var sourceSets = ExtensionUtils.getSourceSets(project);
        sourceSets.all(sourceSet -> {
            var configurationName = sourceSet.getTaskName(null, "jarJar");
            project.getConfigurations().getByName(configurationName).withDependencies(dependencies -> {
                dependencies.forEach(dep -> {
                    if (dep instanceof ProjectDependency projectDependency) {
                        projectDependency.attributes(a -> {
                            a.attribute(MinecraftMappings.ATTRIBUTE, srgMappings);
                        });
                    }
                });
            });
        });

        project.getDependencies().attributesSchema(schema -> {
            var attr = schema.attribute(MinecraftMappings.ATTRIBUTE);
            // Add a disambiguation rule that will prefer named variants.
            // This is needed for cross-project dependencies: in that setting both the named (with the named attribute)
            // and obfuscated (with no attribute) variants are available, and we want to choose named by default.
            attr.getDisambiguationRules().add(MappingsDisambiguationRule.class, config -> {
                config.params(namedMappings);
            });
        });
        // Give every single jar the srg mappings attribute so it can be force-remapped by requesting named
        project.getDependencies().getArtifactTypes().named(ArtifactTypeDefinition.JAR_TYPE, type -> {
            type.getAttributes().attribute(MinecraftMappings.ATTRIBUTE, srgMappings);
        });

        obf.createRemappingConfiguration(project.getConfigurations().getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME));
        obf.createRemappingConfiguration(project.getConfigurations().getByName(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME));
        obf.createRemappingConfiguration(project.getConfigurations().getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME));
        obf.createRemappingConfiguration(project.getConfigurations().getByName(JavaPlugin.API_CONFIGURATION_NAME));
        obf.createRemappingConfiguration(project.getConfigurations().getByName(JavaPlugin.COMPILE_ONLY_API_CONFIGURATION_NAME));
    }
}
