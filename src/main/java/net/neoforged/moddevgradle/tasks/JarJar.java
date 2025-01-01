package net.neoforged.moddevgradle.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;
import net.neoforged.jarjar.metadata.Metadata;
import net.neoforged.jarjar.metadata.MetadataIOHandler;
import net.neoforged.moddevgradle.internal.jarjar.JarJarArtifacts;
import net.neoforged.moddevgradle.internal.jarjar.ResolvedJarJarArtifact;
import net.neoforged.moddevgradle.internal.utils.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
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
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.jetbrains.annotations.ApiStatus;

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

    @Internal
    public abstract DirectoryProperty getBuildDirectory();

    private final FileSystemOperations fileSystemOperations;

    @Inject
    public JarJar(FileSystemOperations fileSystemOperations) {
        this.fileSystemOperations = fileSystemOperations;
        this.getOutputDirectory().convention(getProject().getLayout().getBuildDirectory().dir("generated/" + getName()));
        this.getBuildDirectory().convention(getProject().getLayout().getBuildDirectory());
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
            attributes.attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaPlugin.getToolchain().getLanguageVersion().orElse(JavaLanguageVersion.current()).map(JavaLanguageVersion::asInt));
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
    protected void run() throws IOException {
        List<ResolvedJarJarArtifact> includedJars = new ArrayList<>(getJarJarArtifacts().getResolvedArtifacts().get());
        fileSystemOperations.delete(spec -> spec.delete(getOutputDirectory()));

        var artifactFiles = new ArrayList<>(includedJars.stream().map(ResolvedJarJarArtifact::getFile).toList());
        // Now we have to handle pure file collection dependencies that do not have artifact ids
        for (var file : getInputFiles()) {
            if (!artifactFiles.contains(file)) {
                // Determine the module-name of the file, which is also what Java will use as the unique key
                // when it tries to load the file. No two files can have the same module name, so it seems
                // like a fitting key for conflict resolution by JiJ.
                var moduleName = FileUtils.getExplicitJavaModuleName(file);
                if (moduleName.isEmpty()) {
                    throw new GradleException("Cannot embed local file dependency " + file + " because it has no explicit Java module name.\n" +
                            "Please set either 'Automatic-Module-Name' in the Jar manifest, or make it an explicit Java module.\n" +
                            "This ensures that your file does not conflict with another mods library that has the same or a similar filename.");
                }

                // Create a hashcode to use as a version
                var hashCode = FileUtils.hashFile(file, "MD5");
                includedJars.add(new ResolvedJarJarArtifact(
                        file,
                        file.getName(),
                        hashCode,
                        "[" + hashCode + "]",
                        "",
                        moduleName.get()));
                artifactFiles.add(file);
            }
        }

        // Only copy metadata if not empty, always delete
        if (!includedJars.isEmpty()) {
            fileSystemOperations.copy(spec -> {
                spec.into(getOutputDirectory().dir("META-INF/jarjar"));
                spec.from(artifactFiles.toArray());
                for (var includedJar : includedJars) {
                    // Warn if any included jar is using the cursemaven group.
                    // We know that cursemaven versions are not comparable, and the same artifact might also be
                    // available under a "normal" group and artifact from another Maven repository.
                    // JIJ will not correctly detect the conflicting file at runtime if another mod uses the normal Maven dependency.
                    // For a description of Curse Maven, see https://www.cursemaven.com/
                    if ("curse.maven".equals(includedJar.getGroup())) {
                        getLogger().warn("Embedding dependency {}:{}:{} from cursemaven using JiJ is likely to cause conflicts at runtime when other mods include the same library from a normal Maven repository.",
                                includedJar.getGroup(), includedJar.getArtifact(), includedJar.getVersion());
                    }
                    // Same with the Modrinth official maven (see https://support.modrinth.com/en/articles/8801191-modrinth-maven)
                    // While actual versions can be used, version IDs (which are random strings) can also be used
                    else if ("maven.modrinth".equals(includedJar.getGroup())) {
                        getLogger().warn("Embedding dependency {}:{}:{} from Modrinth Maven using JiJ is likely to cause conflicts at runtime when other mods include the same library from a normal Maven repository.",
                                includedJar.getGroup(), includedJar.getArtifact(), includedJar.getVersion());
                    }

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
        var metadataPath = getJarJarMetadataPath();
        var metadata = createMetadata(includedJars);

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
                        .collect(Collectors.toList()));
    }
}
