package net.neoforged.moddevgradle.legacyforge.internal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.artifacts.CacheableRule;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.DirectDependenciesMetadata;
import org.gradle.api.artifacts.repositories.RepositoryResourceAccessor;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.model.ObjectFactory;

/**
 * Given an implicit Metadata object by Gradle (which results from reading in a pom.xml from Maven for MCP data,
 * which is basically empty), we build metadata that is equivalent to NeoForms Gradle module metadata.
 * <p>
 * Example for NeoForm:
 * https://maven.neoforged.net/releases/net/neoforged/neoform/1.21-20240613.152323/neoform-1.21-20240613.152323.module
 */
@CacheableRule
class McpMetadataTransform extends LegacyMetadataTransform {
    @Inject
    public McpMetadataTransform(ObjectFactory objects, RepositoryResourceAccessor repositoryResourceAccessor) {
        super(objects, repositoryResourceAccessor);
    }

    @Override
    public void execute(ComponentMetadataContext context) {
        executeWithConfig(context, createPath(context, "", "zip"));
    }

    @Override
    protected void adaptWithConfig(ComponentMetadataContext context, JsonObject config) {
        var details = context.getDetails();
        var id = details.getId();

        var zipDataName = id.getName() + "-" + id.getVersion() + ".zip";

        // Very old versions did not specify this. Default to 8 in those cases.
        var javaTarget = config.has("java_target")
                ? config.getAsJsonPrimitive("java_target").getAsInt()
                : 8;

        // a.k.a. "neoformData"
        // Primarily pulled to use for NFRT manifest
        details.addVariant("mcpData", variantMetadata -> {
            variantMetadata.withFiles(files -> files.addFile(zipDataName));
            variantMetadata.attributes(attributes -> {
                attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaTarget);
            });
            // Add tools required by this version of MCP as dependencies of this variant
            variantMetadata.withDependencies(dependencies -> {
                var functions = config.getAsJsonObject("functions");
                for (var function : functions.entrySet()) {
                    var toolCoordinate = ((JsonObject) function.getValue()).getAsJsonPrimitive("version").getAsString();
                    dependencies.add(toolCoordinate);
                }
            });
            variantMetadata.withCapabilities(capabilities -> {
                capabilities.addCapability("net.neoforged", "neoform", id.getVersion());
            });
        });

        dependencies(context, "mcpRuntimeElements", javaTarget, Usage.JAVA_RUNTIME, deps -> {});

        dependencies(context, "mcpApiElements", javaTarget, Usage.JAVA_API, dependencies -> {
            var libraries = config.getAsJsonObject("libraries").getAsJsonArray("joined");
            for (JsonElement library : libraries) {
                dependencies.add(library.getAsString());
            }
        });
    }

    private void dependencies(ComponentMetadataContext context, String name, int javaTarget, String usage, Action<DirectDependenciesMetadata> deps) {
        context.getDetails().addVariant(name, variantMetadata -> {
            variantMetadata.attributes(attributes -> {
                attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaTarget);
                attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, usage));
            });
            variantMetadata.withDependencies(dependencies -> {
                deps.execute(dependencies);
                dependencies.add("net.neoforged:minecraft-dependencies:" + context.getDetails().getId().getVersion());
            });
            variantMetadata.withCapabilities(capabilities -> {
                capabilities.addCapability("net.neoforged", "neoform-dependencies", context.getDetails().getId().getVersion());
            });
        });
    }
}
