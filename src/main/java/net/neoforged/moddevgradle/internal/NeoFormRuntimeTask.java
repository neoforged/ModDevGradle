package net.neoforged.moddevgradle.internal;

import net.neoforged.moddevgradle.internal.utils.IdeDetection;
import net.neoforged.moddevgradle.internal.utils.NetworkSettingPassthrough;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Base task implementation for running the NFRT CLI, regardless of which sub-command is used.
 */
abstract public class NeoFormRuntimeTask extends DefaultTask {

    /**
     * Enable verbose output for the NFRT engine.
     */
    @Internal
    abstract Property<Boolean> getVerbose();

    @Classpath
    @InputFiles
    abstract ConfigurableFileCollection getNeoFormRuntime();

    /**
     * To help NFRT avoid unnecessary downloads of artifacts that Gradle has already cached, and to allow
     * overriding dependencies, this property can specify a properties file mapping G:A:V to the on-disk path
     * of those artifacts, which NFRT will use to avoid downloads if those artifacts are requested.
     */
    @InputFile
    @Optional
    abstract RegularFileProperty getArtifactManifestFile();

    /**
     * Launcher for the java version used by NFRT itself.
     */
    @Internal
    abstract Property<JavaLauncher> getNeoFormRuntimeLauncher();

    @Inject
    protected abstract JavaToolchainService getJavaToolchainService();

    @Inject
    protected abstract ExecOperations getExecOperations();

    /**
     * Where NFRT stores its caches, artifacts, assets, etc.
     */
    @Internal
    protected abstract DirectoryProperty getHomeDirectory();

    /**
     * Where NFRT will store temporary working directories.
     */
    @Internal
    protected abstract DirectoryProperty getWorkDirectory();

    public NeoFormRuntimeTask() {
        var project = getProject();

        getVerbose().convention(false);

        // When running NeoForm as part of a Gradle build, we store our caches under Gradles
        // home directory for user convenience (they will be picked up by Gradle cache actions in CI, etc.)
        var gradleHome = project.getGradle().getGradleUserHomeDir();
        var cacheDir = new File(gradleHome, "caches/neoformruntime");
        getHomeDirectory().set(cacheDir);

        // Store temporary working directories in this projects build directory such that gradle clean removes them
        getWorkDirectory().set(project.getLayout().getBuildDirectory().dir("tmp/neoformruntime"));

        // Default to J21 for NFRT
        getNeoFormRuntimeLauncher().convention(getJavaToolchainService().launcherFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(21))));
    }

    protected void run(List<String> args) {
        // Use Gradle-specific directories when running NFRT
        var realArgs = new ArrayList<>(args);
        realArgs.add(0, "--home-dir");
        realArgs.add(1, getHomeDirectory().get().getAsFile().getAbsolutePath());
        realArgs.add(2, "--work-dir");
        realArgs.add(3, getWorkDirectory().get().getAsFile().getAbsolutePath());

        if (getVerbose().get()) {
            realArgs.add("--verbose");
        }

        if (getArtifactManifestFile().isPresent()) {
            realArgs.add("--artifact-manifest");
            realArgs.add(getArtifactManifestFile().get().getAsFile().getAbsolutePath());
            realArgs.add("--warn-on-artifact-manifest-miss");
        }

        // When running through IJ or Eclipse, always enable emojis
        if (IdeDetection.isIntelliJ() || IdeDetection.isEclipse()) {
            realArgs.add("--emojis");
        }

        getExecOperations().javaexec(execSpec -> {
            // Pass through network properties
            execSpec.systemProperties(NetworkSettingPassthrough.getNetworkSystemProperties());

            // See https://github.com/gradle/gradle/issues/28959
            execSpec.jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8");

            execSpec.executable(getNeoFormRuntimeLauncher().get().getExecutablePath().getAsFile());
            execSpec.classpath(getNeoFormRuntime());
            execSpec.args(realArgs);
        });
    }

}
