package net.neoforged.neoforgegradle.internal.jarjar;

import net.neoforged.jarjar.metadata.Metadata;
import net.neoforged.jarjar.metadata.MetadataIOHandler;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.bundling.Jar;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;

public abstract class JarJarTask extends Jar {

    @Nested
    public abstract JarJarArtifacts getJarJarArtifacts();

    private final CopySpec jarJarCopySpec;

    public JarJarTask() {
        this.jarJarCopySpec = this.getMainSpec().addChild();
        this.jarJarCopySpec.into("META-INF/jarjar");

        setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE); //As opposed to shadow, we do not filter out our entries early!, So we need to handle them accordingly.
    }

    @TaskAction
    @Override
    protected void copy() {
        List<ResolvedJarJarArtifact> includedJars = getJarJarArtifacts().getResolvedArtifacts().get();
        this.jarJarCopySpec.from(
                includedJars.stream().map(ResolvedJarJarArtifact::getFile).collect(Collectors.toList())
        );
        if (!writeMetadata(includedJars).jars().isEmpty()) {
            // Only copy metadata if not empty.
            this.jarJarCopySpec.from(getJarJarMetadataPath().toFile());
        }
        super.copy();
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
