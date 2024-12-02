package net.neoforged.moddevgradle.internal;

import net.neoforged.minecraftdependencies.MinecraftDependenciesPlugin;
import net.neoforged.moddevgradle.dsl.DataFileCollection;
import net.neoforged.moddevgradle.dsl.ModdingVersionSettings;
import net.neoforged.moddevgradle.dsl.NeoForgeExtension;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import net.neoforged.nfrtgradle.NeoFormRuntimePlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.attributes.Category;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.tasks.SourceSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.function.Consumer;

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

        // Create an access transformer configuration
        var accessTransformers = dataFileConfiguration(
                project,
                CONFIGURATION_ACCESS_TRANSFORMERS,
                "AccessTransformers to widen visibility of Minecraft classes/fields/methods",
                "accesstransformer"
        );
        accessTransformers.extension.getFiles().convention(project.provider(() -> {
            var collection = project.getObjects().fileCollection();

            // Only return this when it actually exists
            var mainSourceSet = ExtensionUtils.getSourceSets(project).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            for (var resources : mainSourceSet.getResources().getSrcDirs()) {
                var defaultPath = new File(resources, "META-INF/accesstransformer.cfg");
                if (project.file(defaultPath).exists()) {
                    return collection.from(defaultPath.getAbsolutePath());
                }
            }

            return collection;
        }));

        // Create a configuration for grabbing interface injection data
        var interfaceInjectionData = dataFileConfiguration(
                project,
                CONFIGURATION_INTERFACE_INJECTION_DATA,
                "Interface injection data adds extend/implements clauses for interfaces to Minecraft code at development time",
                "interfaceinjection"
        );

        var extension = project.getExtensions().create(
                NeoForgeExtension.NAME,
                NeoForgeExtension.class,
                accessTransformers.extension,
                interfaceInjectionData.extension
        );
        IdeIntegration.of(project, Branding.MDG).runTaskOnProjectSync(extension.getIdeSyncTasks());
    }

    public void enableModding(
            Project project,
            ModdingVersionSettings settings,
            ModDevExtension extension
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
            ;
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
        String artifactFilenamePrefix;
        if (neoForgeModule != null) {
            neoForgeModDevLibrariesDependency = neoForgeModule.copy()
                    .capabilities(c -> c.requireCapability("net.neoforged:neoforge-dependencies"));

            artifactFilenamePrefix = "neoforge-" + neoForgeVersion;
        } else {
            neoForgeModDevLibrariesDependency = neoFormModule.copy()
                    .capabilities(c -> c.requireCapability("net.neoforged:neoform-dependencies"));
            artifactFilenamePrefix = "vanilla-" + neoFormVersion;
        }

        new ModDevProjectWorkflow(
                project,
                Branding.MDG,
                neoForgeModule,
                moddingPlatformDataDependencyNotation,
                modulePathDependency,
                runTypesDataDependency,
                testFixturesDependency,
                neoFormModule,
                recompilableMinecraftWorkflowDataDependencyNotation,
                artifactFilenamePrefix,
                neoForgeModDevLibrariesDependency,
                extension
        );
    }

    record DataFileCollectionWrapper(DataFileCollection extension, Configuration configuration) {
    }

    private static DataFileCollectionWrapper dataFileConfiguration(Project project, String name, String description, String category) {
        var configuration = project.getConfigurations().create(name, spec -> {
            spec.setDescription(description);
            spec.setCanBeConsumed(false);
            spec.setCanBeResolved(true);
            spec.attributes(attributes -> setNamedAttribute(project, attributes, Category.CATEGORY_ATTRIBUTE, category));
        });

        var elementsConfiguration = project.getConfigurations().create(name + "Elements", spec -> {
            spec.setDescription("Published data files for " + name);
            spec.setCanBeConsumed(true);
            spec.setCanBeResolved(false);
            spec.attributes(attributes -> setNamedAttribute(project, attributes, Category.CATEGORY_ATTRIBUTE, category));
        });

        // Set up the variant publishing conditionally
        var java = (AdhocComponentWithVariants) project.getComponents().getByName("java");
        java.addVariantsFromConfiguration(elementsConfiguration, variant -> {
            // This should be invoked lazily, so checking if the artifacts are empty is fine:
            // "The details object used to determine what to do with a configuration variant **when publishing**."
            if (variant.getConfigurationVariant().getArtifacts().isEmpty()) {
                variant.skip();
            }
        });

        var depFactory = project.getDependencyFactory();
        Consumer<Object> publishCallback = new Consumer<>() {
            ConfigurablePublishArtifact firstArtifact;
            int artifactCount;

            @Override
            public void accept(Object artifactNotation) {
                elementsConfiguration.getDependencies().add(depFactory.create(project.files(artifactNotation)));
                project.getArtifacts().add(elementsConfiguration.getName(), artifactNotation, artifact -> {
                    if (firstArtifact == null) {
                        firstArtifact = artifact;
                        artifact.setClassifier(category);
                        artifactCount = 1;
                    } else {
                        if (artifactCount == 1) {
                            firstArtifact.setClassifier(category + artifactCount);
                        }
                        artifact.setClassifier(category + (++artifactCount));
                    }
                });
            }
        };

        var extension = project.getObjects().newInstance(DataFileCollection.class, publishCallback);
        configuration.getDependencies().add(depFactory.create(extension.getFiles()));

        return new DataFileCollectionWrapper(extension, configuration);
    }
}
