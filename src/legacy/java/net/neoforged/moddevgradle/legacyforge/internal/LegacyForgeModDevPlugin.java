package net.neoforged.moddevgradle.legacyforge.internal;

import net.neoforged.moddevgradle.dsl.NeoForgeExtension;
import net.neoforged.moddevgradle.internal.LegacyForgeFacade;
import net.neoforged.moddevgradle.internal.ModDevPlugin;
import net.neoforged.moddevgradle.legacyforge.dsl.MixinExtension;
import net.neoforged.moddevgradle.legacyforge.dsl.Obfuscation;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.jvm.tasks.Jar;
import org.jetbrains.annotations.ApiStatus;

import java.net.URI;
import java.util.Map;
import java.util.stream.Stream;

@ApiStatus.Internal
public class LegacyForgeModDevPlugin implements Plugin<Project> {
    public static final Attribute<Boolean> REMAPPED = Attribute.of("net.neoforged.moddevgradle.legacy.remapped", Boolean.class);

    public static final String CONFIGURATION_TOOL_ART = "autoRenamingToolRuntime";
    public static final String CONFIGURATION_TOOL_INSTALLERTOOLS = "installerToolsRuntime";

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(ModDevPlugin.class);

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

        // We use this directory to store intermediate files used during moddev
        var modDevBuildDir = project.getLayout().getBuildDirectory().dir("moddev");
        var namedToIntermediate = modDevBuildDir.map(d -> d.file("namedToIntermediate.tsrg"));
        var intermediateToNamed = modDevBuildDir.map(d -> d.file("intermediateToNamed.srg"));
        var mappingsCsv = modDevBuildDir.map(d -> d.file("intermediateToNamed.zip"));

        // This collection is used to share the files added by mixin with the obfuscation extension
        var extraMixinMappings = project.files();

        var obf = project.getExtensions().create("obfuscation", Obfuscation.class, project, namedToIntermediate, mappingsCsv, autoRenamingToolRuntime, installerToolsRuntime, extraMixinMappings);
        var mixin = project.getExtensions().create("mixin", MixinExtension.class, project, namedToIntermediate, extraMixinMappings);

        project.getExtensions().configure(NeoForgeExtension.class, extension -> {
            extension.getNeoForgeArtifact().set(extension.getVersion().map(version -> "net.minecraftforge:forge:" + version));
            extension.getNeoFormArtifact().set(extension.getNeoFormVersion().map(version -> "de.oceanlabs.mcp:mcp_config:" + version));

            extension.getAdditionalMinecraftArtifacts().put("namedToIntermediaryMapping", namedToIntermediate.map(RegularFile::getAsFile));
            extension.getAdditionalMinecraftArtifacts().put("intermediaryToNamedMapping", intermediateToNamed.map(RegularFile::getAsFile));
            extension.getAdditionalMinecraftArtifacts().put("csvMapping", mappingsCsv.map(RegularFile::getAsFile));

            extension.getRuns().configureEach(run -> {
                LegacyForgeFacade.configureRun(project, run);

                // Mixin needs the intermediate (SRG) -> named (Mojang, MCP) mapping file in SRG (TSRG is not supported) to be able to ignore the refmaps of dependencies
                run.getSystemProperties().put("mixin.env.remapRefMap", "true");
                run.getSystemProperties().put("mixin.env.refMapRemappingFile", intermediateToNamed.map(f -> f.getAsFile().getAbsolutePath()));

                run.getProgramArguments().addAll(mixin.getConfigs().map(cfgs -> cfgs.stream().flatMap(config -> Stream.of("--mixin.config", config)).toList()));
            });
        });

        var reobfJar = obf.reobfuscate(
                project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class),
                project.getExtensions().getByType(SourceSetContainer.class).getByName(SourceSet.MAIN_SOURCE_SET_NAME),
                task -> {
                    task.getRemapOperation().getMappings().from(extraMixinMappings);
                }
        );

        project.getTasks().named("assemble", assemble -> assemble.dependsOn(reobfJar));

        // Forge expects the mapping csv files on the root classpath
        project.getConfigurations().getByName(ModDevPlugin.CONFIGURATION_RUNTIME_DEPENDENCIES)
                .getDependencies().add(project.getDependencyFactory().create(project.files(mappingsCsv)));

        // Forge expects to find the Forge and client-extra jar on the legacy classpath
        // Newer FML versions also search for it on the java.class.path.
        // MDG already adds cilent-extra, but the forge jar is missing.
        project.getConfigurations().getByName("additionalRuntimeClasspath")
                .extendsFrom(project.getConfigurations().getByName(ModDevPlugin.CONFIGURATION_RUNTIME_DEPENDENCIES))
                .exclude(Map.of("group", "net.neoforged", "module", "DevLaunch"));

        project.getDependencies().attributesSchema(schema -> schema.attribute(REMAPPED));
        project.getDependencies().getArtifactTypes().named("jar", a -> a.getAttributes().attribute(REMAPPED, false));

        var remapDeps = project.getConfigurations().create("remappingDependencies", spec -> {
            spec.setDescription("An internal configuration that contains the Minecraft dependencies, used for remapping mods");
            spec.setCanBeConsumed(false);
            spec.setCanBeDeclared(false);
            spec.setCanBeResolved(true);
            spec.extendsFrom(project.getConfigurations().getByName(ModDevPlugin.CONFIGURATION_RUNTIME_DEPENDENCIES));
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

        obf.createRemappingConfiguration(project.getConfigurations().getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME));
        obf.createRemappingConfiguration(project.getConfigurations().getByName(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME));
        obf.createRemappingConfiguration(project.getConfigurations().getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME));
        obf.createRemappingConfiguration(project.getConfigurations().getByName(JavaPlugin.API_CONFIGURATION_NAME));
        obf.createRemappingConfiguration(project.getConfigurations().getByName(JavaPlugin.COMPILE_ONLY_API_CONFIGURATION_NAME));
    }
}
