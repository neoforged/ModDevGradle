package net.neoforged.moddevgradle.internal;

import net.neoforged.moddevgradle.dsl.DataFileCollection;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Category;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.util.function.Consumer;

@ApiStatus.Internal
public final class DataFileCollectionFactory {
    public static final String CONFIGURATION_ACCESS_TRANSFORMERS = "accessTransformers";

    public static final String CONFIGURATION_INTERFACE_INJECTION_DATA = "interfaceInjectionData";

    private DataFileCollectionFactory() {
    }

    public record DefaultDataFileCollections(DataFileCollectionWrapper accessTransformers,
                                             DataFileCollectionWrapper interfaceInjectionData) {
    }

    public record DataFileCollectionWrapper(DataFileCollection extension, Configuration configuration) {
    }

    public static DefaultDataFileCollections createDefault(Project project) {
        // Create an access transformer configuration
        var accessTransformers = DataFileCollectionFactory.create(
                project,
                CONFIGURATION_ACCESS_TRANSFORMERS,
                "AccessTransformers to widen visibility of Minecraft classes/fields/methods",
                "accesstransformer"
        );
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
        var interfaceInjectionData = DataFileCollectionFactory.create(
                project,
                CONFIGURATION_INTERFACE_INJECTION_DATA,
                "Interface injection data adds extend/implements clauses for interfaces to Minecraft code at development time",
                "interfaceinjection"
        );

        return new DefaultDataFileCollections(accessTransformers, interfaceInjectionData);
    }

    public static DataFileCollectionWrapper create(Project project, String name, String description, String category) {
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
