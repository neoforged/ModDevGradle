package net.neoforged.nfrtgradle;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.Nullable;

public abstract class SplitMergedJar extends DefaultTask {
    @Inject
    public SplitMergedJar() {}

    @InputFile
    public abstract RegularFileProperty getClientResourcesJar();

    @InputFile
    public abstract RegularFileProperty getMergedJar();

    @OutputFile
    public abstract RegularFileProperty getCommonJar();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getCommonSourcesJar();

    @OutputFile
    public abstract RegularFileProperty getClientJar();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getClientSourcesJar();

    @TaskAction
    public void splitMergedJar() throws IOException {
        if (!getClientResourcesJar().isPresent()) {
            throw new IllegalStateException("Can't request split dist result when splitDist is disabled!");
        }
        try (
                var clientResources = new JarFile(getClientResourcesJar().get().getAsFile());
                var merged = new ZipInputStream(new BufferedInputStream(Files.newInputStream(getMergedJar().get().getAsFile().toPath())));
                var common = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(getCommonJar().get().getAsFile().toPath())));
                var client = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(getClientJar().get().getAsFile().toPath())))) {

            var manifest = clientResources.getManifest();

            if (getCommonSourcesJar().isPresent() && getClientSourcesJar().isPresent()) {
                try (
                        var commonSources = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(getCommonSourcesJar().get().getAsFile().toPath())));
                        var clientSources = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(getClientSourcesJar().get().getAsFile().toPath())))) {
                    spiltHelper(manifest, merged, common, client, commonSources, clientSources);
                }
            } else {
                spiltHelper(manifest, merged, common, client, null, null);
            }

        }
    }

    private static void spiltHelper(
            java.util.jar.Manifest manifest,
            ZipInputStream merged, ZipOutputStream common, ZipOutputStream client,
            @Nullable ZipOutputStream commonSources, @Nullable ZipOutputStream clientSources) throws IOException {
        var sourceDistName = new Attributes.Name("Minecraft-Dist");
        for (var entry = merged.getNextEntry(); entry != null; entry = merged.getNextEntry()) {
            if (entry.isDirectory()) {
                continue;
            }

            var name = entry.getName();
            ZipOutputStream commonTarget = common;
            ZipOutputStream clientTarget = client;
            Attributes fileEntry = null;
            if (name.endsWith(".class")) {
                fileEntry = manifest.getEntries().get(name);
            } else if (name.endsWith(".java")) {
                fileEntry = manifest.getEntries().get(name.replace(".java", ".class"));
                if (commonSources != null && clientSources != null) {
                    commonTarget = commonSources;
                    clientTarget = clientSources;
                }
            }
            String dist = null;

            if (fileEntry != null) {
                dist = fileEntry.getValue(sourceDistName);
            } else if (name.startsWith("net/neoforged/neoforge/client")) {
                dist = "client";
            }

            if ("client".equals(dist)) {
                clientTarget.putNextEntry(entry);
                merged.transferTo(clientTarget);
                clientTarget.closeEntry();
            } else {
                commonTarget.putNextEntry(entry);
                merged.transferTo(commonTarget);
                commonTarget.closeEntry();
            }
        }
    }
}
