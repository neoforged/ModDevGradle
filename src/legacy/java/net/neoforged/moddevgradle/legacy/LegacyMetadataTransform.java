package net.neoforged.moddevgradle.legacy;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataRule;
import org.gradle.api.artifacts.repositories.RepositoryResourceAccessor;
import org.gradle.api.model.ObjectFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarInputStream;

abstract class LegacyMetadataTransform implements ComponentMetadataRule {
    protected final ObjectFactory objects;
    private final RepositoryResourceAccessor repositoryResourceAccessor;

    LegacyMetadataTransform(ObjectFactory objects, RepositoryResourceAccessor repositoryResourceAccessor) {
        this.objects = objects;
        this.repositoryResourceAccessor = repositoryResourceAccessor;
    }

    protected final void executeWithConfig(ComponentMetadataContext context, String path) {
        JsonObject[] configRootHolder = new JsonObject[1];
        repositoryResourceAccessor.withResource(path, inputStream -> {
            try (var zin = new JarInputStream(new BufferedInputStream(inputStream))) {
                for (var entry = zin.getNextJarEntry(); entry != null; entry = zin.getNextJarEntry()) {
                    if (entry.getName().equals("config.json")) {
                        var configJson = new String(zin.readAllBytes(), StandardCharsets.UTF_8);
                        configRootHolder[0] = new Gson().fromJson(configJson, JsonObject.class);
                    }
                }
            } catch (IOException e) {
                throw new GradleException("Failed to read " + path);
            }
        });

        if (configRootHolder[0] == null) {
            throw new GradleException("Couldn't find config.json in " + path);
        }
        adaptWithConfig(context, configRootHolder[0]);
    }

    protected abstract void adaptWithConfig(ComponentMetadataContext context, JsonObject config);

    protected final String createPath(ComponentMetadataContext context, String classifier, String extension) {
        var id = context.getDetails().getId();
        return id.getGroup().replace('.', '/') + "/" + id.getName() + "/" + id.getVersion() + "/" + (id.getName() + "-" + id.getVersion() + (classifier.isBlank() ? "" : "-" + classifier) + "." + extension);
    }
}
