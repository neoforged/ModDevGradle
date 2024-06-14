package net.neoforged.moddevgradle.tasks;

import net.neoforged.jarjar.metadata.Metadata;
import net.neoforged.jarjar.metadata.MetadataIOHandler;
import net.neoforged.moddevgradle.internal.jarjar.JarJarArtifacts;
import net.neoforged.moddevgradle.internal.jarjar.ResolvedJarJarArtifact;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.ApiStatus;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public abstract class JarJar extends DefaultTask {

    @Nested
    @ApiStatus.Internal
    protected abstract JarJarArtifacts getJarJarArtifacts();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    private final FileSystemOperations fileSystemOperations;

    @Inject
    public JarJar(FileSystemOperations fileSystemOperations) {
        this.fileSystemOperations = fileSystemOperations;
        this.getOutputDirectory().convention(getProject().getLayout().getBuildDirectory().dir("generated/jarjar/" + getName()));
    }

    @TaskAction
    protected void run() {
        List<ResolvedJarJarArtifact> includedJars = getJarJarArtifacts().getResolvedArtifacts().get();
        fileSystemOperations.delete(spec -> {
            spec.delete(getOutputDirectory());
        });
        fileSystemOperations.copy(spec -> {
            spec.into(getOutputDirectory().dir("/META-INF/jarjar"));
            spec.from(
                    includedJars.stream().map(ResolvedJarJarArtifact::getFile).collect(Collectors.toList())
            );
            if (!writeMetadata(includedJars).jars().isEmpty()) {
                // Only copy metadata if not empty.
                spec.from(getJarJarMetadataPath().toFile());
            }
        });
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private Metadata writeMetadata(List<ResolvedJarJarArtifact> includedJars) {
        final Path metadataPath = getJarJarMetadataPath();
        final Metadata metadata = createMetadata(includedJars);

        if (!metadata.jars().isEmpty()) {
            try {
                metadataPath.toFile().getParentFile().mkdirs();
                Files.deleteIfExists(metadataPath);
                Files.write(metadataPath, MetadataIOHandler.toLines(metadata), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write JarJar dependency metadata to disk.", e);
            }
        }
        return metadata;
    }

    public void configuration(Configuration jarJarConfiguration) {
        getJarJarArtifacts().configuration(jarJarConfiguration);
        dependsOn(jarJarConfiguration);
    }

    public void setConfigurations(Collection<? extends Configuration> configurations) {
        getJarJarArtifacts().setConfigurations(configurations);
        configurations.forEach(this::dependsOn);
    }

    private Path getJarJarMetadataPath() {
        return getTemporaryDir().toPath().resolve("metadata.json");
    }

    private Metadata createMetadata(List<ResolvedJarJarArtifact> jars) {
        return new Metadata(
                jars.stream()
                        .map(ResolvedJarJarArtifact::createContainerMetadata)
                        .collect(Collectors.toList())
        );
    }
}
