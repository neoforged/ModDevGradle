package net.neoforged.moddevgradle.internal;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.neoforged.moddevgradle.dsl.RunModel;
import net.neoforged.vsclc.BatchedLaunchWriter;
import net.neoforged.vsclc.attribute.ConsoleType;
import net.neoforged.vsclc.attribute.PathLike;
import net.neoforged.vsclc.attribute.ShortCmdBehaviour;
import net.neoforged.vsclc.writer.WritingMode;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides integration with Eclipse Buildship and VSCode extensions based on it.
 */
final class VsCodeIntegration extends EclipseIntegration {
    private static final Logger LOG = LoggerFactory.getLogger(VsCodeIntegration.class);

    VsCodeIntegration(Project project, Branding branding) {
        super(project, branding);
    }

    @Override
    public void configureRuns(Map<RunModel, TaskProvider<PrepareRun>> prepareRunTasks,
            Iterable<RunModel> runs) {
        // Set up runs if running under buildship and in VS Code
        project.afterEvaluate(ignored -> {
            var launchWriter = new BatchedLaunchWriter(WritingMode.MODIFY_CURRENT);

            for (var run : runs) {
                var prepareTask = prepareRunTasks.get(run).get();
                addVscodeLaunchConfiguration(project, run, prepareTask, launchWriter);
            }

            try {
                launchWriter.writeToLatestJson(project.getRootDir().toPath());
            } catch (final IOException e) {
                throw new RuntimeException("Failed to write VSCode launch files", e);
            }
        });
    }

    private void addVscodeLaunchConfiguration(Project project,
            RunModel run,
            PrepareRun prepareTask,
            BatchedLaunchWriter launchWriter) {
        if (!prepareTask.getEnabled()) {
            LOG.info("Not creating VSCode run {} since its prepare task {} is disabled", run, prepareTask);
            return;
        }
        if (!shouldGenerateConfigFor(run)) {
            LOG.info("Not creating VSCode run {} since it's explicitly disabled", run);
            return;
        }

        var runIdeName = run.getIdeName().get();
        var eclipseProjectName = Objects.requireNonNullElse(eclipseModel.getProject().getName(), project.getName());

        // If the user wants to run tasks before the actual execution, we attach them to autoBuildTasks
        // Missing proper support - https://github.com/microsoft/vscode-java-debug/issues/1106
        if (!run.getTasksBefore().isEmpty()) {
            eclipseModel.autoBuildTasks(run.getTasksBefore().toArray());
        }

        var modFoldersProvider = getModFoldersProvider(project, run.getLoadedMods(), null);
        launchWriter.createGroup("Mod Development - " + project.getName(), WritingMode.REMOVE_EXISTING)
                .createLaunchConfiguration()
                .withName(runIdeName)
                .withProjectName(eclipseProjectName)
                .withArguments(List.of(RunUtils.getArgFileParameter(prepareTask.getProgramArgsFile().get())))
                .withAdditionalJvmArgs(List.of(RunUtils.getArgFileParameter(prepareTask.getVmArgsFile().get()),
                        modFoldersProvider.getArgument()))
                .withEnvironmentVariables(RunUtils.replaceModClassesEnv(run, modFoldersProvider))
                .withMainClass(RunUtils.DEV_LAUNCH_MAIN_CLASS)
                .withShortenCommandLine(ShortCmdBehaviour.NONE)
                .withConsoleType(ConsoleType.INTERNAL_CONSOLE)
                .withCurrentWorkingDirectory(PathLike.ofNio(run.getGameDirectory().get().getAsFile().toPath()));
    }
}
