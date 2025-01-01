package net.neoforged.moddevgradle.internal;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.xml.stream.XMLStreamException;
import net.neoforged.elc.configs.GradleLaunchConfig;
import net.neoforged.elc.configs.JavaApplicationLaunchConfig;
import net.neoforged.elc.configs.LaunchConfig;
import net.neoforged.elc.configs.LaunchGroup;
import net.neoforged.moddevgradle.dsl.ModModel;
import net.neoforged.moddevgradle.dsl.RunModel;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.eclipse.model.Classpath;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.Library;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides integration with Eclipse Buildship and VSCode extensions based on it.
 */
sealed class EclipseIntegration extends IdeIntegration permits VsCodeIntegration {
    private static final Logger LOG = LoggerFactory.getLogger(EclipseIntegration.class);

    protected final EclipseModel eclipseModel;

    protected EclipseIntegration(Project project, Branding branding) {
        super(project, branding);
        this.eclipseModel = getOrCreateEclipseModel(project);
        LOG.debug("Configuring Eclipse model for Eclipse project '{}'.", eclipseModel.getProject().getName());

        // Make sure our post-sync task runs on Eclipse project reload
        eclipseModel.synchronizationTasks(ideSyncTask);
    }

    /**
     * Attach a source artifact to a binary artifact if the IDE supports it.
     *
     * @param jarToSourceJarMapping Maps a classpath location containing classes to their source location.
     *                              Locations are usually JAR files but may be folders.
     */
    @Override
    public void attachSources(Map<Provider<RegularFile>, Provider<RegularFile>> jarToSourceJarMapping) {
        var fileClasspath = eclipseModel.getClasspath().getFile();
        fileClasspath.whenMerged((Classpath classpath) -> {
            for (var mapping : jarToSourceJarMapping.entrySet()) {
                var classesPath = mapping.getKey().get().getAsFile();
                var sourcesPath = mapping.getValue().get().getAsFile();

                for (var entry : classpath.getEntries()) {
                    if (entry instanceof Library library && classesPath.equals(new File(library.getPath()))) {
                        library.setSourcePath(classpath.fileReference(sourcesPath));
                    }
                }
            }
        });
    }

    @Override
    public void configureRuns(Map<RunModel, TaskProvider<PrepareRun>> prepareRunTasks,
            Iterable<RunModel> runs) {
        // Set up runs if running under buildship and in VS Code
        project.afterEvaluate(ignored -> {
            for (var run : runs) {
                var prepareTask = prepareRunTasks.get(run).get();
                addEclipseLaunchConfiguration(project, run, prepareTask);
            }
        });
    }

    @Override
    public void configureTesting(Provider<Set<ModModel>> loadedMods,
            Provider<ModModel> testedMod,
            Provider<Directory> runArgsDir,
            File gameDirectory,
            Provider<RegularFile> programArgsFile,
            Provider<RegularFile> vmArgsFile) {
        // Eclipse has no concept of JUnit run templates. We cannot configure VM args or similar for all JUnit runs.
    }

    private static EclipseModel getOrCreateEclipseModel(Project project) {
        // Set up stuff for Eclipse
        var eclipseModel = ExtensionUtils.findExtension(project, "eclipse", EclipseModel.class);
        if (eclipseModel == null) {
            project.getPlugins().apply(EclipsePlugin.class);
            eclipseModel = ExtensionUtils.findExtension(project, "eclipse", EclipseModel.class);
            if (eclipseModel == null) {
                throw new GradleException("Even after applying the Eclipse plugin, no 'eclipse' extension was present!");
            }
        }
        return eclipseModel;
    }

    private void addEclipseLaunchConfiguration(Project project,
            RunModel run,
            PrepareRun prepareTask) {
        if (!prepareTask.getEnabled()) {
            LOG.info("Not creating Eclipse run {} since its prepare task {} is disabled", run, prepareTask);
            return;
        }
        if (!shouldGenerateConfigFor(run)) {
            LOG.info("Not creating Eclipse run {} since it's explicitly disabled", run);
            return;
        }

        // Grab the eclipse model so we can extend it. -> Done on the root project so that the model is available to all subprojects.
        // And so that post sync tasks are only run once for all subprojects.

        var runIdeName = run.getIdeName().get();
        var launchConfigName = runIdeName;
        var eclipseProjectName = Objects.requireNonNullElse(eclipseModel.getProject().getName(), project.getName());

        // If the user wants to run tasks before the actual execution, we create a launch group to facilitate that
        if (!run.getTasksBefore().isEmpty()) {
            // Rename the main launch to "Run " ...
            launchConfigName = "Run " + runIdeName;

            // Creates a launch config to run the preparation tasks
            var prepareRunConfig = GradleLaunchConfig.builder(eclipseProjectName)
                    .tasks(run.getTasksBefore().stream().map(task -> task.get().getPath()).toArray(String[]::new))
                    .build();
            var prepareRunLaunchName = "Prepare " + runIdeName;
            writeEclipseLaunchConfig(project, prepareRunLaunchName, prepareRunConfig);

            // This is the launch group that will first launch Gradle, and then the game
            var withGradleTasksConfig = LaunchGroup.builder()
                    .entry(LaunchGroup.entry(prepareRunLaunchName)
                            .enabled(true)
                            .adoptIfRunning(false)
                            .mode(LaunchGroup.Mode.RUN)
                            // See https://github.com/eclipse/buildship/issues/1272
                            // for why we cannot just wait for termination
                            .action(LaunchGroup.Action.delay(2)))
                    .entry(LaunchGroup.entry(launchConfigName)
                            .enabled(true)
                            .adoptIfRunning(false)
                            .mode(LaunchGroup.Mode.INHERIT)
                            .action(LaunchGroup.Action.none()))
                    .build();
            writeEclipseLaunchConfig(project, runIdeName, withGradleTasksConfig);
        }

        // This is the actual main launch configuration that launches the game
        var modFoldersProvider = getModFoldersProvider(project, run.getLoadedMods(), null);
        var config = JavaApplicationLaunchConfig.builder(eclipseProjectName)
                .vmArgs(
                        RunUtils.escapeJvmArg(RunUtils.getArgFileParameter(prepareTask.getVmArgsFile().get())),
                        RunUtils.escapeJvmArg(modFoldersProvider.getArgument()))
                .args(RunUtils.escapeJvmArg(RunUtils.getArgFileParameter(prepareTask.getProgramArgsFile().get())))
                .envVar(RunUtils.replaceModClassesEnv(run, modFoldersProvider))
                .workingDirectory(run.getGameDirectory().get().getAsFile().getAbsolutePath())
                .build(RunUtils.DEV_LAUNCH_MAIN_CLASS);
        writeEclipseLaunchConfig(project, launchConfigName, config);
    }

    protected static ModFoldersProvider getModFoldersProvider(Project project,
            Provider<Set<ModModel>> modsProvider,
            @Nullable Provider<ModModel> testedMod) {
        var folders = RunUtils.buildModFolders(project, modsProvider, testedMod, (sourceSet, output) -> {
            output.from(RunUtils.findSourceSetProject(project, sourceSet).getProjectDir().toPath()
                    .resolve("bin")
                    .resolve(sourceSet.getName()));
        });

        var modFoldersProvider = project.getObjects().newInstance(ModFoldersProvider.class);
        modFoldersProvider.getModFolders().set(folders);
        return modFoldersProvider;
    }

    private static void writeEclipseLaunchConfig(Project project, String name, LaunchConfig config) {
        var file = project.file(".eclipse/configurations/" + name + ".launch");
        file.getParentFile().mkdirs();
        try (var writer = new FileWriter(file, false)) {
            config.write(writer);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write launch file: " + file, e);
        } catch (XMLStreamException e) {
            throw new RuntimeException("Failed to write launch file: " + file, e);
        }
    }
}
