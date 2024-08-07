package net.neoforged.moddevgradle.dsl;

import net.neoforged.moddevgradle.internal.ModDevPlugin;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;

/**
 * This is the top-level {@code neoForge} extension, used to configure the moddev plugin.
 */
public abstract class NeoForgeExtension {
    public static final String NAME = "neoForge";

    private final Project project;
    private final NamedDomainObjectContainer<ModModel> mods;
    private final NamedDomainObjectContainer<RunModel> runs;
    private final Parchment parchment;
    private final NeoFormRuntime neoFormRuntime;
    private final UnitTest unitTest;

    private final DataFileCollection accessTransformers;
    private final DataFileCollection interfaceInjectionData;

    @Inject
    public NeoForgeExtension(Project project, DataFileCollection accessTransformers, DataFileCollection interfaceInjectionData) {
        this.project = project;
        mods = project.container(ModModel.class);
        runs = project.container(RunModel.class);
        parchment = project.getObjects().newInstance(Parchment.class);
        neoFormRuntime = project.getObjects().newInstance(NeoFormRuntime.class);
        unitTest = project.getObjects().newInstance(UnitTest.class);
        this.accessTransformers = accessTransformers;
        this.interfaceInjectionData = interfaceInjectionData;
        getValidateAccessTransformers().convention(false);
    }

    /**
     * Adds the necessary dependencies to develop a Minecraft mod to the given source set.
     * The plugin automatically adds these dependencies to the main source set.
     */
    public void addModdingDependenciesTo(SourceSet sourceSet) {
        var configurations = project.getConfigurations();
        var sourceSets = ExtensionUtils.getSourceSets(project);
        if (!sourceSets.contains(sourceSet)) {
            throw new GradleException("Cannot add to the source set in another project.");
        }

        configurations.getByName(sourceSet.getRuntimeClasspathConfigurationName())
                .extendsFrom(configurations.getByName(ModDevPlugin.CONFIGURATION_RUNTIME_DEPENDENCIES));
        configurations.getByName(sourceSet.getCompileClasspathConfigurationName())
                .extendsFrom(configurations.getByName(ModDevPlugin.CONFIGURATION_COMPILE_DEPENDENCIES));
    }

    /**
     * NeoForge version number. You have to set either this or {@link #getNeoFormVersion()}.
     */
    public abstract Property<String> getVersion();

    /**
     * You can set this property to a version of <a href="https://projects.neoforged.net/neoforged/neoform">NeoForm</a>
     * to either override the version used in the version of NeoForge you set, or to compile against
     * Vanilla artifacts that have no NeoForge code added.
     */
    public abstract Property<String> getNeoFormVersion();

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

    public NeoFormRuntime getNeoFormRuntime() {
        return neoFormRuntime;
    }

    public void neoFormRuntime(Action<NeoFormRuntime> action) {
        action.execute(neoFormRuntime);
    }

    public UnitTest getUnitTest() {
        return unitTest;
    }

    public void unitTest(Action<UnitTest> action) {
        action.execute(unitTest);
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
}
