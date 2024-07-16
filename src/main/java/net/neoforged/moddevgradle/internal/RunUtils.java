package net.neoforged.moddevgradle.internal;

import net.neoforged.elc.configs.JavaApplicationLaunchConfig;
import net.neoforged.elc.configs.LaunchConfig;
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
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.process.CommandLineArgumentProvider;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.gradle.ext.ModuleRef;
import org.slf4j.event.Level;
import org.xml.sax.InputSource;

import javax.inject.Inject;
import javax.xml.stream.XMLStreamException;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

final class RunUtils {
    private RunUtils() {
    }

    public static final String DEV_LAUNCH_GAV = "net.neoforged:DevLaunch:1.0.1";
    public static final String DEV_LAUNCH_MAIN_CLASS = "net.neoforged.devlaunch.Main";

    public static String escapeJvmArg(final String arg) {
        final String escaped = arg.replace("\\", "\\\\").replace("\"", "\\\"");
        if (escaped.contains(" ")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    public static Provider<String> getRequiredType(final Project project, final RunModel runModel) {
        return runModel.getType().orElse(project.getProviders().provider(new Callable<String>() {
            @Override
            public String call() throws Exception {
                throw new GradleException("The run '" + runModel.getName() + "' did not specify a type property");
            }
        }));
    }

    public static AssetProperties loadAssetProperties(final File file) {
        final Properties assetProperties = new Properties();
        try (final BufferedInputStream input = new BufferedInputStream(new FileInputStream(file))) {
            assetProperties.load(input);
        } catch (final IOException e) {
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

    public static void writeLog4j2Configuration(final Level rootLevel, final Path destination) throws IOException {
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

    public static File getArgFile(final Provider<Directory> modDevFolder, final RunModel run, final RunArgFile type) {
        return modDevFolder.get().file(InternalModelHelper.nameOfRun(run, "", type.filename)).getAsFile();
    }

    public enum RunArgFile {
        VMARGS("runVmArgs.txt"),
        PROGRAMARGS("runProgramArgs.txt"),
        LOG4J_CONFIG("log4j2.xml");

        private final String filename;

        RunArgFile(final String filename) {
            this.filename = filename;
        }
    }

    public static String getArgFileParameter(final RegularFile argFile) {
        return "@" + argFile.getAsFile().getAbsolutePath();
    }

    public static ModFoldersProvider getGradleModFoldersProvider(final Project project, final Provider<Set<ModModel>> modsProvider, final boolean includeUnitTests) {
        final ModFoldersProvider modFoldersProvider = project.getObjects().newInstance(ModFoldersProvider.class);
        modFoldersProvider.getModFolders().set(getModFoldersForGradle(project, modsProvider, includeUnitTests));
        return modFoldersProvider;
    }

    private static Project findSourceSetProject(final Project someProject, final SourceSet sourceSet) {
        for (final SourceSet s : ExtensionUtils.getSourceSets(someProject)) {
            if (s == sourceSet) {
                return someProject;
            }
        }
        // The code below will break with cross-project isolation, but that's expected when depending on other source sets!
        for (final Project p : someProject.getRootProject().getAllprojects()) {
            final SourceSetContainer sourceSets = ExtensionUtils.findSourceSets(p);
            if (sourceSets != null) {
                for (final SourceSet s : sourceSets) {
                    if (s == sourceSet) {
                        return p;
                    }
                }
            }
        }
        throw new IllegalArgumentException("Could not find project for source set " + someProject);
    }

    public static ModFoldersProvider getIdeaModFoldersProvider(final Project project,
                                                               @Nullable final Function<Project, File> outputDirectory,
                                                               final Provider<Set<ModModel>> modsProvider,
                                                               final boolean includeUnitTests) {
        final Provider<Map<String, ModFolder>> folders;
        if (outputDirectory != null) {
            folders = buildModFolders(project, modsProvider, includeUnitTests, new BiConsumer<SourceSet, ConfigurableFileCollection>() {
                @Override
                public void accept(SourceSet sourceSet, ConfigurableFileCollection output) {
                    final Path sourceSetDir = outputDirectory.apply(findSourceSetProject(project, sourceSet)).toPath().resolve(getIdeaOutName(sourceSet));
                    output.from(sourceSetDir.resolve("classes"), sourceSetDir.resolve("resources"));
                }
            });
        } else {
            folders = getModFoldersForGradle(project, modsProvider, includeUnitTests);
        }

        final ModFoldersProvider modFoldersProvider = project.getObjects().newInstance(ModFoldersProvider.class);
        modFoldersProvider.getModFolders().set(folders);
        return modFoldersProvider;
    }

    public static void writeEclipseLaunchConfig(final Project project, final String name, final LaunchConfig config) {
        final File file = project.file(".eclipse/configurations/" + name + ".launch");
        file.getParentFile().mkdirs();
        try (final FileWriter writer = new FileWriter(file, false)) {
            config.write(writer);
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to write launch file: " + file, e);
        } catch (final XMLStreamException e) {
            throw new RuntimeException("Failed to write launch file: " + file, e);
        }
    }

    public static ModFoldersProvider getEclipseModFoldersProvider(final Project project,
                                                                  final Provider<Set<ModModel>> modsProvider,
                                                                  final boolean includeUnitTests) {
        final Provider<Map<String, ModFolder>> folders = buildModFolders(project, modsProvider, includeUnitTests, new BiConsumer<SourceSet, ConfigurableFileCollection>() {
            @Override
            public void accept(SourceSet sourceSet, ConfigurableFileCollection output) {
                output.from(findSourceSetProject(project, sourceSet).getProjectDir().toPath()
                        .resolve("bin")
                        .resolve(sourceSet.getName()));
            }
        });

        final ModFoldersProvider modFoldersProvider = project.getObjects().newInstance(ModFoldersProvider.class);
        modFoldersProvider.getModFolders().set(folders);
        return modFoldersProvider;
    }

    private static String getIdeaOutName(final SourceSet sourceSet) {
        return sourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME) ? "production" : sourceSet.getName();
    }

    private static Provider<Map<String, ModFolder>> getModFoldersForGradle(final Project project, final Provider<Set<ModModel>> modsProvider, final boolean includeUnitTests) {
        return buildModFolders(project, modsProvider, includeUnitTests, new BiConsumer<SourceSet, ConfigurableFileCollection>() {
            @Override
            public void accept(SourceSet sourceSet, ConfigurableFileCollection output) {
                output.from(sourceSet.getOutput());
            }
        });
    }

    private static Provider<Map<String, ModFolder>> buildModFolders(final Project project, final Provider<Set<ModModel>> modsProvider, final boolean includeUnitTests, final BiConsumer<SourceSet, ConfigurableFileCollection> outputFolderResolver) {
        final NeoForgeExtension extension = ExtensionUtils.findExtension(project, NeoForgeExtension.NAME, NeoForgeExtension.class);
        final Provider<Optional<ModModel>> testedModProvider = extension.getUnitTest().getTestedMod()
                .filter(new Spec<ModModel>() {
                    @Override
                    public boolean isSatisfiedBy(ModModel m) {
                        return includeUnitTests;
                    }
                })
                .map(Optional::of)
                .orElse(Optional.empty());

        return modsProvider.zip(testedModProvider, (new BiFunction<Set<ModModel>, Optional<ModModel>, Map<String, ModFolder>>() {
            @Override
            public Map<String, ModFolder> apply(Set<ModModel> mods, Optional<ModModel> testedMod) {
                return mods.stream()
                        .collect(Collectors.toMap(ModModel::getName, new Function<ModModel, ModFolder>() {
                            @Override
                            public ModFolder apply(ModModel mod) {
                                final ModFolder modFolder = project.getObjects().newInstance(ModFolder.class);
                                modFolder.getFolders().from(InternalModelHelper.getModConfiguration(mod));

                                final List<SourceSet> sourceSets = mod.getModSourceSets().get();

                                for (final SourceSet sourceSet : sourceSets) {
                                    outputFolderResolver.accept(sourceSet, modFolder.getFolders());
                                }

                                // Add the test source set to the mod under test and if unit tests are enabled
                                if (testedMod.isPresent() && testedMod.get() == mod) {
                                    final SourceSet testSourceSet = ExtensionUtils.getSourceSets(project).findByName(SourceSet.TEST_SOURCE_SET_NAME);
                                    if (testSourceSet != null && !sourceSets.contains(testSourceSet)) {
                                        outputFolderResolver.accept(testSourceSet, modFolder.getFolders());
                                    }
                                }

                                return modFolder;
                            }
                        }));
            }
        }));
    }

    // TODO: Loom has unit tests for this... Probably a good idea!
    @Language("xpath")
    public static final String IDEA_DELEGATED_BUILD_XPATH = "/project/component[@name='GradleSettings']/option[@name='linkedExternalProjectsSettings']/GradleProjectSettings/option[@name='delegatedBuild']/@value";
    @Language("xpath")
    public static final String IDEA_OUTPUT_XPATH = "/project/component[@name='ProjectRootManager']/output/@url";

    /**
     * Returns a function that maps a project to the configured output directory,
     * only if "Build and run using" is set to "IDEA".
     * In other cases, returns {@code null}.
     */
    @Nullable
    static Function<Project, File> getIntellijOutputDirectory(final Project someProject) {
        final File ideaDir = IdeDetection.getIntellijProjectDir(someProject);
        if (ideaDir == null) {
            return null;
        }

        // Check if IntelliJ is configured to build with Gradle.
        final File gradleXml = new File(ideaDir, "gradle.xml");
        final String delegatedBuild = evaluateXPath(gradleXml, IDEA_DELEGATED_BUILD_XPATH);
        if (!"false".equals(delegatedBuild)) {
            return null;
        }

        // Find configured output path
        final File miscXml = new File(ideaDir, "misc.xml");
        String outputDirUrl = evaluateXPath(miscXml, IDEA_OUTPUT_XPATH);
        if (outputDirUrl == null) {
            // Apparently IntelliJ defaults to out/ now?
            outputDirUrl = "file://$PROJECT_DIR$/out";
        }

        // The output dir can start with something like "//C:\"; File can handle it.
        outputDirUrl = outputDirUrl.replaceAll("^file:", "");

        final String outputDirTemplate = outputDirUrl;
        return new Function<Project, File>() {
            @Override
            public File apply(Project p) {
                return new File(outputDirTemplate.replace("$PROJECT_DIR$", p.getProjectDir().getAbsolutePath()));
            }
        };
    }

    @Nullable
    private static String evaluateXPath(final File file, @Language("xpath") final String expression) {
        try (final FileInputStream fis = new FileInputStream(file)) {
            final String result = XPathFactory.newInstance().newXPath().evaluate(expression, new InputSource(fis));
            return result.isBlank() ? null : result;
        } catch (final FileNotFoundException | XPathExpressionException ignored) {
            return null;
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to evaluate xpath " + expression + " on file " + file, e);
        }
    }

    /**
     * Convert a project and source set to an IntelliJ module name.
     * Do not use {@link ModuleRef} as it does not correctly handle projects with a space in their name!
     */
    public static String getIntellijModuleName(final Project project, final SourceSet sourceSet) {
        final StringBuilder moduleName = new StringBuilder();
        // The `replace` call here is our bug fix compared to ModuleRef!
        // The actual IDEA logic is more complicated, but this should cover the majority of use cases.
        // See https://github.com/JetBrains/intellij-community/blob/a32fd0c588a6da11fd6d5d2fb0362308da3206f3/plugins/gradle/src/org/jetbrains/plugins/gradle/service/project/GradleProjectResolverUtil.java#L205
        // which calls https://github.com/JetBrains/intellij-community/blob/a32fd0c588a6da11fd6d5d2fb0362308da3206f3/platform/util-rt/src/com/intellij/util/PathUtilRt.java#L120
        moduleName.append(project.getRootProject().getName().replace(" ", "_"));
        if (project != project.getRootProject()) {
            moduleName.append(project.getPath().replaceAll(":", "."));
        }
        moduleName.append(".");
        moduleName.append(sourceSet.getName());
        return moduleName.toString();
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
    public final String getArgument() {
        final Map<String, ModFolder> stringModFolderMap = getModFolders().get();
        return "-Dfml.modFolders=%s".formatted(
                stringModFolderMap.entrySet().stream()
                        .<String>mapMulti(new BiConsumer<Map.Entry<String, ModFolder>, Consumer<String>>() {
                            @Override
                            public void accept(Map.Entry<String, ModFolder> entry, Consumer<String> output) {
                                for (final File directory : entry.getValue().getFolders()) {
                                    // Resources
                                    output.accept(entry.getKey() + "%%" + directory.getAbsolutePath());
                                }
                            }
                        })
                        .collect(Collectors.joining(File.pathSeparator)));
    }

    @Override
    public final Iterable<String> asArguments() {
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
