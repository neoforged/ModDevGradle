package net.neoforged.moddevgradle.internal;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import net.neoforged.moddevgradle.internal.utils.FileUtils;
import net.neoforged.moddevgradle.internal.utils.OperatingSystem;
import net.neoforged.moddevgradle.internal.utils.StringUtils;
import net.neoforged.moddevgradle.internal.utils.VersionCapabilitiesInternal;
import net.neoforged.nfrtgradle.DownloadedAssetsReference;
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
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.Nullable;
import org.slf4j.event.Level;

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

    /**
     * A file to use for the {@code log4j2.xml} config file that will be written.
     * If absent, the standard log4j2.xml file produced by {@link RunUtils#writeLog4j2Configuration} will be used.
     */
    @InputFile
    @Optional
    public abstract RegularFileProperty getLog4jConfigFileOverride();

    /**
     * Where the {@code log4j2.xml} config file will be written.
     */
    @OutputFile
    @Optional
    public abstract RegularFileProperty getLog4jConfigFile();

    /**
     * The source of the underlying run type templates. This must contain a single file, which has one of the
     * following supported formats:
     * <ul>
     * <li>NeoForge Userdev config.json file</li>
     * <li>NeoForge Userdev jar file, containing a Userdev config.json file</li>
     * </ul>
     * Subclasses implementing {@link #resolveRunType} can then access this information to get the run type template.
     */
    @Classpath
    public abstract ConfigurableFileCollection getRunTypeTemplatesSource();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getAssetProperties();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
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

    /**
     * Only used when {@link #getRunTypeTemplatesSource()} is empty,
     * to know whether the associated Minecraft version has separate entrypoints for generating resource- and
     * data packs.
     * Defaults to latest.
     */
    @Input
    @Optional
    public abstract Property<VersionCapabilitiesInternal> getVersionCapabilities();

    /**
     * The property that decides whether DevLogin is enabled.
     */
    @Input
    public abstract Property<Boolean> getDevLogin();

    private final ProgramArgsFormat programArgsFormat;

    protected PrepareRunOrTest(ProgramArgsFormat programArgsFormat) {
        this.programArgsFormat = programArgsFormat;
        getVersionCapabilities().convention(VersionCapabilitiesInternal.latest());
        getDevLogin().convention(false);
    }

    protected abstract UserDevRunType resolveRunType(UserDevConfig userDevConfig);

    @Nullable
    protected abstract String resolveMainClass(UserDevRunType runConfig);

    @Internal
    protected abstract boolean isClientDistribution();

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
        var runDir = getGameDirectory().get().getAsFile();
        Files.createDirectories(runDir.toPath());

        // If no NeoForge userdev config is set, we only support Vanilla run types
        UserDevRunType runConfig;
        if (getRunTypeTemplatesSource().isEmpty()) {
            runConfig = resolveRunType(getSimulatedUserDevConfigForVanilla());
        } else {
            var userDevConfig = loadUserDevConfig(getRunTypeTemplatesSource().getSingleFile());
            runConfig = resolveRunType(userDevConfig);
        }

        var mainClass = resolveMainClass(runConfig);
        var devLogin = getDevLogin().get();

        var sysProps = new LinkedHashMap<String, String>();

        // When DevLogin is used, we swap out the main class with the DevLogin one, and add the actual main class as a system property
        if (devLogin && mainClass != null) {
            sysProps.put("devlogin.launch_target", mainClass);
            mainClass = RunUtils.DEV_LOGIN_MAIN_CLASS;
        }

        writeJvmArguments(runConfig, sysProps);
        writeProgramArguments(runConfig, mainClass);
    }

    private UserDevConfig loadUserDevConfig(File userDevFile) {
        // For backwards compatibility reasons we also support loading this from the userdev jar,
        // for NeoForge and Forge versions that didn't publish the configuration as a separate JSON to Maven
        if (userDevFile.getName().endsWith(".jar")) {
            try (var zf = new ZipFile(userDevFile)) {
                var configJson = zf.getEntry("config.json");
                if (configJson != null) {
                    try (var in = zf.getInputStream(configJson)) {
                        return UserDevConfig.from(in);
                    }
                }
            } catch (IOException e) {
                throw new GradleException("Failed to read userdev config file from Jar-file " + userDevFile, e);
            }
        }

        try (var in = Files.newInputStream(userDevFile.toPath())) {
            return UserDevConfig.from(in);
        } catch (Exception e) {
            throw new GradleException("Failed to read userdev config file from " + userDevFile, e);
        }
    }

    private UserDevConfig getSimulatedUserDevConfigForVanilla() {
        var clientArgs = List.of("--gameDir", ".", "--assetIndex", "{asset_index}", "--assetsDir", "{assets_root}", "--accessToken", "NotValid", "--version", "ModDevGradle");
        var commonArgs = List.<String>of();

        var runTypes = new LinkedHashMap<String, UserDevRunType>();
        runTypes.put("client", new UserDevRunType(
                true, "net.minecraft.client.main.Main", clientArgs, List.of(), Map.of(), Map.of()));
        runTypes.put("server", new UserDevRunType(
                true, "net.minecraft.server.Main", commonArgs, List.of(), Map.of(), Map.of()));

        if (getVersionCapabilities().getOrElse(VersionCapabilitiesInternal.latest()).splitDataRuns()) {
            runTypes.put("clientData", new UserDevRunType(
                    true, "net.minecraft.client.data.Main", commonArgs, List.of(), Map.of(), Map.of()));
            runTypes.put("serverData", new UserDevRunType(
                    true, "net.minecraft.data.Main", commonArgs, List.of(), Map.of(), Map.of()));
        } else {
            runTypes.put("data", new UserDevRunType(
                    true, "net.minecraft.data.Main", commonArgs, List.of(), Map.of(), Map.of()));
        }

        return new UserDevConfig(runTypes);
    }

    private void writeJvmArguments(UserDevRunType runConfig, Map<String, String> additionalProperties) throws IOException {
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

        if (getLog4jConfigFile().isPresent()) {
            var log4jConfigFile = getLog4jConfigFile().get().getAsFile();
            if (getLog4jConfigFileOverride().isPresent()) {
                Files.copy(getLog4jConfigFileOverride().get().getAsFile().toPath(), log4jConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                RunUtils.writeLog4j2Configuration(getGameLogLevel().get(), log4jConfigFile.toPath());
            }
            lines.add(RunUtils.escapeJvmArg("-Dlog4j2.configurationFile=" + log4jConfigFile.getAbsolutePath()));
        }

        for (var prop : runConfig.props().entrySet()) {
            var propValue = prop.getValue();
            if (propValue.equals("{minecraft_classpath_file}")) {
                propValue = getLegacyClasspathFile().getAsFile().get().getAbsolutePath();
            }

            addSystemProp(prop.getKey(), propValue, lines);
        }

        additionalProperties.putAll(getSystemProperties().get());

        for (var entry : additionalProperties.entrySet()) {
            addSystemProp(entry.getKey(), entry.getValue(), lines);
        }

        FileUtils.writeLinesSafe(
                getVmArgsFile().get().getAsFile().toPath(),
                lines,
                // JVM expects default character set
                StringUtils.getNativeCharset());
    }

    private void writeProgramArguments(UserDevRunType runConfig, @Nullable String mainClass) throws IOException {
        var lines = new ArrayList<String>();

        if (mainClass != null) {
            lines.add("# Main Class");
            lines.add(mainClass);
            lines.add("");
        }

        lines.add("# NeoForge Run-Type Program Arguments");
        var assetProperties = DownloadedAssetsReference.loadProperties(getAssetProperties().get().getAsFile());
        List<String> args = runConfig.args();
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
        for (var arg : getProgramArguments().get()) {
            // FML JUnit simply expects one line per argument
            if (programArgsFormat == ProgramArgsFormat.FML_JUNIT) {
                lines.add(arg);
            } else {
                lines.add(RunUtils.escapeJvmArg(arg));
            }
        }

        // For FML JUnit, we need to drop comments + empty lines
        if (programArgsFormat == ProgramArgsFormat.FML_JUNIT) {
            lines.removeIf(line -> {
                line = line.strip();
                return line.isEmpty() || line.startsWith("#");
            });
        }

        FileUtils.writeLinesSafe(
                getProgramArgsFile().get().getAsFile().toPath(),
                lines,
                // FML Junit and DevLaunch (starting in 1.0.1) read this file using UTF-8
                StandardCharsets.UTF_8);
    }

    private static void addSystemProp(String name, String value, List<String> lines) {
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
