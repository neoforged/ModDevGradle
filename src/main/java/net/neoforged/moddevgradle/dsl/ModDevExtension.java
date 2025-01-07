package net.neoforged.moddevgradle.dsl;

import java.io.File;
import javax.inject.Inject;
import net.neoforged.moddevgradle.internal.Branding;
import net.neoforged.moddevgradle.internal.IdeIntegration;
import net.neoforged.moddevgradle.internal.ModDevArtifactsWorkflow;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

public abstract class ModDevExtension {
    private final NamedDomainObjectContainer<ModModel> mods;
    private final NamedDomainObjectContainer<RunModel> runs;
    private final Parchment parchment;

    private final Project project;
    private final DataFileCollection accessTransformers;
    private final DataFileCollection interfaceInjectionData;

    @Inject
    public ModDevExtension(Project project,
            DataFileCollection accessTransformers,
            DataFileCollection interfaceInjectionData) {
        mods = project.container(ModModel.class);
        runs = project.container(RunModel.class, name -> project.getObjects().newInstance(RunModel.class, name, project, mods));
        parchment = project.getObjects().newInstance(Parchment.class);
        this.project = project;
        this.accessTransformers = accessTransformers;
        this.interfaceInjectionData = interfaceInjectionData;
        getValidateAccessTransformers().convention(false);

        // Make sync tasks run
        var ideIntegration = IdeIntegration.of(project, Branding.MDG);
        ideIntegration.runTaskOnProjectSync(getIdeSyncTasks());
    }

    /**
     * The list of additional access transformers that should be applied to the Minecraft source code.
     * <p>
     * If you do not set this property, the plugin will look for an access transformer file at
     * {@code META-INF/accesstransformer.cfg} relative to your main source sets resource directories.
     *
     * @see <a href="https://projects.neoforged.net/neoforged/accesstransformers">Access Transformer File Format</a>
     */
    public void accessTransformers(Action<DataFileCollection> action) {
        action.execute(accessTransformers);
    }

    public DataFileCollection getAccessTransformers() {
        return accessTransformers;
    }

    /**
     * Replaces current access transformers.
     */
    public void setAccessTransformers(Object... paths) {
        getAccessTransformers().getFiles().setFrom(paths);
    }

    /**
     * The data-files describing additional interface implementation declarations to be added to
     * Minecraft classes.
     * <p>
     * <strong>This is an advanced property: Injecting interfaces in your development environment using this property will not implement
     * the interfaces in your published mod. You have to use Mixin or ASM to do that.</strong>
     *
     * @see <a href="https://github.com/neoforged/JavaSourceTransformer?tab=readme-ov-file#interface-injection">Interface Injection Data Format</a>
     */
    public void interfaceInjectionData(Action<DataFileCollection> action) {
        action.execute(interfaceInjectionData);
    }

    public DataFileCollection getInterfaceInjectionData() {
        return interfaceInjectionData;
    }

    /**
     * Replaces current interface injection data files.
     */
    public void setInterfaceInjectionData(Object... paths) {
        getInterfaceInjectionData().getFiles().setFrom(paths);
    }

    /**
     * Enable access transformer validation, raising fatal errors if an AT targets a member that doesn't exist.
     * <p>
     * <b>Default</b> {@code false}<br>
     */
    public abstract Property<Boolean> getValidateAccessTransformers();

    public NamedDomainObjectContainer<ModModel> getMods() {
        return mods;
    }

    public void mods(Action<NamedDomainObjectContainer<ModModel>> action) {
        action.execute(mods);
    }

    public NamedDomainObjectContainer<RunModel> getRuns() {
        return runs;
    }

    public void runs(Action<NamedDomainObjectContainer<RunModel>> action) {
        action.execute(runs);
    }

    public Parchment getParchment() {
        return parchment;
    }

    public void parchment(Action<Parchment> action) {
        action.execute(parchment);
    }

    /**
     * The tasks to be run when the IDE reloads the Gradle project.
     */
    public abstract ListProperty<TaskProvider<?>> getIdeSyncTasks();

    /**
     * Configures the given task to be run when the IDE reloads the Gradle project.
     */
    public void ideSyncTask(TaskProvider<?> task) {
        this.getIdeSyncTasks().add(task);
    }

    /**
     * Configures the given task to be run when the IDE reloads the Gradle project.
     */
    public void ideSyncTask(Task task) {
        this.getIdeSyncTasks().add(task.getProject().getTasks().named(task.getName()));
    }

    /**
     * Used to request additional Minecraft artifacts from NFRT for advanced usage scenarios.
     * <p>
     * Maps a result name to the file it should be written to.
     * The result names are specific to the NeoForm process that is being used in the background and may change between
     * NeoForge versions.
     */
    public abstract MapProperty<String, File> getAdditionalMinecraftArtifacts();

    /**
     * Adds the necessary dependencies to develop a Minecraft mod to additional source sets.
     * If you do not specify a source set when you enable modding, the dependencies are automatically added
     * to the main source set.
     */
    public void addModdingDependenciesTo(SourceSet sourceSet) {
        ModDevArtifactsWorkflow.get(project).addToSourceSet(sourceSet);
    }

    /**
     * After enabling modding, you can retrieve the version of the modding platform you picked using this getter.
     * I.e. the NeoForge or Forge version. If you chose to enable vanilla-only mode, this getter will throw.
     */
    public String getVersion() {
        var dependencies = ModDevArtifactsWorkflow.get(project).dependencies();
        if (dependencies.neoForgeDependency() == null) {
            throw new InvalidUserCodeException("You cannot retrieve the enabled version if you are in vanilla-only mode.");
        }
        return dependencies.neoForgeDependency().getVersion();
    }

    /**
     * After enabling modding, you can retrieve the effective Minecraft version using this getter.
     */
    public String getMinecraftVersion() {
        return ModDevArtifactsWorkflow.get(project).versionCapabilities().minecraftVersion();
    }

    /**
     * After enabling modding, you can retrieve the capabilities of the version you picked using this getter.
     */
    public VersionCapabilities getVersionCapabilities() {
        return ModDevArtifactsWorkflow.get(project).versionCapabilities();
    }
}
