package net.neoforged.moddevgradle.tasks;

import net.neoforged.jarjar.metadata.Metadata;
import net.neoforged.jarjar.metadata.MetadataIOHandler;
import net.neoforged.moddevgradle.internal.jarjar.JarJarArtifacts;
import net.neoforged.moddevgradle.internal.jarjar.ResolvedJarJarArtifact;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
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
    private static final String DEFAULT_GROUP = "jarjar";

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
    public JarJar(FileSystemOperations fileSystemOperations) {
        this.fileSystemOperations = fileSystemOperations;
        this.getOutputDirectory().convention(getProject().getLayout().getBuildDirectory().dir("generated/" + getName()));
        setGroup(DEFAULT_GROUP);
    }

    /**
     * Registers an instance of this task with a project and creates the associated configuration from which
     * the dependencies to embed are sourced.
     */
    public static TaskProvider<JarJar> registerWithConfiguration(Project project, String name) {
        var configuration = project.getConfigurations().create(name);
        configuration.setTransitive(false);
        // jarJar configurations should be resolvable, but ought not to be exposed to consumers;
        // as it has attributes, it could conflict with normal exposed configurations
        configuration.setCanBeResolved(true);
        configuration.setCanBeConsumed(false);

        var javaPlugin = project.getExtensions().getByType(JavaPluginExtension.class);

        configuration.attributes(attributes -> {
            // Unfortunately, while we can hopefully rely on disambiguation rules to get us some of these, others run
            // into issues. The target JVM version is the most worrying - we don't want to pull in a variant for a newer
            // jvm version. We could copy DefaultJvmFeature, and search for the target version of the compile task,
            // but this is difficult - we only have a feature name, not the linked source set. For this reason, we use
            // the toolchain version, which is the most likely to be correct.
            attributes.attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaPlugin.getToolchain().getLanguageVersion().map(JavaLanguageVersion::asInt));
            attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.getObjects().named(LibraryElements.class, LibraryElements.JAR));
            attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
            attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, project.getObjects().named(Bundling.class, Bundling.EXTERNAL));
        });

        return project.getTasks().register(name, JarJar.class, jarJar -> {
            jarJar.setDescription("Creates the directory structure and metadata needed to embed other Jar files.");
            jarJar.configuration(configuration);
        });
    }

    @TaskAction
    protected void run() {
        List<ResolvedJarJarArtifact> includedJars = getJarJarArtifacts().getResolvedArtifacts().get();
        fileSystemOperations.delete(spec -> spec.delete(getOutputDirectory()));

        // Only copy metadata if not empty, always delete
        if (!includedJars.isEmpty()) {
            fileSystemOperations.copy(spec -> {
                spec.into(getOutputDirectory().dir("META-INF/jarjar"));
                spec.from(includedJars.stream().map(ResolvedJarJarArtifact::getFile).toArray());
                for (var includedJar : includedJars) {
                    var originalName = includedJar.getFile().getName();
                    var embeddedName = includedJar.getEmbeddedFilename();
                    if (!originalName.equals(embeddedName)) {
                        spec.rename(Pattern.quote(originalName), Matcher.quoteReplacement(embeddedName));
                    }
                }
                spec.from(writeMetadata(includedJars).toFile());
            });
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private Path writeMetadata(List<ResolvedJarJarArtifact> includedJars) {
        final Path metadataPath = getJarJarMetadataPath();
        final Metadata metadata = createMetadata(includedJars);

        try {
            metadataPath.toFile().getParentFile().mkdirs();
            Files.deleteIfExists(metadataPath);
            Files.write(metadataPath, MetadataIOHandler.toLines(metadata), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write JarJar dependency metadata to disk.", e);
        }
        return metadataPath;
    }

    public void configuration(Configuration jarJarConfiguration) {
        getInputFiles().from(jarJarConfiguration);
        getJarJarArtifacts().configuration(jarJarConfiguration);
        dependsOn(jarJarConfiguration);
    }

    public void setConfigurations(Collection<? extends Configuration> configurations) {
        var newConfig = getObjects().fileCollection();
        newConfig.from(configurations.toArray());
        getInputFiles().setFrom(newConfig);
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
