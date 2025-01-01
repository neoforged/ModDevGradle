package net.neoforged.moddevgradle.internal;

import java.io.File;
import java.util.Map;
import java.util.Set;
import net.neoforged.moddevgradle.dsl.ModModel;
import net.neoforged.moddevgradle.dsl.RunModel;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import net.neoforged.moddevgradle.internal.utils.IdeDetection;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementing classes are responsible for registering {@code ideSyncTask} with their IDE.
 */
public sealed abstract class IdeIntegration permits IntelliJIntegration, EclipseIntegration, NoIdeIntegration {
    private static final Logger LOG = LoggerFactory.getLogger(IdeIntegration.class);

    /**
     * A task we attach other tasks to that should run when the IDE reloads the projects.
     */
    protected final TaskProvider<Task> ideSyncTask;

    protected final Project project;

    public IdeIntegration(Project project, Branding branding) {
        this.project = project;
        this.ideSyncTask = project.getTasks().register("neoForgeIdeSync", task -> {
            task.setGroup(branding.internalTaskGroup());
            task.setDescription("A utility task that is used to create necessary files when the Gradle project is synchronized with the IDE project.");
        });
    }

    public static IdeIntegration of(Project project, Branding branding) {
        var ideIntegration = ExtensionUtils.findExtension(project, "mdgInternalIdeIntegration", IdeIntegration.class);
        if (ideIntegration == null) {
            ideIntegration = createForProject(project, branding);
            project.getExtensions().add(IdeIntegration.class, "mdgInternalIdeIntegration", ideIntegration);
        }
        return ideIntegration;
    }

    private static IdeIntegration createForProject(Project project, Branding branding) {
        if (IdeDetection.isVsCode()) {
            // VSCode internally uses Eclipse and as such, we need to prioritize it over the pure Eclipse integration
            LOG.debug("Activating VSCode integration for project {}.", project.getPath());
            return new VsCodeIntegration(project, branding);
        } else if (IdeDetection.isEclipse()) {
            LOG.debug("Activating Eclipse integration for project {}.", project.getPath());
            return new EclipseIntegration(project, branding);
        } else if (IdeDetection.isIntelliJSync()) {
            LOG.debug("Activating IntelliJ integration for project {}.", project.getPath());
            return new IntelliJIntegration(project, branding);
        } else {
            return new NoIdeIntegration(project, branding);
        }
    }

    /**
     * Attach a source artifact to a binary artifact if the IDE supports it.
     *
     * @param jarToSourceJarMapping Maps a classpath location containing classes to their source location.
     *                              Locations are usually JAR files but may be folders.
     * @see #shouldUseCombinedSourcesAndClassesArtifact() This method will not work if the IDE doesn't support attaching sources.
     */
    void attachSources(Map<Provider<RegularFile>, Provider<RegularFile>> jarToSourceJarMapping) {}

    /**
     * Only IntelliJ needs the combined artifact.
     * We also use this model when Gradle tasks are being run from IntelliJ, not only if IntelliJ is reloading
     * the project.
     * This prevents the classpath during debugging from the classpath detected by IntelliJ itself.
     * For Eclipse, we can attach the sources via the Eclipse project model.
     */
    boolean shouldUseCombinedSourcesAndClassesArtifact() {
        return false;
    }

    /**
     * Registers a task to be run when the IDE reloads the Gradle project.
     */
    public final void runTaskOnProjectSync(Object task) {
        ideSyncTask.configure(ideSyncTask -> ideSyncTask.dependsOn(task));
    }

    void configureRuns(Map<RunModel, TaskProvider<PrepareRun>> prepareRunTasks, Iterable<RunModel> runs) {}

    void configureTesting(Provider<Set<ModModel>> loadedMods,
            Provider<ModModel> testedMod,
            Provider<Directory> runArgsDir,
            File gameDirectory,
            Provider<RegularFile> programArgsFile,
            Provider<RegularFile> vmArgsFile) {}

    protected boolean shouldGenerateConfigFor(RunModel run) {
        var ideName = run.getIdeName().get();
        return !ideName.isBlank();
    }
}
