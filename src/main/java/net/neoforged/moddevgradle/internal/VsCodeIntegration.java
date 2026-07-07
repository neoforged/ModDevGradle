package net.neoforged.moddevgradle.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.neoforged.moddevgradle.dsl.RunModel;
import net.neoforged.vsclc.BatchedLaunchWriter;
import net.neoforged.vsclc.attribute.ConsoleType;
import net.neoforged.vsclc.attribute.LocatorPathLike;
import net.neoforged.vsclc.attribute.PathLike;
import net.neoforged.vsclc.attribute.ShortCmdBehaviour;
import net.neoforged.vsclc.writer.WritingMode;
import org.gradle.api.Project;
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
    public void configureRuns(Map<RunModel, IdeRunConfiguration> ideRunConfigurations,
            Iterable<RunModel> runs) {
        // Set up runs if running under buildship and in VS Code
        project.afterEvaluate(ignored -> {
            var launchWriter = new BatchedLaunchWriter(WritingMode.MODIFY_CURRENT);

            for (var run : runs) {
                var ideRunConfiguration = ideRunConfigurations.get(run);
                var prepareTask = ideRunConfiguration.prepareRunTask().get();
                addVscodeLaunchConfiguration(project, run, prepareTask, ideRunConfiguration, launchWriter);
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
            IdeRunConfiguration ideRunConfiguration,
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
        var modFoldersProvider = getModFoldersProvider(project, run.getLoadedMods(), null);
        var usesDedicatedVanillaRuntime = ideRunConfiguration.usesDedicatedVanillaRuntime().get();
        var additionalJvmArgs = new ArrayList<String>();
        if (usesDedicatedVanillaRuntime) {
            ideSyncTask.configure(task -> task.dependsOn(ideRunConfiguration.launchScriptTask()));
            additionalJvmArgs.add(RunUtils.getArgFileParameter(ideRunConfiguration.launchScriptTask().get().getClasspathArgsFile().get()));
        }
        additionalJvmArgs.add(RunUtils.getArgFileParameter(prepareTask.getVmArgsFile().get()));
        additionalJvmArgs.add(modFoldersProvider.getArgument());

        // If the user wants to run tasks before the actual execution, we attach them to autoBuildTasks
        // Missing proper support - https://github.com/microsoft/vscode-java-debug/issues/1106
        if (!run.getTasksBefore().isEmpty()) {
            eclipseModel.autoBuildTasks(run.getTasksBefore().toArray());
        }

        var launchConfiguration = launchWriter.createGroup("Mod Development - " + project.getName(), WritingMode.REMOVE_EXISTING)
                .createLaunchConfiguration()
                .withName(runIdeName)
                .withProjectName(eclipseProjectName)
                .withArguments(List.of(RunUtils.getArgFileParameter(prepareTask.getProgramArgsFile().get())))
                .withAdditionalJvmArgs(additionalJvmArgs)
                .withEnvironmentVariables(RunUtils.replaceModClassesEnv(run, modFoldersProvider))
                .withMainClass(RunUtils.DEV_LAUNCH_MAIN_CLASS)
                .withShortenCommandLine(ShortCmdBehaviour.NONE)
                .withConsoleType(ConsoleType.INTERNAL_CONSOLE)
                .withCurrentWorkingDirectory(PathLike.ofNio(run.getGameDirectory().get().getAsFile().toPath()));
        if (usesDedicatedVanillaRuntime) {
            launchConfiguration.withClassPathsOverride(List.<LocatorPathLike>of());
        }
    }
}
