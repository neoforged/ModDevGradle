package net.neoforged.nfrtgradle;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.inject.Inject;
import net.neoforged.moddevgradle.internal.utils.IdeDetection;
import net.neoforged.moddevgradle.internal.utils.NetworkSettingPassthrough;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.process.ExecOperations;
import org.jetbrains.annotations.ApiStatus;

/**
 * Base task implementation for running the NFRT CLI, regardless of which sub-command is used.
 */
public abstract class NeoFormRuntimeTask extends DefaultTask {
    /**
     * To help NFRT avoid unnecessary downloads of artifacts that Gradle has already cached, and to allow
     * overriding dependencies, this property can specify a properties file mapping G:A:V to the on-disk path
     * of those artifacts, which NFRT will use to avoid downloads if those artifacts are requested.
     */
    private final SetProperty<ArtifactManifestEntry> artifactManifestEntries;

    /**
     * Should contain the files that are in the artifact manifest.
     * This is used to make sure that updates to the content of these files force a task re-run,
     * even if the path did not change. (For example, when updating a mavenLocal file).
     */
    private final ConfigurableFileCollection artifacts;

    /**
     * You must ensure that this file collection contains a single file corresponding to the executable
     * NFRT jar-file.
     */
    @Classpath
    @InputFiles
    public abstract ConfigurableFileCollection getNeoFormRuntime();

    /**
     * Enable verbose output for the NFRT engine. Defaults to false.
     */
    @Internal
    public abstract Property<Boolean> getVerbose();

    /**
     * Path to the Java executable to launch NFRT with. This is by default set to a Java 21 launcher.
     */
    @Input
    @ApiStatus.Internal
    protected abstract Property<String> getJavaExecutable();

    @Inject
    @ApiStatus.Internal
    protected abstract JavaToolchainService getJavaToolchainService();

    @Inject
    @ApiStatus.Internal
    protected abstract ExecOperations getExecOperations();

    /**
     * Where NFRT stores its caches, artifacts, assets, etc.
     * This defaults to a subdirectory in the cache folder found in the Gradle user home.
     */
    @Internal
    @ApiStatus.Internal
    protected abstract DirectoryProperty getHomeDirectory();

    /**
     * Where NFRT will store temporary working directories.
     * This defaults to using {@code build/tmp/neoformruntime}.
     */
    @Internal
    @ApiStatus.Internal
    protected abstract DirectoryProperty getWorkDirectory();

    public NeoFormRuntimeTask() {
        var project = getProject();

        // When running NeoForm as part of a Gradle build, we store our caches under Gradles
        // home directory for user convenience (they will be picked up by Gradle cache actions in CI, etc.)
        var gradleHome = project.getGradle().getGradleUserHomeDir();
        var cacheDir = new File(gradleHome, "caches/neoformruntime");
        getHomeDirectory().set(cacheDir);

        // Store temporary working directories in this projects build directory such that gradle clean removes them
        getWorkDirectory().convention(project.getLayout().getBuildDirectory().dir("tmp/neoformruntime"));

        // Run NFRT itself with Java 21
        getJavaExecutable().convention(getJavaToolchainService()
                .launcherFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(21)))
                .map(javaLauncher -> javaLauncher.getExecutablePath().getAsFile().getAbsolutePath()));

        // We construct this here to keep them private from subclasses
        artifactManifestEntries = project.getObjects().setProperty(ArtifactManifestEntry.class);
        getInputs().property("artifactManifestEntries", artifactManifestEntries);
        artifacts = project.files();
        getInputs().property("artifacts", artifacts);
    }

    /**
     * Use this from your task subclass or custom task actions to run NFRT with the given arguments.
     */
    public final void run(List<String> args) {
        // Use Gradle-specific directories when running NFRT
        var realArgs = new ArrayList<>(args);
        realArgs.add(0, "--home-dir");
        realArgs.add(1, getHomeDirectory().get().getAsFile().getAbsolutePath());
        realArgs.add(2, "--work-dir");
        realArgs.add(3, getWorkDirectory().get().getAsFile().getAbsolutePath());

        if (getVerbose().get()) {
            realArgs.add("--verbose");
        }

        var manifestEntries = artifactManifestEntries.get();
        if (!manifestEntries.isEmpty()) {
            var artifactManifest = writeArtifactManifest();
            realArgs.add("--artifact-manifest");
            realArgs.add(artifactManifest.getAbsolutePath());
            realArgs.add("--warn-on-artifact-manifest-miss");
        }

        // When running through IJ always enable emojis
        if (IdeDetection.isIntelliJ()) {
            realArgs.add("--emojis");
        }

        getExecOperations().javaexec(execSpec -> {
            // Pass through network properties
            execSpec.systemProperties(NetworkSettingPassthrough.getNetworkSystemProperties());

            // See https://github.com/gradle/gradle/issues/28959
            execSpec.jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8");

            execSpec.executable(getJavaExecutable().get());
            execSpec.classpath(getNeoFormRuntime());
            execSpec.args(realArgs);
        });
    }

    /**
     * Add all incoming dependencies in the given configuration to the artifact manifest passed to NFRT.
     * This causes NFRT to use files from the configuration when trying to resolve the same
     * dependency coordinate, instead of downloading them.
     */
    public final void addArtifactsToManifest(Configuration configuration) {
        artifactManifestEntries.addAll(configuration.getIncoming().getArtifacts().getResolvedArtifacts().map(results -> {
            return results.stream().map(ArtifactManifestEntry::new).collect(Collectors.toSet());
        }));

        artifacts.from(configuration);
    }

    private File writeArtifactManifest() {
        var artifactsManifest = new Properties();

        for (var artifact : artifactManifestEntries.get()) {
            artifactsManifest.setProperty(artifact.artifactId(), artifact.file().getAbsolutePath());
        }

        File artifactManifest = new File(getTemporaryDir(), "nfrt_artifact_manifest.properties");
        try (var out = new BufferedOutputStream(new FileOutputStream(artifactManifest))) {
            artifactsManifest.store(out, "");
        } catch (IOException e) {
            throw new GradleException("Failed to write NFRT artifact manifest: " + e, e);
        }
        return artifactManifest;
    }
}
