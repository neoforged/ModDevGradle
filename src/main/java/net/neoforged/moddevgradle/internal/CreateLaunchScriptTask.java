package net.neoforged.moddevgradle.internal;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import net.neoforged.moddevgradle.dsl.RunModel;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import net.neoforged.moddevgradle.internal.utils.FileUtils;
import net.neoforged.moddevgradle.internal.utils.OperatingSystem;
import net.neoforged.moddevgradle.internal.utils.StringUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaToolchainService;

/**
 * Writes standalone start scripts to launch the game.
 * These scripts are useful for launching the game outside Gradle or the IDE, for example, from tools
 * like Renderdoc, NVidia NSight, Linux perf record and similar.
 */
abstract class CreateLaunchScriptTask extends DefaultTask {
    @Input
    abstract Property<String> getWorkingDirectory();

    @InputFile
    abstract Property<String> getVmArgsFile();

    /**
     * Set to {@link PrepareRun#getProgramArgsFile()}
     */
    @InputFile
    abstract Property<String> getProgramArgsFile();

    /**
     * This argument file is only used by the launch shell-scripts.
     */
    @OutputFile
    abstract RegularFileProperty getClasspathArgsFile();

    /**
     * A platform-specific script to launch this run configuration with directly from outside the IDE / Gradle.
     */
    @OutputFile
    abstract RegularFileProperty getLaunchScript();

    /**
     * Set to the desired Java runtime classpath.
     */
    @Classpath
    @InputFiles
    abstract ConfigurableFileCollection getRuntimeClasspath();

    @Input
    abstract Property<ModFoldersProvider> getModFolders();

    /**
     * @see RunModel#getEnvironment()
     */
    @Input
    abstract MapProperty<String, String> getEnvironment();

    /**
     * The Java executable to run the game with.
     */
    @Input
    abstract Property<String> getJavaExecutable();

    @Inject
    protected abstract JavaToolchainService getJavaToolchainService();

    @Inject
    public CreateLaunchScriptTask() {
        // Get the projects java launcher to use for the shell script
        var java = ExtensionUtils.getExtension(getProject(), "java", JavaPluginExtension.class);
        getJavaExecutable().convention(getJavaToolchainService()
                .launcherFor(java.getToolchain())
                .map(javaLauncher -> javaLauncher.getExecutablePath().getAsFile().getAbsolutePath()));
    }

    @TaskAction
    public void createScripts() throws IOException {
        writeClasspathArguments();
        if (!getLaunchScript().isPresent()) {
            return;
        }

        var javaCommand = new ArrayList<String>();
        javaCommand.add(getJavaExecutable().get());
        javaCommand.add("@" + getClasspathArgsFile().get().getAsFile().getAbsolutePath());
        javaCommand.add("@" + getVmArgsFile().get());
        javaCommand.add(getModFolders().get().getArgument());
        javaCommand.add(RunUtils.DEV_LAUNCH_MAIN_CLASS);
        javaCommand.add("@" + getProgramArgsFile().get());

        var os = OperatingSystem.current();
        if (os == OperatingSystem.WINDOWS) {
            writeLaunchScriptForWindows(javaCommand);
        } else {
            writeLaunchScriptForUnix(javaCommand);
        }
    }

    /**
     * Writes a JVM argument file that would launch the JVM with the same classpath that
     * Gradle would launch it with.
     */
    private void writeClasspathArguments() throws IOException {
        if (!getClasspathArgsFile().isPresent()) {
            return;
        }

        var classpathFileList = getRuntimeClasspath().getFiles().stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.joining(File.pathSeparator));

        var lines = List.of(
                "-classpath",
                RunUtils.escapeJvmArg(classpathFileList));

        FileUtils.writeLinesSafe(
                getClasspathArgsFile().get().getAsFile().toPath(),
                lines,
                // JVM expects default character set
                StringUtils.getNativeCharset());
    }

    private void writeLaunchScriptForWindows(List<String> javaCommand) throws IOException {
        var lines = new ArrayList<String>();
        Collections.addAll(lines,
                "@echo off",
                "setlocal",
                // Stackoverflow suggests this to save the current codepage so that we can restore it
                // https://stackoverflow.com/a/1427817
                "for /f \"tokens=2 delims=:.\" %%x in ('chcp') do set _codepage=%%x",
                // Switch encoding to Unicode, otherwise the next "cd" might not work with special chars
                "chcp 65001>nul");

        for (var entry : getEnvironment().get().entrySet()) {
            lines.add("set " + escapeBatchScriptArg(entry.getKey()) + "=" + escapeBatchScriptArg(entry.getValue()));
        }

        Collections.addAll(lines,
                "cd " + getWorkingDirectory().get(),
                javaCommand.stream().map(this::escapeBatchScriptArg).collect(Collectors.joining(" ")),
                // When Minecraft crashed, pause to prevent the console from closing, making it harder to read the error
                "if not ERRORLEVEL 0 (" +
                        "  echo Minecraft failed with exit code %ERRORLEVEL%" +
                        "  pause" +
                        ")",
                // Restore original codepage
                "chcp %_codepage%>nul",
                "endlocal");

        FileUtils.writeStringSafe(
                getLaunchScript().get().getAsFile().toPath(),
                String.join("\r\n", lines),
                StandardCharsets.UTF_8);
    }

    private String escapeBatchScriptArg(String text) {
        text = text.replace("%", "%%");
        if (text.contains(" ")) {
            text = '"' + text + '"';
        }
        return text;
    }

    private void writeLaunchScriptForUnix(List<String> javaCommand) throws IOException {
        var lines = new ArrayList<String>();

        for (var entry : getEnvironment().get().entrySet()) {
            lines.add("export " + escapeShellArg(entry.getKey()) + "=" + escapeShellArg(entry.getValue()));
        }

        Collections.addAll(lines,
                "(cd " + escapeShellArg(getWorkingDirectory().get()) + "; exec "
                        + javaCommand.stream().map(this::escapeShellArg).collect(Collectors.joining(" ")) + ")");

        FileUtils.writeStringSafe(
                getLaunchScript().get().getAsFile().toPath(),
                String.join("\n", lines),
                StandardCharsets.UTF_8);
    }

    private String escapeShellArg(String text) {
        return "'" + text.replace("'", "'\"'\"'") + "'";
    }
}
