package net.neoforged.neoforgegradle;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * By extending JavaExec, we allow IntelliJ to automatically attach a debugger to the forked JVM, making
 * these runs easy and nice to work with.
 */
@DisableCachingByDefault
public abstract class RunGameTask extends JavaExec {
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getLegacyClasspathFile();

    @Classpath
    @InputFiles
    abstract ConfigurableFileCollection getModules();

    @Classpath
    @InputFiles
    public abstract ConfigurableFileCollection getClasspathProvider();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getAssetProperties();

    @Internal
    public abstract DirectoryProperty getGameDirectory();

    @Internal
    public abstract ListProperty<String> getRunCommandLineArgs();

    @Internal
    public abstract ListProperty<String> getRunJvmArgs();

    @Internal
    public abstract MapProperty<String, String> getRunEnvironment();

    @Internal
    public abstract MapProperty<String, String> getRunSystemProperties();

    @Inject
    public RunGameTask() {
        super.getJvmArgumentProviders().add(this::getInterpolatedJvmArgs);
    }

    private List<String> getInterpolatedJvmArgs() {
        var result = new ArrayList<String>();
        for (var jvmArg : getRunJvmArgs().get()) {
            String arg = jvmArg;
            if (arg.equals("{modules}")) {
                arg = getModules().getFiles().stream()
                        .map(File::getAbsolutePath)
                        .collect(Collectors.joining(File.pathSeparator));
            }
            result.add(arg);
        }
        return result;
    }

    @TaskAction
    public void exec() {
        var assetProperties = RunUtils.loadAssetProperties(getAssetProperties().get().getAsFile());

        // Create directory if needed
        var runDir = getGameDirectory().get().getAsFile(); // store here, can't reference project inside doFirst for the config cache
        try {
            Files.createDirectories(runDir.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create run directory", e);
        }

        // Write log4j2 configuration file
        File log4j2xml;
        try {
            log4j2xml = RunUtils.writeLog4j2Configuration(runDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // This should probably all be done using providers; but that's for later :)
        for (var arg : getRunCommandLineArgs().get()) {
            if (arg.equals("{assets_root}")) {
                arg = assetProperties.assetsRoot();
            } else if (arg.equals("{asset_index}")) {
                arg = assetProperties.assetIndex();
            }
            args(arg);
        }

        for (var env : getRunEnvironment().get().entrySet()) {
            var envValue = env.getValue();
            if (envValue.equals("{source_roots}")) {
                continue; // This is MOD_CLASSES, skip for now.
            }
            environment(env.getKey(), envValue);
        }
        systemProperty("log4j2.configurationFile", log4j2xml.getAbsolutePath());
        for (var prop : getRunSystemProperties().get().entrySet()) {
            var propValue = prop.getValue();
            if (propValue.equals("{minecraft_classpath_file}")) {
                propValue = getLegacyClasspathFile().getAsFile().get().getAbsolutePath();
            }

            systemProperty(prop.getKey(), propValue);
        }

        classpath(getClasspathProvider());
        setWorkingDir(runDir);
        super.exec();
        // Enable debug logging; doesn't work for FML???
//            runClientTask.systemProperty("forge.logging.console.level", "debug");
    }

}
