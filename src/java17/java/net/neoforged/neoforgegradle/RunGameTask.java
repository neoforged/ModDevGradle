package net.neoforged.neoforgegradle;

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
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
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * By extending JavaExec, we allow IntelliJ to automatically attach a debugger to the forked JVM, making
 * these runs easy and nice to work with.
 */
// TODO: look into shared abstraction with PrepareRunForIde
@DisableCachingByDefault
public abstract class RunGameTask extends JavaExec {
    @Classpath
    @InputFiles
    public abstract ConfigurableFileCollection getClasspathProvider();

    @Internal
    public abstract DirectoryProperty getGameDirectory();

    @Inject
    public RunGameTask() {
    }

    @TaskAction
    public void exec() {
        // Create directory if needed
        var runDir = getGameDirectory().get().getAsFile(); // store here, can't reference project inside doFirst for the config cache
        try {
            Files.createDirectories(runDir.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create run directory", e);
        }

        classpath(getClasspathProvider());
        setWorkingDir(runDir);
        super.exec();
        // Enable debug logging; doesn't work for FML???
//            runClientTask.systemProperty("forge.logging.console.level", "debug");
    }

}
