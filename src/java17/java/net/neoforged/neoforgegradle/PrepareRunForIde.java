package net.neoforged.neoforgegradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Performs preparation for running the game through the IDE:
 * <p>
 * Writes the JVM arguments for running the game to an args-file compatible with the JVM spec.
 * This is used only for IDEs.
 */
public abstract class PrepareRunForIde extends DefaultTask {
    @InputDirectory
    public abstract DirectoryProperty getRunDirectory();

    @OutputFile
    public abstract RegularFileProperty getArgsFile();

    @Classpath
    public abstract ConfigurableFileCollection getNeoForgeModDev();

    @Input
    public abstract Property<String> getRunType();

    @Inject
    public PrepareRunForIde() {
    }

    @TaskAction
    public void prepareRun() throws IOException {

        // Make sure the run directory exists
        // IntelliJ refuses to start a run configuration whose working directory does not exist
        Files.createDirectories(getRunDirectory().get().getAsFile().toPath());

        // Resolve and write all JVM arguments, main class and main program arguments to an args-file
        Files.writeString(getArgsFile().get().getAsFile().toPath(), "");

    }
}
