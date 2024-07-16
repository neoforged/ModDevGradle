package net.neoforged.moddevgradle.dsl;

import net.neoforged.moddevgradle.internal.utils.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

import javax.inject.Inject;
import java.util.List;

/**
 * Model of a mod. This tells the moddev plugin which classes and resources need to be combined to produce a valid mod.
 */
public abstract class ModModel implements Named {
    /**
     * Created on-demand if the user wants to add content to this mod using cross-project references
     * or just standard dependency notation.
     */
    private Configuration configuration;

    @Inject
    public ModModel() {
        // TODO: We could potentially do a bit of name validation
        getModSourceSets().convention(List.of());
        getModSourceSets().finalizeValueOnRead();
    }

    @Inject
    protected abstract Project getProject();

    @Override
    public abstract String getName();

    final Configuration getConfiguration() {
        if (configuration == null) {
            configuration = getProject().getConfigurations().create("neoForgeModContent" + StringUtils.capitalize(getName()), new Action<Configuration>() {
                @Override
                public void execute(Configuration configuration) {
                    configuration.setCanBeConsumed(false);
                    configuration.setCanBeResolved(true);
                }
            });
        }
        return configuration;
    }

    // Do not name getSourceSets or it will conflict with project.sourceSets in scripts.
    public abstract ListProperty<SourceSet> getModSourceSets();
    public void dependency(final CharSequence dependencyNotation) {
        getConfiguration().getDependencies().add(getProject().getDependencyFactory().create(dependencyNotation));
    }

    public void dependency(final Project projectRef) {
        getConfiguration().getDependencies().add(getProject().getDependencyFactory().create(projectRef));
    }

    public void extendsFrom(final Configuration configuration) {
        getConfiguration().extendsFrom(configuration);
    }

    public void sourceSet(final SourceSet sourceSet) {
        getModSourceSets().add(sourceSet);
    }
}
