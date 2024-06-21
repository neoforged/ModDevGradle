package net.neoforged.moddevgradle.internal;

import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataRule;
import org.gradle.api.artifacts.repositories.RepositoryResourceAccessor;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.Usage;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;

public class LegacyMetadataTransform implements ComponentMetadataRule {
    private final ObjectFactory objects;
    private final RepositoryResourceAccessor repositoryResourceAccessor;

    @Inject
    public LegacyMetadataTransform(ObjectFactory objects, RepositoryResourceAccessor repositoryResourceAccessor) {
        this.objects = objects;
        this.repositoryResourceAccessor = repositoryResourceAccessor;
    }

    @Override
    public void execute(ComponentMetadataContext context) {
        var details = context.getDetails();
        var id = details.getId();

        details.addVariant("modDevBundle", variantMetadata -> {
            variantMetadata.withCapabilities(capabilities -> {
                capabilities.addCapability("net.neoforged", "neoforge-moddev-bundle", id.getVersion());
            });
        });
        details.addVariant("modDevApiElements", variantMetadata -> {
            variantMetadata.attributes(attributes -> {
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.LIBRARY));
                attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.class, Bundling.EXTERNAL));
                attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_API));
            });
            variantMetadata.withCapabilities(capabilities -> {
                capabilities.addCapability("net.neoforged", "neoforge-dependencies", id.getVersion());
            });
        });
        details.withVariant("runtime", variantMetadata -> {
            variantMetadata.attributes(attributes -> {
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.LIBRARY));
                attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.class, Bundling.EXTERNAL));
                attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
            });
            variantMetadata.withCapabilities(capabilities -> {
                capabilities.addCapability("net.neoforged", "neoforge-dependencies", id.getVersion());
            });
        });
    }
}
