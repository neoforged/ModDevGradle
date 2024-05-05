package net.neoforged.neoforgegradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.stream.Collectors;

@CacheableTask
public abstract class RunGameTask extends DefaultTask {
    private final ExecOperations execOperations;

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getLegacyClasspathFile();

    @Classpath
    @InputFiles
    abstract ConfigurableFileCollection getModules();

    @Classpath
    @InputFiles
    abstract ConfigurableFileCollection getClasspath();

    @Internal
    abstract DirectoryProperty getGameDirectory();

    @Internal
    abstract Property<String> getMainClass();

    @Internal
    abstract ListProperty<String> getArgs();

    @Internal
    abstract ListProperty<String> getJvmArgs();

    @Internal
    abstract MapProperty<String, String> getEnvironment();

    @Internal
    abstract MapProperty<String, String> getSystemProperties();

    @Inject
    public RunGameTask(ExecOperations execOperations) {
        this.execOperations = execOperations;
    }

    @TaskAction
    public void runGame() throws Exception {

        // Create directory if needed
        var runDir = getGameDirectory().get().getAsFile(); // store here, can't reference project inside doFirst for the config cache
        try {
            Files.createDirectories(runDir.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create run directory", e);
        }

        execOperations.javaexec(spec -> {
            // This should probably all be done using providers; but that's for later :)
            spec.getMainClass().set(getMainClass().get());
            for (var arg : getArgs().get()) {
//                if (arg.equals("{asset_index}")) {
//                    arg = assetsPath.resolve("indexes")
//                }
                spec.args(arg);
            }
            for (var jvmArg : getJvmArgs().get()) {
                String arg = jvmArg;
                if (arg.equals("{modules}")) {
                    arg = getModules().getFiles().stream()
                            .map(File::getAbsolutePath)
                            .collect(Collectors.joining(File.pathSeparator));
                }
                spec.jvmArgs(arg);
            }
            for (var env : getEnvironment().get().entrySet()) {
                var envValue = env.getValue();
                if (envValue.equals("{source_roots}")) {
                    continue; // This is MOD_CLASSES, skip for now.
                }
                spec.environment(env.getKey(), envValue);
            }
            for (var prop : getSystemProperties().get().entrySet()) {
                var propValue = prop.getValue();
                if (propValue.equals("{minecraft_classpath_file}")) {
                    propValue = getLegacyClasspathFile().getAsFile().get().getAbsolutePath();
                }

                spec.systemProperty(prop.getKey(), propValue);
            }

            spec.classpath(getClasspath());
            spec.setWorkingDir(runDir);
        });
        // Enable debug logging; doesn't work for FML???
//            runClientTask.systemProperty("forge.logging.console.level", "debug");
    }
}
