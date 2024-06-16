package net.neoforged.moddevgradle.dsl;

import net.neoforged.moddevgradle.internal.ModDevPlugin;
import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * This is the top-level {@code neoForge} extension, used to configure the moddev plugin.
 */
public abstract class NeoForgeExtension {
    public static final String NAME = "neoForge";

    private final NamedDomainObjectContainer<ModModel> mods;
    private final NamedDomainObjectContainer<RunModel> runs;
    private final Parchment parchment;
    private final NeoFormRuntime neoFormRuntime;
    private final UnitTest unitTest;
    private final SourceSet sourceSet;
    private final ModDevPlugin plugin;

    @Inject
    protected abstract Project getProject();

    @Inject
    public NeoForgeExtension(SourceSet sourceSet, ModDevPlugin plugin) {
        this.sourceSet = sourceSet;
        this.plugin = plugin;
        mods = getProject().container(ModModel.class);
        runs = getProject().container(RunModel.class);
        parchment = getProject().getObjects().newInstance(Parchment.class);
        neoFormRuntime = getProject().getObjects().newInstance(NeoFormRuntime.class);
        unitTest = getProject().getObjects().newInstance(UnitTest.class, sourceSet);

        getAccessTransformers().convention(getProject().provider(() -> {
            // TODO Can we scan the source sets for the main source sets resource dir?
            // Only return this when it actually exists
            var defaultPath = "src/main/resources/META-INF/accesstransformer.cfg";
            List<File> out = new ArrayList<>();
            for (var dir : sourceSet.getResources().getSrcDirs()) {
                var target = dir.toPath().resolve("META-INF/accesstransformer.cfg");
                if (target.toFile().exists()) {
                    out.add(getProject().getLayout().getProjectDirectory().getAsFile().toPath().relativize(target).toFile());
                }
            }
            return out;
        }));

        plugin.forSourceSet(getProject(), sourceSet, this);
    }

    /**
     * Adds the necessary dependencies to develop a Minecraft mod to the given source set.
     * The plugin automatically adds these dependencies to the main source set.
     */
    public void addModdingDependenciesTo(SourceSet sourceSet) {
        var configurations = getProject().getConfigurations();
        var sourceSets = ExtensionUtils.getSourceSets(getProject());
        if (!sourceSets.contains(sourceSet)) {
            throw new GradleException("Cannot add to the source set in another project.");
        }

        configurations.getByName(sourceSet.getRuntimeClasspathConfigurationName())
                .extendsFrom(configurations.getByName(ModDevPlugin.CONFIGURATION_RUNTIME_DEPENDENCIES));
        configurations.getByName(sourceSet.getCompileClasspathConfigurationName())
                .extendsFrom(configurations.getByName(ModDevPlugin.CONFIGURATION_COMPILE_DEPENDENCIES));
    }

    public NeoForgeExtension forSourceSet(SourceSet target) {
        var extension = getProject().getObjects().newInstance(NeoForgeExtension.class, target, plugin);
        target.getExtensions().add(NeoForgeExtension.class, NeoForgeExtension.NAME, extension);
        return extension;
    }

    public NeoForgeExtension forSourceSet(SourceSet target, Action<NeoForgeExtension> action) {
        var extension = forSourceSet(target);
        action.execute(extension);
        return extension;
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

    public abstract ConfigurableFileCollection getAccessTransformers();

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
