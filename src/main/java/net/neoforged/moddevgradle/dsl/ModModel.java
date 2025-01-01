package net.neoforged.moddevgradle.dsl;

import java.util.List;
import javax.inject.Inject;
import org.gradle.api.Named;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.SourceSet;

/**
 * Model of a mod. This tells the moddev plugin which classes and resources need to be combined to produce a valid mod.
 */
public abstract class ModModel implements Named {
    @Inject
    public ModModel() {
        // TODO: We could potentially do a bit of name validation
        getModSourceSets().convention(List.of());
        getModSourceSets().finalizeValueOnRead();
    }

    @Override
    public abstract String getName();

    // Do not name getSourceSets or it will conflict with project.sourceSets in scripts.
    public abstract ListProperty<SourceSet> getModSourceSets();

    public void sourceSet(SourceSet sourceSet) {
        getModSourceSets().add(sourceSet);
    }
}
