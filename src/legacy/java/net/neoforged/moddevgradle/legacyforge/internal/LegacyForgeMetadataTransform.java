package net.neoforged.moddevgradle.legacyforge.internal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.artifacts.CacheableRule;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.DirectDependenciesMetadata;
import org.gradle.api.artifacts.MutableVariantFilesMetadata;
import org.gradle.api.artifacts.repositories.RepositoryResourceAccessor;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.model.ObjectFactory;

@CacheableRule
class LegacyForgeMetadataTransform extends LegacyMetadataTransform {
    @Inject
    public LegacyForgeMetadataTransform(ObjectFactory objects, RepositoryResourceAccessor repositoryResourceAccessor) {
        super(objects, repositoryResourceAccessor);
    }

    @Override
    public void execute(ComponentMetadataContext context) {
        executeWithConfig(context, createPath(context, "userdev", "jar"));
    }

    @Override
    public void adaptWithConfig(ComponentMetadataContext context, JsonObject config) {
        var details = context.getDetails();
        var id = details.getId();

        var userdevJarName = id.getName() + "-" + id.getVersion() + "-userdev.jar";
        var universalJarName = id.getName() + "-" + id.getVersion() + "-universal.jar";

        Action<DirectDependenciesMetadata> vanillaDependencies = deps -> {
            deps.add("de.oceanlabs.mcp:mcp_config:" + id.getVersion().split("-")[0]);
            deps.add("net.neoforged:minecraft-dependencies:" + id.getVersion().split("-")[0]);
        };

        details.addVariant("modDevConfig", variantMetadata -> {
            variantMetadata.withFiles(metadata -> metadata.addFile(userdevJarName, userdevJarName));
            variantMetadata.withCapabilities(capabilities -> {
                capabilities.addCapability("net.neoforged", "neoforge-moddev-config", id.getVersion());
            });
        });
        details.addVariant("modDevBundle", variantMetadata -> {
            variantMetadata.withFiles(metadata -> metadata.addFile(userdevJarName, userdevJarName));
            variantMetadata.withDependencies(vanillaDependencies);
            variantMetadata.withCapabilities(capabilities -> {
                capabilities.addCapability("net.neoforged", "neoforge-moddev-bundle", id.getVersion());
            });
        });
        details.addVariant("modDevModulePath", variantMetadata -> {
            variantMetadata.withDependencies(dependencies -> {
                // Support versions that do not declare modules
                if (config.has("modules")) {
                    var modules = config.getAsJsonArray("modules");
                    for (JsonElement module : modules) {
                        dependencies.add(module.getAsString());
                    }
                }
            });
            variantMetadata.withCapabilities(capabilities -> {
                capabilities.addCapability("net.neoforged", "neoforge-moddev-module-path", id.getVersion());
            });
        });
        details.addVariant("modDevApiElements", variantMetadata -> {
            variantMetadata.attributes(attributes -> {
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.LIBRARY));
                attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.class, Bundling.EXTERNAL));
                attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_API));
            });
            variantMetadata.withDependencies(dependencies -> {
                var libraries = config.getAsJsonArray("libraries");
                for (JsonElement library : libraries) {
                    dependencies.add(library.getAsString());
                }
            });
            variantMetadata.withDependencies(vanillaDependencies);
            variantMetadata.withCapabilities(capabilities -> {
                capabilities.addCapability("net.neoforged", "neoforge-dependencies", id.getVersion());
            });
        });
        details.addVariant("modDevRuntimeElements", variantMetadata -> {
            variantMetadata.attributes(attributes -> {
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.LIBRARY));
                attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.class, Bundling.EXTERNAL));
                attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
            });
            variantMetadata.withCapabilities(capabilities -> {
                capabilities.removeCapability(id.getGroup(), id.getName());
                capabilities.addCapability("net.neoforged", "neoforge-dependencies", id.getVersion());
            });
            variantMetadata.withDependencies(vanillaDependencies);
            variantMetadata.withFiles(MutableVariantFilesMetadata::removeAllFiles);
            variantMetadata.withDependencies(dependencies -> {
                // Support versions that do not declare modules
                if (config.has("modules")) {
                    var modules = config.getAsJsonArray("modules");
                    for (JsonElement module : modules) {
                        dependencies.add(module.getAsString());
                    }
                }
                var libraries = config.getAsJsonArray("libraries");
                for (JsonElement library : libraries) {
                    dependencies.add(library.getAsString());
                }
            });
        });

        details.addVariant("universalJar", variantMetadata -> {
            variantMetadata.attributes(attributes -> {
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.LIBRARY));
                attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.class, Bundling.EXTERNAL));
                attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
                attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.class, LibraryElements.JAR));
            });
            variantMetadata.withFiles(metadata -> metadata.addFile(universalJarName, universalJarName));
        });
    }
}
