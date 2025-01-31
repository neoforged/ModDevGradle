package net.neoforged.moddevgradle.internal;

import java.io.File;
import java.util.function.Consumer;
import net.neoforged.moddevgradle.dsl.DataFileCollection;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import net.neoforged.moddevgradle.internal.utils.StringUtils;
import net.neoforged.moddevgradle.tasks.CopyDataFile;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Category;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.ApiStatus;

/**
 * Access Transformers and Interface Injection Data are treated in a common way as "collections of data files",
 * which can be declared via a {@link DataFileCollection DSL}, and have an associated configuration for internal
 * use by the plugin and the publication of these files.
 * <p>
 * This factory constructs these pairs.
 */
@ApiStatus.Internal
public record DataFileCollections(CollectionWrapper accessTransformers,
        CollectionWrapper interfaceInjectionData) {

    public static final String CONFIGURATION_ACCESS_TRANSFORMERS = "accessTransformers";

    public static final String CONFIGURATION_INTERFACE_INJECTION_DATA = "interfaceInjectionData";

    /**
     * Constructs the default data file collections for access transformers and intrface injection data
     * with sensible defaults.
     */
    public static DataFileCollections create(Project project) {
        // Create an access transformer configuration
        var accessTransformers = createCollection(
                project,
                CONFIGURATION_ACCESS_TRANSFORMERS,
                "AccessTransformers to widen visibility of Minecraft classes/fields/methods",
                "accesstransformer");
        accessTransformers.extension().getFiles().convention(project.provider(() -> {
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
        var interfaceInjectionData = createCollection(
                project,
                CONFIGURATION_INTERFACE_INJECTION_DATA,
                "Interface injection data adds extend/implements clauses for interfaces to Minecraft code at development time",
                "interfaceinjection");

        return new DataFileCollections(accessTransformers, interfaceInjectionData);
    }
    public record CollectionWrapper(DataFileCollection extension, Configuration configuration) {}

    private static CollectionWrapper createCollection(Project project, String name, String description, String category) {
        var configuration = project.getConfigurations().create(name, spec -> {
            spec.setDescription(description);
            spec.setCanBeConsumed(false);
            spec.setCanBeResolved(true);
            spec.attributes(attributes -> {
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.CATEGORY_ATTRIBUTE.getType(), category));
            });
        });

        var elementsConfiguration = project.getConfigurations().create(name + "Elements", spec -> {
            spec.setDescription("Published data files for " + name);
            spec.setCanBeConsumed(true);
            spec.setCanBeResolved(false);
            spec.attributes(attributes -> {
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.CATEGORY_ATTRIBUTE.getType(), category));
            });
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

        var copyTaskName = "copy" + StringUtils.capitalize(name) + "Publications";
        var copyTask = project.getTasks().register(copyTaskName, CopyDataFile.class);

        var depFactory = project.getDependencyFactory();
        Consumer<Object> publishCallback = new Consumer<>() {
            ConfigurablePublishArtifact firstArtifact;
            int artifactCount;

            @Override
            public void accept(Object artifactNotation) {
                // Create a temporary artifact to resolve file and task dependencies.
                var dummyArtifact = project.getArtifacts().add(elementsConfiguration.getName(), artifactNotation);

                var artifactFile = dummyArtifact.getFile();
                var artifactDependencies = dummyArtifact.getBuildDependencies();
                elementsConfiguration.getArtifacts().remove(dummyArtifact);

                var copyOutput = project.getLayout().getBuildDirectory().file(copyTaskName + "/" + artifactCount + "-" + artifactFile.getName());
                copyTask.configure(t -> {
                    t.dependsOn(artifactDependencies);
                    t.getInputFiles().add(project.getLayout().file(project.provider(() -> artifactFile)));
                    t.getOutputFiles().add(copyOutput);
                });

                project.getArtifacts().add(elementsConfiguration.getName(), copyOutput, artifact -> {
                    artifact.builtBy(copyTask);
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

        return new CollectionWrapper(extension, configuration);
    }
}
