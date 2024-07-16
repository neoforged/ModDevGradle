package net.neoforged.moddevgradle.tasks;

import net.neoforged.jarjar.metadata.Metadata;
import net.neoforged.jarjar.metadata.MetadataIOHandler;
import net.neoforged.moddevgradle.internal.jarjar.JarJarArtifacts;
import net.neoforged.moddevgradle.internal.jarjar.ResolvedJarJarArtifact;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DeleteSpec;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.ApiStatus;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class JarJar extends DefaultTask {

    @Nested
    @ApiStatus.Internal
    protected abstract JarJarArtifacts getJarJarArtifacts();

    // Used for NO-SOURCE tracking to prevent the task from running if no configurations are defined
    @InputFiles
    @SkipWhenEmpty
    @ApiStatus.Internal
    protected abstract ConfigurableFileCollection getInputFiles();

    @Inject
    @ApiStatus.Internal
    protected abstract ObjectFactory getObjects();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    private final FileSystemOperations fileSystemOperations;

    @Inject
    public JarJar(final FileSystemOperations fileSystemOperations) {
        this.fileSystemOperations = fileSystemOperations;
        this.getOutputDirectory().convention(getProject().getLayout().getBuildDirectory().dir("generated/" + getName()));
    }

    @TaskAction
    protected void run() {
        final List<ResolvedJarJarArtifact> includedJars = getJarJarArtifacts().getResolvedArtifacts().get();
        fileSystemOperations.delete(new Action<DeleteSpec>() {
            @Override
            public void execute(DeleteSpec spec) {
                spec.delete(JarJar.this.getOutputDirectory());
            }
        });

        // Only copy metadata if not empty, always delete
        if (!includedJars.isEmpty()) {
            fileSystemOperations.copy(new Action<CopySpec>() {
                @Override
                public void execute(CopySpec spec) {
                    spec.into(JarJar.this.getOutputDirectory().dir("META-INF/jarjar"));
                    spec.from(includedJars.stream().map(ResolvedJarJarArtifact::getFile).toArray());
                    for (final ResolvedJarJarArtifact includedJar : includedJars) {
                        final String originalName = includedJar.getFile().getName();
                        final String embeddedName = includedJar.getEmbeddedFilename();
                        if (!originalName.equals(embeddedName)) {
                            spec.rename(Pattern.quote(originalName), Matcher.quoteReplacement(embeddedName));
                        }
                    }
                    spec.from(JarJar.this.writeMetadata(includedJars).toFile());
                }
            });
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private Path writeMetadata(final List<ResolvedJarJarArtifact> includedJars) {
        final Path metadataPath = getJarJarMetadataPath();
        final Metadata metadata = createMetadata(includedJars);

        try {
            metadataPath.toFile().getParentFile().mkdirs();
            Files.deleteIfExists(metadataPath);
            Files.write(metadataPath, MetadataIOHandler.toLines(metadata), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (final IOException e) {
            throw new RuntimeException("Failed to write JarJar dependency metadata to disk.", e);
        }
        return metadataPath;
    }

    public final void configuration(final Configuration jarJarConfiguration) {
        getInputFiles().from(jarJarConfiguration);
        getJarJarArtifacts().configuration(jarJarConfiguration);
        dependsOn(jarJarConfiguration);
    }

    public void setConfigurations(final Collection<? extends Configuration> configurations) {
        final ConfigurableFileCollection newConfig = getObjects().fileCollection();
        newConfig.from(configurations.toArray());
        getInputFiles().setFrom(newConfig);
        getJarJarArtifacts().setConfigurations(configurations);
        configurations.forEach(this::dependsOn);
    }

    private Path getJarJarMetadataPath() {
        return getTemporaryDir().toPath().resolve("metadata.json");
    }

    private Metadata createMetadata(final List<ResolvedJarJarArtifact> jars) {
        return new Metadata(
                jars.stream()
                        .map(ResolvedJarJarArtifact::createContainerMetadata)
                        .collect(Collectors.toList())
        );
    }
}
