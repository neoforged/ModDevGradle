package net.neoforged.moddevgradle.internal;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import net.neoforged.moddevgradle.dsl.InternalModelHelper;
import net.neoforged.moddevgradle.dsl.ModModel;
import net.neoforged.moddevgradle.dsl.RunModel;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import net.neoforged.moddevgradle.internal.utils.OperatingSystem;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.SourceSet;
import org.gradle.process.CommandLineArgumentProvider;
import org.jetbrains.annotations.Nullable;
import org.slf4j.event.Level;

final class RunUtils {
    private RunUtils() {}

    public static final String DEV_LAUNCH_GAV = "net.neoforged:DevLaunch:1.0.2"; // renovate
    public static final String DEV_LAUNCH_MAIN_CLASS = "net.neoforged.devlaunch.Main";
    public static final String DEV_LOGIN_GAV = "net.covers1624:DevLogin:0.1.0.5"; // renovate
    public static final String DEV_LOGIN_MAIN_CLASS = "net.covers1624.devlogin.DevLogin";

    public static String escapeJvmArg(String arg) {
        var escaped = arg.replace("\\", "\\\\").replace("\"", "\\\"");
        // # is used for line comments in arg files and should be quoted to avoid misinterpretation
        if (escaped.contains(" ") || escaped.contains("#")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    public static Provider<String> getRequiredType(Project project, RunModel runModel) {
        return runModel.getType().orElse(project.getProviders().provider(() -> {
            throw new GradleException("The run '" + runModel.getName() + "' did not specify a type property");
        }));
    }

    public static void writeLog4j2Configuration(Level rootLevel, Path destination) throws IOException {
        Files.writeString(destination, """
                <?xml version="1.0" encoding="UTF-8"?>
                <Configuration status="warn" shutdownHook="disable">
                    <filters>
                        <ThresholdFilter level="WARN" onMatch="ACCEPT" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="NETWORK_PACKETS" onMatch="${sys:forge.logging.marker.networking:-DENY}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="CLASSLOADING" onMatch="${sys:forge.logging.marker.classloading:-DENY}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="LAUNCHPLUGIN" onMatch="${sys:forge.logging.marker.launchplugin:-DENY}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="CLASSDUMP" onMatch="${sys:forge.logging.marker.classdump:-DENY}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="AXFORM" onMatch="${sys:forge.logging.marker.axform:-DENY}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="EVENTBUS" onMatch="${sys:forge.logging.marker.eventbus:-DENY}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="DISTXFORM" onMatch="${sys:forge.logging.marker.distxform:-DENY}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="SCAN" onMatch="${sys:forge.logging.marker.scan:-DENY}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="REGISTRIES" onMatch="${sys:forge.logging.marker.registries:-DENY}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="REGISTRYDUMP" onMatch="${sys:forge.logging.marker.registrydump:-DENY}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="SPLASH" onMatch="${sys:forge.logging.marker.splash:-DENY}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="RESOURCE-CACHE" onMatch="${sys:forge.logging.marker.resource.cache:-DENY}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="FORGEMOD" onMatch="${sys:forge.logging.marker.forgemod:-NEUTRAL}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="LOADING" onMatch="${sys:forge.logging.marker.loading:-NEUTRAL}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="CORE" onMatch="${sys:forge.logging.marker.core:-NEUTRAL}" onMismatch="NEUTRAL"/>
                    </filters>
                    <Appenders>
                        <Console name="Console">
                            <PatternLayout>
                                <LoggerNamePatternSelector defaultPattern="%highlightForge{[%d{HH:mm:ss}] [%t/%level] [%c{2.}/%markerSimpleName]: %minecraftFormatting{%msg{nolookup}}%n%tEx}">
                                    <!-- don't include the full logger name for Mojang's logs since they use full class names and it's very verbose -->
                                    <PatternMatch key="net.minecraft." pattern="%highlightForge{[%d{HH:mm:ss}] [%t/%level] [minecraft/%logger{1}]: %minecraftFormatting{%msg{nolookup}}%n%tEx}"/>
                                    <PatternMatch key="com.mojang." pattern="%highlightForge{[%d{HH:mm:ss}] [%t/%level] [mojang/%logger{1}]: %minecraftFormatting{%msg{nolookup}}%n%tEx}"/>
                                </LoggerNamePatternSelector>
                            </PatternLayout>
                        </Console>
                        <Queue name="ServerGuiConsole" ignoreExceptions="true">
                            <PatternLayout>
                                <LoggerNamePatternSelector defaultPattern="[%d{HH:mm:ss}] [%t/%level] [%c{2.}/%markerSimpleName]: %minecraftFormatting{%msg{nolookup}}{strip}%n">
                                    <!-- don't include the full logger name for Mojang's logs since they use full class names and it's very verbose -->
                                    <PatternMatch key="net.minecraft." pattern="[%d{HH:mm:ss}] [%t/%level] [minecraft/%logger{1}]: %minecraftFormatting{%msg{nolookup}}{strip}%n"/>
                                    <PatternMatch key="com.mojang." pattern="[%d{HH:mm:ss}] [%t/%level] [mojang/%logger{1}]: %minecraftFormatting{%msg{nolookup}}{strip}%n"/>
                                </LoggerNamePatternSelector>
                            </PatternLayout>
                        </Queue>
                        <RollingRandomAccessFile name="File" fileName="logs/latest.log" filePattern="logs/%d{yyyy-MM-dd}-%i.log.gz">
                            <PatternLayout pattern="[%d{ddMMMyyyy HH:mm:ss.SSS}] [%t/%level] [%logger/%markerSimpleName]: %minecraftFormatting{%msg{nolookup}}{strip}%n%xEx"/>
                            <Policies>
                                <TimeBasedTriggeringPolicy/>
                                <OnStartupTriggeringPolicy/>
                            </Policies>
                            <DefaultRolloverStrategy max="99" fileIndex="min"/>
                        </RollingRandomAccessFile>
                        <RollingRandomAccessFile name="DebugFile" fileName="logs/debug.log" filePattern="logs/debug-%i.log.gz">
                            <PatternLayout pattern="[%d{ddMMMyyyy HH:mm:ss.SSS}] [%t/%level] [%logger/%markerSimpleName]: %minecraftFormatting{%msg{nolookup}}{strip}%n%xEx"/>
                            <Policies>
                                <OnStartupTriggeringPolicy/>
                                <SizeBasedTriggeringPolicy size="200MB"/>
                            </Policies>
                            <DefaultRolloverStrategy max="5" fileIndex="min"/>
                        </RollingRandomAccessFile>
                    </Appenders>
                    <Loggers>
                        <!-- make sure mojang's logging is set to 'info' so that their LOGGER.isDebugEnabled() behavior isn't active -->
                        <Logger level="${sys:forge.logging.mojang.level:-info}" name="com.mojang"/>
                        <Logger level="${sys:forge.logging.mojang.level:-info}" name="net.minecraft"/>
                        <Logger level="${sys:forge.logging.classtransformer.level:-info}" name="cpw.mods.modlauncher.ClassTransformer"/>

                        <!-- Netty reflects into JDK internals, and it's causing useless DEBUG-level error stacktraces. We just ignore them -->
                        <Logger name="io.netty.util.internal.PlatformDependent0">
                            <filters>
                                <RegexFilter regex="^direct buffer constructor: unavailable$" onMatch="DENY" onMismatch="NEUTRAL" />
                                <RegexFilter regex="^jdk\\.internal\\.misc\\.Unsafe\\.allocateUninitializedArray\\(int\\): unavailable$" onMatch="DENY" onMismatch="NEUTRAL" />
                            </filters>
                        </Logger>

                        <Root level="$ROOTLEVEL$">
                            <AppenderRef ref="Console" />
                            <AppenderRef ref="ServerGuiConsole" level="${sys:forge.logging.console.level:-info}"/>
                            <AppenderRef ref="File" level="${sys:forge.logging.file.level:-info}"/>
                            <AppenderRef ref="DebugFile" />
                        </Root>
                    </Loggers>
                </Configuration>
                """.replace("$ROOTLEVEL$", rootLevel.name()));
    }

    public static Provider<RegularFile> getArgFile(Provider<Directory> modDevFolder, RunModel run, RunArgFile type) {
        return modDevFolder.map(dir -> dir.file(InternalModelHelper.nameOfRun(run, "", type.filename)));
    }

    public static Provider<RegularFile> getLaunchScript(Provider<Directory> modDevFolder, RunModel run) {
        var scriptExtension = switch (OperatingSystem.current()) {
            case LINUX, MACOS -> ".sh";
            case WINDOWS -> ".cmd";
        };

        return modDevFolder.map(dir -> dir.file(InternalModelHelper.nameOfRun(run, "run", scriptExtension)));
    }

    public enum RunArgFile {
        VMARGS("runVmArgs.txt"),
        PROGRAMARGS("runProgramArgs.txt"),
        CLASSPATH("runClasspath.txt"),
        LOG4J_CONFIG("log4j2.xml");

        private final String filename;

        RunArgFile(String filename) {
            this.filename = filename;
        }
    }

    public static String getArgFileParameter(RegularFile argFile) {
        return "@" + argFile.getAsFile().getAbsolutePath();
    }

    public static ModFoldersProvider getGradleModFoldersProvider(Project project, Provider<Set<ModModel>> modsProvider, Provider<ModModel> testedMod) {
        var modFoldersProvider = project.getObjects().newInstance(ModFoldersProvider.class);
        modFoldersProvider.getModFolders().set(getModFoldersForGradle(project, modsProvider, testedMod));
        return modFoldersProvider;
    }

    public static Project findSourceSetProject(Project someProject, SourceSet sourceSet) {
        for (var s : ExtensionUtils.getSourceSets(someProject)) {
            if (s == sourceSet) {
                return someProject;
            }
        }
        // The code below will break with cross-project isolation, but that's expected when depending on other source sets!
        for (var p : someProject.getRootProject().getAllprojects()) {
            var sourceSets = ExtensionUtils.findSourceSets(p);
            if (sourceSets != null) {
                for (var s : sourceSets) {
                    if (s == sourceSet) {
                        return p;
                    }
                }
            }
        }
        throw new IllegalArgumentException("Could not find project for source set " + someProject);
    }

    /**
     * In the run model, the environment variable "MOD_CLASSES" is set to the gradle output folders by the legacy plugin,
     * since MDG itself completely ignores run-type specific environment variables.
     * To ensure that in IDE runs, the IDE output folders are used, we replace the MOD_CLASSES environment variable
     * explicitly.
     */
    public static Map<String, String> replaceModClassesEnv(RunModel model, ModFoldersProvider modFoldersProvider) {
        var vars = model.getEnvironment().get();
        if (vars.containsKey("MOD_CLASSES")) {
            final var copy = new HashMap<>(vars);
            copy.put("MOD_CLASSES", modFoldersProvider.getClassesArgument().get());
            return copy;
        }
        return vars;
    }

    public static Provider<Map<String, ModFolder>> getModFoldersForGradle(Project project,
            Provider<Set<ModModel>> modsProvider,
            @Nullable Provider<ModModel> testedMod) {
        return buildModFolders(project, modsProvider, testedMod, (sourceSet, output) -> {
            output.from(sourceSet.getOutput());
        });
    }

    public static Provider<Map<String, ModFolder>> buildModFolders(Project project,
            Provider<Set<ModModel>> modsProvider,
            @Nullable Provider<ModModel> testedModProvider,
            BiConsumer<SourceSet, ConfigurableFileCollection> outputFolderResolver) {
        // Convert it to optional to ensure zip will be called even if no mod under test is present.
        if (testedModProvider == null) {
            testedModProvider = project.provider(() -> null);
        }
        var optionalTestedModProvider = testedModProvider.map(Optional::of).orElse(Optional.empty());

        return modsProvider.zip(optionalTestedModProvider, ((mods, testedMod) -> {
            if (testedMod.isPresent()) {
                if (!mods.contains(testedMod.get())) {
                    throw new InvalidUserCodeException("The tested mod (%s) must be included in the mods loaded for unit testing (%s)."
                            .formatted(testedMod.get().getName(), mods.stream().map(ModModel::getName).toList()));
                }
            }

            return mods.stream()
                    .collect(Collectors.toMap(ModModel::getName, mod -> {
                        var modFolder = project.getObjects().newInstance(ModFolder.class);

                        var sourceSets = mod.getModSourceSets().get();

                        for (int i = 0; i < sourceSets.size(); ++i) {
                            var sourceSet = sourceSets.get(i);
                            if (sourceSets.subList(0, i).contains(sourceSet)) {
                                throw new InvalidUserCodeException("Duplicate source set '%s' in mod '%s'".formatted(sourceSet.getName(), mod.getName()));
                            }
                            outputFolderResolver.accept(sourceSet, modFolder.getFolders());
                        }

                        // Add the test source set to the mod under test and if unit tests are enabled
                        if (testedMod.isPresent() && testedMod.get() == mod) {
                            var testSourceSet = ExtensionUtils.getSourceSets(project).findByName(SourceSet.TEST_SOURCE_SET_NAME);
                            if (testSourceSet != null && !sourceSets.contains(testSourceSet)) {
                                outputFolderResolver.accept(testSourceSet, modFolder.getFolders());
                            }
                        }

                        return modFolder;
                    }));
        }));
    }
}

abstract class ModFoldersProvider implements CommandLineArgumentProvider {
    @Inject
    public ModFoldersProvider() {
        var classesArgument = getModFolders().map(map -> map.entrySet().stream()
                .<String>mapMulti((entry, output) -> {
                    for (var directory : entry.getValue().getFolders()) {
                        // Resources
                        output.accept(entry.getKey() + "%%" + directory.getAbsolutePath());
                    }
                })
                .collect(Collectors.joining(File.pathSeparator)));
        getClassesArgument().set(classesArgument);
    }

    @Nested
    abstract MapProperty<String, ModFolder> getModFolders();

    @Internal
    abstract Property<String> getClassesArgument();

    @Internal
    public String getArgument() {
        return "-Dfml.modFolders=%s".formatted(getClassesArgument().get());
    }

    @Override
    public Iterable<String> asArguments() {
        return List.of(getArgument());
    }
}

abstract class ModFolder {
    @Inject
    public ModFolder() {}

    @InputFiles
    @Classpath
    abstract ConfigurableFileCollection getFolders();
}
