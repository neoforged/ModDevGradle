package net.neoforged.neoforgegradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;

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
    public abstract ConfigurableFileCollection getNeoForgeModDevConfig();

    @Input
    public abstract Property<String> getRunType();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getAssetProperties();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getLegacyClasspathFile();

    @Inject
    public PrepareRunForIde() {
    }

    @TaskAction
    public void prepareRun() throws IOException {

        // Make sure the run directory exists
        // IntelliJ refuses to start a run configuration whose working directory does not exist
        var runDir = getRunDirectory().get().getAsFile();
        Files.createDirectories(runDir.toPath());

        var userDevConfig = UserDevConfig.from(getNeoForgeModDevConfig().getSingleFile());
        var runConfig = userDevConfig.runs().get(getRunType().get());
        if (runConfig == null) {
            throw new GradleException("Trying to prepare unknown run: " + getRunType().get() + ". Available run types: " + userDevConfig.runs().keySet());
        }

        // Resolve and write all JVM arguments, main class and main program arguments to an args-file
        var lines = new ArrayList<String>();

        // Write log4j2 configuration file
        File log4j2xml;
        try {
            log4j2xml = RunUtils.writeLog4j2Configuration(runDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // TODO Can't set env
//        for (var env : getRunEnvironment().get().entrySet()) {
//            var envValue = env.getValue();
//            if (envValue.equals("{source_roots}")) {
//                continue; // This is MOD_CLASSES, skip for now.
//            }
//            environment(env.getKey(), envValue);
//        }

        lines.add("\"-Dlog4j2.configurationFile=" + log4j2xml.getAbsolutePath() + "\"");
        for (var prop : runConfig.props().entrySet()) {
            var propValue = prop.getValue();
            if (propValue.equals("{minecraft_classpath_file}")) {
                propValue = getLegacyClasspathFile().getAsFile().get().getAbsolutePath();
            }

            lines.add("\"-D" + prop.getKey() + "=" + propValue + "\"");
        }

        lines.add(runConfig.main());

        // This should probably all be done using providers; but that's for later :)
        var assetProperties = RunUtils.loadAssetProperties(getAssetProperties().get().getAsFile());
        for (var arg : runConfig.args()) {
            if (arg.equals("{assets_root}")) {
                arg = assetProperties.assetsRoot();
            } else if (arg.equals("{asset_index}")) {
                arg = assetProperties.assetIndex();
            }
            lines.add(arg);
        }

        Files.write(getArgsFile().get().getAsFile().toPath(), lines);

    }
}
