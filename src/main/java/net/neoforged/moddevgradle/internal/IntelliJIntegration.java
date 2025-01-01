package net.neoforged.moddevgradle.internal;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import net.neoforged.moddevgradle.dsl.ModModel;
import net.neoforged.moddevgradle.dsl.RunModel;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import net.neoforged.moddevgradle.internal.utils.FileUtils;
import net.neoforged.moddevgradle.internal.utils.IdeDetection;
import net.neoforged.moddevgradle.internal.utils.StringUtils;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.DefaultTaskExecutionRequest;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.gradle.ext.Application;
import org.jetbrains.gradle.ext.BeforeRunTask;
import org.jetbrains.gradle.ext.IdeaExtPlugin;
import org.jetbrains.gradle.ext.JUnit;
import org.jetbrains.gradle.ext.ModuleRef;
import org.jetbrains.gradle.ext.ProjectSettings;
import org.jetbrains.gradle.ext.RunConfigurationContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class IntelliJIntegration extends IdeIntegration {
    private static final Logger LOG = LoggerFactory.getLogger(IntelliJIntegration.class);

    private final IdeaModel rootIdeaModel;

    IntelliJIntegration(Project project, Branding branding) {
        super(project, branding);

        // While the IDEA model on the root project is the only sensible place to adjust IntelliJ project-wide settings
        // such as run configurations.
        var rootProject = project.getRootProject();

        // Force apply during IJ sync to be able to set IntelliJ settings
        rootProject.getPlugins().apply(IdeaPlugin.class);

        this.rootIdeaModel = ExtensionUtils.getExtension(rootProject, "idea", IdeaModel.class);

        // idea-ext doesn't seem to do anything if no idea model is present anyway
        if (!rootProject.getPlugins().hasPlugin(IdeaExtPlugin.class)) {
            rootProject.getPlugins().apply(IdeaExtPlugin.class);
        }

        // Since this does not just configure a data model but actually runs an additional task, we only do this
        // when IntelliJ is actually reloading the Gradle project right now.
        if (IdeDetection.isIntelliJSync()) {
            project.afterEvaluate(ignored -> {
                // Also run the sync task directly as part of the sync. (Thanks Loom).
                var startParameter = project.getGradle().getStartParameter();
                var taskRequests = new ArrayList<>(startParameter.getTaskRequests());

                taskRequests.add(new DefaultTaskExecutionRequest(List.of(ideSyncTask.getName())));
                startParameter.setTaskRequests(taskRequests);
            });
        }
    }

    @Override
    public void attachSources(Map<Provider<RegularFile>, Provider<RegularFile>> jarToSourceJarMapping) {
        // IntelliJ does not have a mechanism for us to attach the source artifacts
    }

    @Override
    public void configureRuns(Map<RunModel, TaskProvider<PrepareRun>> prepareRunTasks, Iterable<RunModel> runs) {
        // IDEA Sync has no real notion of tasks or providers or similar
        project.afterEvaluate(ignored -> {

            var runConfigurations = getIntelliJRunConfigurations();

            if (runConfigurations == null) {
                LOG.debug("Failed to find IntelliJ run configuration container. Not adding run configurations.");
            } else {
                var outputDirectory = IntelliJOutputDirectoryValueSource.getIntellijOutputDirectory(project);

                for (var run : runs) {
                    var prepareTask = prepareRunTasks.get(run).get();
                    if (!prepareTask.getEnabled()) {
                        LOG.info("Not creating IntelliJ run {} since its prepare task {} is disabled", run, prepareTask);
                        continue;
                    }
                    if (!shouldGenerateConfigFor(run)) {
                        LOG.info("Not creating IntelliJ run {} since it's explicitly disabled", run);
                        continue;
                    }
                    addIntelliJRunConfiguration(project, runConfigurations, outputDirectory, run, prepareTask);
                }
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
        // IDEA Sync has no real notion of tasks or providers or similar
        project.afterEvaluate(ignored -> {
            // Write out a separate file that has IDE specific VM args, which include the definition of the output directories.
            // For JUnit we have to write this to a separate file due to the Run parameters being shared among all projects.
            var intellijVmArgsFile = runArgsDir.map(dir -> dir.file("intellijVmArgs.txt"));

            var outputDirectory = IntelliJOutputDirectoryValueSource.getIntellijOutputDirectory(project);
            var ideSpecificVmArgs = RunUtils.escapeJvmArg(getModFoldersProvider(project, outputDirectory, loadedMods, testedMod).getArgument());
            try {
                var vmArgsFilePath = intellijVmArgsFile.get().getAsFile().toPath();
                Files.createDirectories(vmArgsFilePath.getParent());
                // JVM args generally expect platform encoding
                FileUtils.writeStringSafe(vmArgsFilePath, ideSpecificVmArgs, StringUtils.getNativeCharset());
            } catch (IOException e) {
                throw new GradleException("Failed to write VM args file for IntelliJ unit tests", e);
            }

            // Configure IntelliJ default JUnit parameters, which are used when the user configures IJ to run tests natively
            // IMPORTANT: This affects *all projects*, not just this one. We have to use $MODULE_WORKING_DIR$ to make it work.
            var intelliJRunConfigurations = getIntelliJRunConfigurations();
            if (intelliJRunConfigurations != null) {
                intelliJRunConfigurations.defaults(JUnit.class, jUnitDefaults -> {
                    // $MODULE_WORKING_DIR$ is documented here: https://www.jetbrains.com/help/idea/absolute-path-variables.html
                    jUnitDefaults.setWorkingDirectory("$MODULE_WORKING_DIR$/" + ModDevRunWorkflow.JUNIT_GAME_DIR);
                    jUnitDefaults.setVmParameters(
                            // The FML JUnit plugin uses this system property to read a file containing the program arguments needed to launch
                            // NOTE: IntelliJ does not support $MODULE_WORKING_DIR$ in VM Arguments
                            // See https://youtrack.jetbrains.com/issue/IJPL-14230/Add-macro-support-for-VM-options-field-e.g.-expand-ModuleFileDir-properly
                            // As a workaround, we just use paths relative to the working directory.
                            RunUtils.escapeJvmArg("-Dfml.junit.argsfile=" + buildRelativePath(programArgsFile, gameDirectory))
                                    + " "
                                    + RunUtils.escapeJvmArg("@" + buildRelativePath(vmArgsFile, gameDirectory))
                                    + " "
                                    + RunUtils.escapeJvmArg("@" + buildRelativePath(intellijVmArgsFile, gameDirectory)));
                });
            }
        });
    }

    @Nullable
    private RunConfigurationContainer getIntelliJRunConfigurations() {
        // The idea and idea-ext plugins are required on the root-project level to add run configurations
        if (rootIdeaModel == null) {
            return null;
        }

        // It's unclear when idea is present but the project is null, but guard against it
        if (rootIdeaModel.getProject() == null) {
            return null;
        }

        var projectSettings = ((ExtensionAware) rootIdeaModel.getProject()).getExtensions().getByType(ProjectSettings.class);

        return ExtensionUtils.findExtension((ExtensionAware) projectSettings, "runConfigurations", RunConfigurationContainer.class);
    }

    private static void addIntelliJRunConfiguration(Project project,
            RunConfigurationContainer runConfigurations,
            @Nullable Function<Project, File> outputDirectory,
            RunModel run,
            PrepareRun prepareTask) {
        var appRun = new Application(run.getIdeName().get(), project);
        var sourceSets = ExtensionUtils.getSourceSets(project);
        var sourceSet = run.getSourceSet().get();
        // Validate that the source set is part of this project
        if (!sourceSets.contains(sourceSet)) {
            throw new GradleException("Cannot use source set from another project for run " + run.getName());
        }
        appRun.setModuleName(getIntellijModuleName(project, sourceSet));
        appRun.setWorkingDirectory(run.getGameDirectory().get().getAsFile().getAbsolutePath());
        var modFoldersProvider = getModFoldersProvider(project, outputDirectory, run.getLoadedMods(), null);
        appRun.setEnvs(RunUtils.replaceModClassesEnv(run, modFoldersProvider));
        appRun.setJvmArgs(
                RunUtils.escapeJvmArg(RunUtils.getArgFileParameter(prepareTask.getVmArgsFile().get()))
                        + " "
                        + RunUtils.escapeJvmArg(modFoldersProvider.getArgument()));
        appRun.setMainClass(RunUtils.DEV_LAUNCH_MAIN_CLASS);
        appRun.setProgramParameters(RunUtils.escapeJvmArg(RunUtils.getArgFileParameter(prepareTask.getProgramArgsFile().get())));

        if (!run.getTasksBefore().isEmpty()) {
            // This is slightly annoying.
            // idea-ext does not expose the ability to run multiple gradle tasks at once, but the IDE model is capable of it.
            class GradleTasks extends BeforeRunTask {
                @Inject
                GradleTasks(String nameParam) {
                    type = "gradleTask";
                    name = nameParam;
                }

                @SuppressWarnings("unchecked")
                @Override
                public Map<String, ?> toMap() {
                    var result = (Map<String, Object>) super.toMap();
                    result.put("projectPath", project.getProjectDir().getAbsolutePath().replaceAll("\\\\", "/"));
                    var tasks = run.getTasksBefore().stream().map(task -> task.get().getPath()).collect(Collectors.joining(" "));
                    result.put("taskName", tasks);
                    return result;
                }
            }
            appRun.getBeforeRun().add(new GradleTasks("Prepare"));
        }

        runConfigurations.add(appRun);
    }

    private static String buildRelativePath(Provider<RegularFile> file, File workingDirectory) {
        return workingDirectory.toPath().relativize(file.get().getAsFile().toPath()).toString().replace("\\", "/");
    }

    @Override
    public boolean shouldUseCombinedSourcesAndClassesArtifact() {
        return true;
    }

    private static ModFoldersProvider getModFoldersProvider(Project project,
            @Nullable Function<Project, File> outputDirectory,
            Provider<Set<ModModel>> modsProvider,
            @Nullable Provider<ModModel> testedMod) {
        Provider<Map<String, ModFolder>> folders;
        if (outputDirectory != null) {
            folders = RunUtils.buildModFolders(project, modsProvider, testedMod, (sourceSet, output) -> {
                var sourceSetDir = outputDirectory.apply(RunUtils.findSourceSetProject(project, sourceSet)).toPath().resolve(getIdeaOutName(sourceSet));
                output.from(sourceSetDir.resolve("classes"), sourceSetDir.resolve("resources"));
            });
        } else {
            folders = RunUtils.getModFoldersForGradle(project, modsProvider, testedMod);
        }

        var modFoldersProvider = project.getObjects().newInstance(ModFoldersProvider.class);
        modFoldersProvider.getModFolders().set(folders);
        return modFoldersProvider;
    }

    private static String getIdeaOutName(final SourceSet sourceSet) {
        return sourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME) ? "production" : sourceSet.getName();
    }

    /**
     * Convert a project and source set to an IntelliJ module name.
     * Do not use {@link ModuleRef} as it does not correctly handle projects with a space in their name!
     */
    private static String getIntellijModuleName(Project project, SourceSet sourceSet) {
        var moduleName = new StringBuilder();
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
