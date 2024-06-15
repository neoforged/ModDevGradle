package net.neoforged.moddevgradle.internal;

import net.neoforged.moddevgradle.dsl.InternalModelHelper;
import net.neoforged.moddevgradle.dsl.ModModel;
import net.neoforged.moddevgradle.dsl.NeoForgeExtension;
import net.neoforged.moddevgradle.dsl.RunModel;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import net.neoforged.moddevgradle.internal.utils.IdeDetection;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.SourceSet;
import org.gradle.process.CommandLineArgumentProvider;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;
import org.slf4j.event.Level;
import org.xml.sax.InputSource;

import javax.inject.Inject;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

final class RunUtils {
    private RunUtils() {
    }

    public static String DEV_LAUNCH_GAV = "net.neoforged:DevLaunch:1.0.0";
    public static String DEV_LAUNCH_MAIN_CLASS = "net.neoforged.devlaunch.Main";

    public static String escapeJvmArg(String arg) {
        var escaped = arg.replace("\\", "\\\\").replace("\"", "\\\"");
        if (escaped.contains(" ")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    public static Provider<String> getRequiredType(Project project, RunModel runModel) {
        return runModel.getType().orElse(project.getProviders().provider(() -> {
            throw new GradleException("The run '" + runModel.getName() + "' did not specify a type property");
        }));
    }

    public static AssetProperties loadAssetProperties(File file) {
        Properties assetProperties = new Properties();
        try (var input = new BufferedInputStream(new FileInputStream(file))) {
            assetProperties.load(input);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load asset properties", e);
        }
        if (!assetProperties.containsKey("assets_root")) {
            throw new IllegalStateException("Asset properties file does not contain assets_root");
        }
        if (!assetProperties.containsKey("asset_index")) {
            throw new IllegalStateException("Asset properties file does not contain asset_index");
        }

        return new AssetProperties(
                assetProperties.getProperty("asset_index"),
                assetProperties.getProperty("assets_root")
        );
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

    public static File getArgFile(Provider<Directory> modDevFolder, RunModel run, RunArgFile type) {
        return modDevFolder.get().file(InternalModelHelper.nameOfRun(run, "", type.filename)).getAsFile();
    }

    public enum RunArgFile {
        VMARGS("runVmArgs.txt"),
        PROGRAMARGS("runProgramArgs.txt"),
        LOG4J_CONFIG("log4j2.xml");

        private final String filename;

        RunArgFile(String filename) {
            this.filename = filename;
        }
    }

    public static String getArgFileParameter(RegularFile argFile) {
        return "@" + argFile.getAsFile().getAbsolutePath();
    }

    public static ModFoldersProvider getGradleModFoldersProvider(Project project, Provider<Set<ModModel>> modsProvider, boolean includeUnitTests) {
        var modFoldersProvider = project.getObjects().newInstance(ModFoldersProvider.class);
        modFoldersProvider.getModFolders().set(getModFoldersForGradle(project, modsProvider, includeUnitTests));
        return modFoldersProvider;
    }

    public static ModFoldersProvider getIdeaModFoldersProvider(Project project, @Nullable File outputDirectory, Provider<Set<ModModel>> modsProvider, boolean includeUnitTests) {
        Provider<Map<String, ModFolder>> folders;
        if (outputDirectory != null) {
            folders = buildModFolders(project, modsProvider, includeUnitTests, (sourceSet, output) -> {
                var sourceSetDir = outputDirectory.toPath().resolve(getIdeaOutName(sourceSet));
                output.from(sourceSetDir.resolve("classes"), sourceSetDir.resolve("resources"));
            });
        } else {
            folders = getModFoldersForGradle(project, modsProvider, includeUnitTests);
        }

        var modFoldersProvider = project.getObjects().newInstance(ModFoldersProvider.class);
        modFoldersProvider.getModFolders().set(folders);
        return modFoldersProvider;
    }

    private static String getIdeaOutName(final SourceSet sourceSet) {
        return sourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME) ? "production" : sourceSet.getName();
    }

    private static Provider<Map<String, ModFolder>> getModFoldersForGradle(Project project, Provider<Set<ModModel>> modsProvider, boolean includeUnitTests) {
        return buildModFolders(project, modsProvider, includeUnitTests, (sourceSet, output) -> {
            output.from(sourceSet.getOutput());
        });
    }

    private static Provider<Map<String, ModFolder>> buildModFolders(Project project, Provider<Set<ModModel>> modsProvider, boolean includeUnitTests, BiConsumer<SourceSet, ConfigurableFileCollection> outputFolderResolver) {
        var extension = ExtensionUtils.findExtension(project, NeoForgeExtension.NAME, NeoForgeExtension.class);
        var testedModProvider = extension.getUnitTest().getTestedMod()
                .filter(m -> includeUnitTests)
                .map(Optional::of)
                .orElse(Optional.empty());

        return modsProvider.zip(testedModProvider, ((mods, testedMod) -> mods.stream()
                .collect(Collectors.toMap(ModModel::getName, mod -> {
                    var modFolder = project.getObjects().newInstance(ModFolder.class);
                    modFolder.getFolders().from(InternalModelHelper.getModConfiguration(mod));

                    var sourceSets = mod.getModSourceSets().get();

                    for (var sourceSet : sourceSets) {
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
                }))));
    }

    // TODO: Loom has unit tests for this... Probably a good idea!
    @Language("xpath")
    public static final String IDEA_DELEGATED_BUILD_XPATH = "/project/component[@name='GradleSettings']/option[@name='linkedExternalProjectsSettings']/GradleProjectSettings/option[@name='delegatedBuild']/@value";
    @Language("xpath")
    public static final String IDEA_OUTPUT_XPATH = "/project/component[@name='ProjectRootManager']/output/@url";

    /**
     * Returns the configured output directory, only if "Build and run using" is set to "IDEA".
     * In other cases, returns {@code null}.
     */
    @Nullable
    static File getIntellijOutputDirectory(Project project) {
        var ideaDir = IdeDetection.getIntellijProjectDir(project);
        if (ideaDir == null) {
            return null;
        }

        // Check if IntelliJ is configured to build with Gradle.
        var gradleXml = new File(ideaDir, "gradle.xml");
        var delegatedBuild = evaluateXPath(gradleXml, IDEA_DELEGATED_BUILD_XPATH);
        if (!"false".equals(delegatedBuild)) {
            return null;
        }

        // Find configured output path
        var miscXml = new File(ideaDir, "misc.xml");
        String outputDirUrl = evaluateXPath(miscXml, IDEA_OUTPUT_XPATH);
        if (outputDirUrl == null) {
            // Apparently IntelliJ defaults to out/ now?
            outputDirUrl = "file://$PROJECT_DIR$/out";
        }

        outputDirUrl = outputDirUrl.replace("$PROJECT_DIR$", project.getProjectDir().getAbsolutePath());
        outputDirUrl = outputDirUrl.replaceAll("^file:", "");

        // The output dir can start with something like "//C:\"; File can handle it.
        return new File(outputDirUrl);
    }

    @Nullable
    private static String evaluateXPath(File file, @Language("xpath") String expression) {
        try (var fis = new FileInputStream(file)) {
            String result = XPathFactory.newInstance().newXPath().evaluate(expression, new InputSource(fis));
            return result.isBlank() ? null : result;
        } catch (FileNotFoundException | XPathExpressionException ignored) {
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to evaluate xpath " + expression + " on file " + file, e);
        }
    }
}

record AssetProperties(String assetIndex, String assetsRoot) {
}

abstract class ModFoldersProvider implements CommandLineArgumentProvider {
    @Inject
    public ModFoldersProvider() {
    }

    @Nested
    abstract MapProperty<String, ModFolder> getModFolders();

    @Internal
    public String getArgument() {
        var stringModFolderMap = getModFolders().get();
        return "-Dfml.modFolders=%s".formatted(
                stringModFolderMap.entrySet().stream()
                        .<String>mapMulti((entry, output) -> {
                            for (var directory : entry.getValue().getFolders()) {
                                // Resources
                                output.accept(entry.getKey() + "%%" + directory.getAbsolutePath());
                            }
                        })
                        .collect(Collectors.joining(File.pathSeparator)));
    }

    @Override
    public Iterable<String> asArguments() {
        return List.of(getArgument());
    }
}

abstract class ModFolder {
    @Inject
    public ModFolder() {
    }

    @InputFiles
    @Classpath
    abstract ConfigurableFileCollection getFolders();
}
