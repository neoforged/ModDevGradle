package net.neoforged.moddevgradle.internal;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataRule;
import org.gradle.api.artifacts.repositories.RepositoryResourceAccessor;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.Usage;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.jar.JarInputStream;

// @CacheableTransform
public class LegacyMetadataTransform implements ComponentMetadataRule {
    private final File temporaryArtifactsDir;
    private final ObjectFactory objects;
    private final RepositoryResourceAccessor repositoryResourceAccessor;

    @Inject
    public LegacyMetadataTransform(File temporaryArtifactsDir,
                                   ObjectFactory objects,
                                   RepositoryResourceAccessor repositoryResourceAccessor) {
        this.temporaryArtifactsDir = temporaryArtifactsDir;
        this.objects = objects;
        this.repositoryResourceAccessor = repositoryResourceAccessor;
    }

    @Override
    public void execute(ComponentMetadataContext context) {
        var details = context.getDetails();
        var id = details.getId();

        var userdevJarName = id.getName() + "-" + id.getVersion() + "-userdev.jar";
        var userdevJarPath = id.getGroup().replace('.', '/') + "/" + id.getName() + "/" + id.getVersion() + "/" + userdevJarName;

        JsonObject[] configRootHolder = new JsonObject[1];
        repositoryResourceAccessor.withResource(userdevJarPath, inputStream -> {
            try (var zin = new JarInputStream(new BufferedInputStream(inputStream))) {
                for (var entry = zin.getNextJarEntry(); entry != null; entry = zin.getNextJarEntry()) {
                    if (entry.getName().equals("config.json")) {
                        var configJson = new String(zin.readAllBytes(), StandardCharsets.UTF_8);
                        configRootHolder[0] = new Gson().fromJson(configJson, JsonObject.class);
                    }
                }
            } catch (IOException e) {
                throw new GradleException("Failed to read " + userdevJarPath);
            }
        });

        if (configRootHolder[0] == null) {
            throw new GradleException("Couldn't find userdev config json in " + userdevJarPath);
        }
        var configJsonFile = new File(temporaryArtifactsDir, id.getName() + "-" + id.getVersion() + "-config.json").toPath();
        try {
            Files.writeString(configJsonFile, new Gson().toJson(configRootHolder[0]));
        } catch (IOException e) {
            throw new GradleException("Failed to write userdev config json", e);
        }

        details.addVariant("modDevConfig", variantMetadata -> {
            variantMetadata.withFiles(metadata -> {
                metadata.removeAllFiles();
                metadata.addFile(userdevJarName, userdevJarName);
            });
            variantMetadata.withCapabilities(capabilities -> {
                capabilities.addCapability("net.neoforged", "neoforge-moddev-config", id.getVersion());
            });
        });
        details.addVariant("modDevBundle", variantMetadata -> {
            variantMetadata.withCapabilities(capabilities -> {
                capabilities.addCapability("net.neoforged", "neoforge-moddev-bundle", id.getVersion());
            });
        });
        details.addVariant("modDevModulePath", variantMetadata -> {
            variantMetadata.withDependencies(dependencies -> {
                var modules = configRootHolder[0].getAsJsonArray("modules");
                for (JsonElement module : modules) {
                    dependencies.add(module.getAsString());
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
                var libraries = configRootHolder[0].getAsJsonArray("libraries");
                for (JsonElement library : libraries) {
                    dependencies.add(library.getAsString());
                }
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
