package net.neoforged.moddevgradle.legacy;

import net.neoforged.moddevgradle.internal.utils.NetworkSettingPassthrough;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Task used to remap a jar using AutoRenamingTool.
 */
public abstract class RemapJarTask extends DefaultTask {

    @Inject
    public RemapJarTask() {
        final JavaPluginExtension extension = getProject().getExtensions().findByType(JavaPluginExtension.class);
        if (extension != null) {
            getJavaExecutable().convention(getToolchainService().launcherFor(extension.getToolchain())
                    .map(launcher -> launcher.getExecutablePath().getAsFile().getAbsolutePath()));
        }

        getOutput().convention(getProject().provider(() -> {
            var path = getArchiveBaseName().get() + getArchiveVersion().map(v -> "-" + v).getOrElse("") + getArchiveClassifier().map(c -> "-" + c).getOrElse("") + ".jar";
            return getDestinationDirectory().file(path);
        }).flatMap(regularFileProvider -> regularFileProvider));
    }

    @Input
    public abstract Property<String> getJavaExecutable();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract JavaToolchainService getToolchainService();

    @Classpath
    @InputFiles
    protected abstract ConfigurableFileCollection getToolClasspath();

    @InputFile
    public abstract RegularFileProperty getMappings();

    /**
     * The libraries to use for inheritance data during the renaming process.
     */
    @Optional
    @InputFiles
    public abstract ConfigurableFileCollection getLibraries();

    @InputFile
    public abstract RegularFileProperty getInput();

    @Internal
    public abstract Property<String> getArchiveBaseName();

    @Internal
    public abstract Property<String> getArchiveVersion();

    @Internal
    public abstract Property<String> getArchiveClassifier();

    @Internal
    public abstract DirectoryProperty getDestinationDirectory();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @Internal
    public abstract RegularFileProperty getLogFile();

    @TaskAction
    public void remap() throws IOException {
        final List<String> args = new ArrayList<>();

        args.addAll(Arrays.asList("--input", getInput().get().getAsFile().getAbsolutePath()));
        args.addAll(Arrays.asList("--output", getOutput().get().getAsFile().getAbsolutePath()));
        args.addAll(Arrays.asList("--names", getMappings().get().getAsFile().getAbsolutePath()));
        getLibraries().forEach(lib -> args.addAll(Arrays.asList("--lib", lib.getAbsolutePath())));
        args.add("--disable-abstract-param");

        try (var log = getLogFile().isPresent() ? Files.newOutputStream(getLogFile().get().getAsFile().toPath()) : new OutputStream() {
            @Override
            public void write(int b) {

            }
        }) {
            getExecOperations().javaexec(execSpec -> {
                // Pass through network properties
                execSpec.systemProperties(NetworkSettingPassthrough.getNetworkSystemProperties());

                // See https://github.com/gradle/gradle/issues/28959
                execSpec.jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8");

                execSpec.executable(getJavaExecutable().get());
                execSpec.classpath(getToolClasspath());
                execSpec.args(args);
                execSpec.setStandardOutput(log);
            }).rethrowFailure().assertNormalExitValue();
        }
    }
}
