package net.neoforged.moddevgradle.internal;

import net.neoforged.moddevgradle.internal.utils.FileUtils;
import net.neoforged.moddevgradle.internal.utils.OperatingSystem;
import net.neoforged.moddevgradle.internal.utils.StringUtils;
import org.gradle.api.DefaultTask;
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
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.Nullable;
import org.slf4j.event.Level;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Performs preparation for running the game or running a test.
 *
 * <p><ul>
 * <li>Writes the JVM and program arguments for running the game to args-files.</li>
 * <li>Creates the run folder.</li>
 * </ul>
 */
abstract class PrepareRunOrTest extends DefaultTask {
    @Internal
    public abstract DirectoryProperty getGameDirectory();

    @OutputFile
    public abstract RegularFileProperty getVmArgsFile();

    @OutputFile
    public abstract RegularFileProperty getProgramArgsFile();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getLog4jConfigFile();

    @Classpath
    public abstract ConfigurableFileCollection getNeoForgeModDevConfig();

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

    @Input
    public abstract Property<Level> getGameLogLevel();

    private final ProgramArgsFormat programArgsFormat;

    protected PrepareRunOrTest(final ProgramArgsFormat programArgsFormat) {
        this.programArgsFormat = programArgsFormat;
    }

    protected abstract UserDevRunType resolveRunType(UserDevConfig userDevConfig);

    @Nullable
    protected abstract String resolveMainClass(UserDevRunType runConfig);

    @Internal
    protected abstract boolean isClientDistribution();

    private List<String> getInterpolatedJvmArgs(final UserDevRunType runConfig) {
        final ArrayList<String> result = new ArrayList<String>();
        for (final String jvmArg : runConfig.jvmArgs()) {
            String arg = jvmArg;
            if (arg.equals("{modules}")) {
                arg = getModules().getFiles().stream()
                        .map(File::getAbsolutePath)
                        .collect(Collectors.joining(File.pathSeparator));
            }
            result.add(RunUtils.escapeJvmArg(arg));
        }
        if (isClientDistribution() && OperatingSystem.current() == OperatingSystem.MACOS) {
            // TODO: it might be more future-proof to source this from the platform args in the MC version json
            result.add("-XstartOnFirstThread");
        }
        return result;
    }

    @TaskAction
    public void prepareRun() throws IOException {
        // Make sure the run directory exists
        // IntelliJ refuses to start a run configuration whose working directory does not exist
        final File runDir = getGameDirectory().get().getAsFile();
        Files.createDirectories(runDir.toPath());

        // If no NeoForge userdev config is set, we only support Vanilla run types
        final UserDevRunType runConfig;
        if (getNeoForgeModDevConfig().isEmpty()) {
            runConfig = resolveRunType(getSimulatedUserDevConfigForVanilla());
        } else {
            final UserDevConfig userDevConfig = UserDevConfig.from(getNeoForgeModDevConfig().getSingleFile());
            runConfig = resolveRunType(userDevConfig);
        }

        writeJvmArguments(runConfig);
        writeProgramArguments(runConfig);
    }

    private UserDevConfig getSimulatedUserDevConfigForVanilla() {
        final List<String> clientArgs = List.of("--gameDir", ".", "--assetIndex", "{asset_index}", "--assetsDir", "{assets_root}", "--accessToken", "NotValid", "--version", "ModDevGradle");
        final List<String> commonArgs = List.<String>of();

        return new UserDevConfig("", "", "", List.of(), List.of(), Map.of(
                "client", new UserDevRunType(
                        true, "net.minecraft.client.main.Main", clientArgs, List.of(), true, false, false, false, Map.of(), Map.of()
                ),
                "server", new UserDevRunType(
                        true, "net.minecraft.server.Main", commonArgs, List.of(), false, true, false, false, Map.of(), Map.of()
                ),
                "data", new UserDevRunType(
                        true, "net.minecraft.data.Main", commonArgs, List.of(), false, false, true, false, Map.of(), Map.of()
                )
        ));
    }

    private void writeJvmArguments(final UserDevRunType runConfig) throws IOException {
        final ArrayList<String> lines = new ArrayList<String>();

        lines.addAll(getInterpolatedJvmArgs(runConfig));

        final List<String> userJvmArgs = getJvmArguments().get();
        if (!userJvmArgs.isEmpty()) {
            lines.add("");
            lines.add("# User JVM Arguments");
            for (final String userJvmArg : userJvmArgs) {
                lines.add(RunUtils.escapeJvmArg(userJvmArg));
            }
            lines.add("");
        }

        if (getLog4jConfigFile().isPresent()) {
            final File log4jConfigFile = getLog4jConfigFile().get().getAsFile();
            RunUtils.writeLog4j2Configuration(getGameLogLevel().get(), log4jConfigFile.toPath());
            lines.add(RunUtils.escapeJvmArg("-Dlog4j2.configurationFile=" + log4jConfigFile.getAbsolutePath()));
        }

        for (final Map.Entry<String, String> prop : runConfig.props().entrySet()) {
            String propValue = prop.getValue();
            if (propValue.equals("{minecraft_classpath_file}")) {
                propValue = getLegacyClasspathFile().getAsFile().get().getAbsolutePath();
            }

            addSystemProp(prop.getKey(), propValue, lines);
        }

        for (final Map.Entry<String, String> entry : getSystemProperties().get().entrySet()) {
            addSystemProp(entry.getKey(), entry.getValue(), lines);
        }

        FileUtils.writeLinesSafe(
                getVmArgsFile().get().getAsFile().toPath(),
                lines,
                // JVM expects default character set
                StringUtils.getNativeCharset()
        );
    }

    private void writeProgramArguments(final UserDevRunType runConfig) throws IOException {
        final ArrayList<String> lines = new ArrayList<String>();

        final String mainClass = resolveMainClass(runConfig);
        if (mainClass != null) {
            lines.add("# Main Class");
            lines.add(mainClass);
            lines.add("");
        }

        lines.add("# NeoForge Run-Type Program Arguments");
        final AssetProperties assetProperties = RunUtils.loadAssetProperties(getAssetProperties().get().getAsFile());
        final List<String> args = runConfig.args();
        for (String arg : args) {
            switch (arg) {
                case "{assets_root}" -> arg = Objects.requireNonNull(assetProperties.assetsRoot(), "assets_root");
                case "{asset_index}" -> arg = Objects.requireNonNull(assetProperties.assetIndex(), "asset_index");
            }

            // FML JUnit simply expects one line per argument
            if (programArgsFormat == ProgramArgsFormat.FML_JUNIT) {
                lines.add(arg);
            } else {
                lines.add(RunUtils.escapeJvmArg(arg));
            }
        }
        lines.add("");

        lines.add("# User Supplied Program Arguments");
        for (final String arg : getProgramArguments().get()) {
            // FML JUnit simply expects one line per argument
            if (programArgsFormat == ProgramArgsFormat.FML_JUNIT) {
                lines.add(arg);
            } else {
                lines.add(RunUtils.escapeJvmArg(arg));
            }
        }

        // For FML JUnit, we need to drop comments + empty lines
        if (programArgsFormat == ProgramArgsFormat.FML_JUNIT) {
            lines.removeIf(new Predicate<String>() {
                @Override
                public boolean test(String line) {
                    line = line.strip();
                    return line.isEmpty() || line.startsWith("#");
                }
            });
        }

        FileUtils.writeLinesSafe(
                getProgramArgsFile().get().getAsFile().toPath(),
                lines,
                // FML Junit and DevLaunch (starting in 1.0.1) read this file using UTF-8
                StandardCharsets.UTF_8
        );
    }

    private static void addSystemProp(final String name, final String value, final List<String> lines) {
        lines.add(RunUtils.escapeJvmArg("-D" + name + "=" + value));
    }

    /**
     * Declares the format of the program arguments file being written.
     */
    protected enum ProgramArgsFormat {
        /**
         * Format as used by JVM @-files
         */
        JVM_ARGFILE,
        /**
         * Format as used by FML JUnit, which expects
         * a file to be passed in the system property "fml.junit.argsfile", containing one program argument per line
         * without consideration for escaping.
         */
        FML_JUNIT
    }
}
