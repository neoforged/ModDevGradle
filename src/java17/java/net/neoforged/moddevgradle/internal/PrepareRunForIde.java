package net.neoforged.moddevgradle.internal;

import net.neoforged.moddevgradle.internal.utils.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
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
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Performs preparation for running the game through the IDE:
 * <p>
 * Writes the JVM arguments for running the game to an args-file compatible with the JVM spec.
 * This is used only for IDEs.
 */
abstract class PrepareRunForIde extends DefaultTask {
    @Internal
    public abstract DirectoryProperty getGameDirectory();

    @OutputFile
    public abstract RegularFileProperty getVmArgsFile();

    @OutputFile
    public abstract RegularFileProperty getProgramArgsFile();

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

    @Classpath
    @InputFiles
    abstract ConfigurableFileCollection getModules();

    @Input
    public abstract MapProperty<String, String> getSystemProperties();

    @Input
    public abstract ListProperty<String> getJvmArguments();

    @Input
    public abstract ListProperty<String> getProgramArguments();

    @Inject
    public PrepareRunForIde() {
    }

    private List<String> getInterpolatedJvmArgs(UserDevRunType runConfig) {
        var result = new ArrayList<String>();
        for (var jvmArg : runConfig.jvmArgs()) {
            String arg = jvmArg;
            if (arg.equals("{modules}")) {
                arg = getModules().getFiles().stream()
                        .map(File::getAbsolutePath)
                        .collect(Collectors.joining(File.pathSeparator));
            }
            result.add(RunUtils.escapeJvmArg(arg));
        }
        return result;
    }

    @TaskAction
    public void prepareRun() throws IOException {
        // Make sure the run directory exists
        // IntelliJ refuses to start a run configuration whose working directory does not exist
        var runDir = getGameDirectory().get().getAsFile();
        Files.createDirectories(runDir.toPath());

        var userDevConfig = UserDevConfig.from(getNeoForgeModDevConfig().getSingleFile());
        var runConfig = userDevConfig.runs().get(getRunType().get());
        if (runConfig == null) {
            throw new GradleException("Trying to prepare unknown run: " + getRunType().get() + ". Available run types: " + userDevConfig.runs().keySet());
        }

        writeJvmArguments(runDir, runConfig);
        writeProgramArguments(runConfig);
    }

    private void writeJvmArguments(File runDir, UserDevRunType runConfig) throws IOException {
        var lines = new ArrayList<String>();

        lines.addAll(getInterpolatedJvmArgs(runConfig));

        var userJvmArgs = getJvmArguments().get();
        if (!userJvmArgs.isEmpty()) {
            lines.add("");
            lines.add("# User JVM Arguments");
            for (var userJvmArg : userJvmArgs) {
                lines.add(RunUtils.escapeJvmArg(userJvmArg));
            }
            lines.add("");
        }

        // Write log4j2 configuration file
        File log4j2xml;
        try {
            log4j2xml = RunUtils.writeLog4j2Configuration(runDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        lines.add(RunUtils.escapeJvmArg("-Dlog4j2.configurationFile=" + log4j2xml.getAbsolutePath()));
        for (var prop : runConfig.props().entrySet()) {
            var propValue = prop.getValue();
            if (propValue.equals("{minecraft_classpath_file}")) {
                propValue = getLegacyClasspathFile().getAsFile().get().getAbsolutePath();
            }

            addSystemProp(prop.getKey(), propValue, lines);
        }

        for (var entry : getSystemProperties().get().entrySet()) {
            addSystemProp(entry.getKey(), entry.getValue(), lines);
        }

        FileUtils.writeLinesSafe(getVmArgsFile().get().getAsFile().toPath(), lines);
    }

    private void writeProgramArguments(UserDevRunType runConfig) throws IOException {
        var lines = new ArrayList<String>();

        lines.add("# Main Class");
        lines.add(runConfig.main());

        lines.add("");
        lines.add("# NeoForge Run-Type Program Arguments");
        var assetProperties = RunUtils.loadAssetProperties(getAssetProperties().get().getAsFile());
        for (var arg : runConfig.args()) {
            if (arg.equals("{assets_root}")) {
                arg = Objects.requireNonNull(assetProperties.assetsRoot(), "assets_root");
            } else if (arg.equals("{asset_index}")) {
                arg = Objects.requireNonNull(assetProperties.assetIndex(), "asset_index");
            }
            lines.add(RunUtils.escapeJvmArg(arg));
        }

        lines.add("# User Supplied Program Arguments");
        lines.addAll(getProgramArguments().get());

        FileUtils.writeLinesSafe(getProgramArgsFile().get().getAsFile().toPath(), lines);
    }

    private static void addSystemProp(String name, String value, List<String> lines) {
        lines.add(RunUtils.escapeJvmArg("-D" + name + "=" + value));
    }
}
