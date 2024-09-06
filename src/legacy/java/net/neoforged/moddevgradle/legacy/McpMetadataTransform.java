package net.neoforged.moddevgradle.legacy;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataRule;
import org.gradle.api.artifacts.repositories.RepositoryResourceAccessor;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarInputStream;

/**
 * Given an implicit Metadata object by Gradle (which results from reading in a pom.xml from Maven for MCP data,
 * which is basically empty), we build metadata that is equivalent to NeoForms Gradle module metadata.
 * <p>
 * Example for NeoForm:
 * https://maven.neoforged.net/releases/net/neoforged/neoform/1.21-20240613.152323/neoform-1.21-20240613.152323.module
 */
// @CacheableTransform
public class McpMetadataTransform implements ComponentMetadataRule {
    private static final Attribute<String> JVM_VERSION = Attribute.of("org.gradle.jvm.version", String.class);
    private final ObjectFactory objects;
    private final RepositoryResourceAccessor repositoryResourceAccessor;

    @Inject
    public McpMetadataTransform(ObjectFactory objects, RepositoryResourceAccessor repositoryResourceAccessor) {
        this.objects = objects;
        this.repositoryResourceAccessor = repositoryResourceAccessor;
    }

    @Override
    public void execute(ComponentMetadataContext context) {
        var details = context.getDetails();
        var id = details.getId();

        var zipDataName = id.getName() + "-" + id.getVersion() + ".zip";
        var zipDataPath = id.getGroup().replace('.', '/') + "/" + id.getName() + "/" + id.getVersion() + "/" + zipDataName;

        JsonObject[] configRootHolder = new JsonObject[1];
        repositoryResourceAccessor.withResource(zipDataPath, inputStream -> {
            try (var zin = new JarInputStream(new BufferedInputStream(inputStream))) {
                for (var entry = zin.getNextJarEntry(); entry != null; entry = zin.getNextJarEntry()) {
                    if (entry.getName().equals("config.json")) {
                        var configJson = new String(zin.readAllBytes(), StandardCharsets.UTF_8);
                        configRootHolder[0] = new Gson().fromJson(configJson, JsonObject.class);
                    }
                }
            } catch (IOException e) {
                throw new GradleException("Failed to read " + zipDataPath);
            }
        });

        if (configRootHolder[0] == null) {
            throw new GradleException("Couldn't find userdev config json in " + zipDataPath);
        }

        var javaTarget = configRootHolder[0].getAsJsonPrimitive("java_target").getAsInt();

        // a.k.a. "neoformData"
        // Primarily pulled to use for NFRT manifest
        details.addVariant("mcpData", variantMetadata -> {
            variantMetadata.withFiles(files -> {
                files.addFile(zipDataName);
            });
            variantMetadata.attributes(attributes -> {
                attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaTarget);
            });
            // Add tools required by this version of MCP as dependencies of this variant
            variantMetadata.withDependencies(dependencies -> {
                var functions = configRootHolder[0].getAsJsonObject("functions");
                for (var function : functions.entrySet()) {
                    var toolCoordinate = ((JsonObject) function.getValue()).getAsJsonPrimitive("version").getAsString();
                    dependencies.add(toolCoordinate);
                }
            });
            variantMetadata.withCapabilities(capabilities -> {
                capabilities.addCapability("net.neoforged", "neoform", id.getVersion());
            });
        });

        details.addVariant("mcpRuntimeElements", variantMetadata -> {
            variantMetadata.attributes(attributes -> {
                attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaTarget);
                attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
            });
            variantMetadata.withDependencies(dependencies -> {
                dependencies.add("net.neoforged:minecraft-dependencies:" + id.getVersion(), dependency -> {
                    dependency.endorseStrictVersions();
                });
            });
            variantMetadata.withCapabilities(capabilities -> {
                capabilities.addCapability("net.neoforged", "neoform-dependencies", id.getVersion());
            });
        });

        details.addVariant("mcpApiElements", variantMetadata -> {
            variantMetadata.attributes(attributes -> {
                attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaTarget);
                attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_API));
            });
            variantMetadata.withDependencies(dependencies -> {
                var libraries = configRootHolder[0].getAsJsonObject("libraries").getAsJsonArray("joined");
                for (JsonElement library : libraries) {
                    dependencies.add(library.getAsString());
                }
                dependencies.add("net.neoforged:minecraft-dependencies:" + id.getVersion(), dependency -> {
                    dependency.endorseStrictVersions();
                });
            });
            variantMetadata.withCapabilities(capabilities -> {
                capabilities.addCapability("net.neoforged", "neoform-dependencies", id.getVersion());
            });
        });
    }
}
