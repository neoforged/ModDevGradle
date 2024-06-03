package net.neoforged.moddevgradle.internal;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.HashMap;

/**
 * By extending JavaExec, we allow IntelliJ to automatically attach a debugger to the forked JVM, making
 * these runs easy and nice to work with.
 */
@DisableCachingByDefault
public abstract class RunGameTask extends JavaExec {
    @Classpath
    @InputFiles
    public abstract ConfigurableFileCollection getClasspathProvider();

    @Input
    public abstract MapProperty<String, String> getEnvironmentProperty();

    @Internal
    public abstract DirectoryProperty getGameDirectory();

    @Inject
    public RunGameTask() {
    }

    @TaskAction
    public void exec() {
        // Create directory if needed
        var runDir = getGameDirectory().get().getAsFile();
        try {
            Files.createDirectories(runDir.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create run directory", e);
        }

        getEnvironment().putAll(getEnvironmentProperty().get());

        classpath(getClasspathProvider());
        setWorkingDir(runDir);
        super.exec();
    }

}
