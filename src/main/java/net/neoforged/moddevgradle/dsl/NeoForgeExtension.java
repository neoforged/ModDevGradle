package net.neoforged.moddevgradle.dsl;

import net.neoforged.moddevgradle.internal.ModDevPlugin;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

import javax.inject.Inject;
import java.util.List;

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

    @Inject
    public NeoForgeExtension(Project project) {
        this.project = project;
        mods = project.container(ModModel.class);
        runs = project.container(RunModel.class);
        parchment = project.getObjects().newInstance(Parchment.class);
        neoFormRuntime = project.getObjects().newInstance(NeoFormRuntime.class);
        unitTest = project.getObjects().newInstance(UnitTest.class);

        getAccessTransformers().convention(project.provider(() -> {
            // TODO Can we scan the source sets for the main source sets resource dir?
            // Only return this when it actually exists
            var defaultPath = "src/main/resources/META-INF/accesstransformer.cfg";
            if (!project.file(defaultPath).exists()) {
                return List.of();
            }
            return List.of(defaultPath);
        }));
    }

    /**
     * Adds the necessary dependencies to develop a Minecraft mod to the given source set.
     * The plugin automatically adds these dependencies to the main source set.
     */
    public void addModdingDependenciesTo(SourceSet sourceSet) {
        var configurations = project.getConfigurations();
        var sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
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

    public abstract ListProperty<String> getAccessTransformers();

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
}
