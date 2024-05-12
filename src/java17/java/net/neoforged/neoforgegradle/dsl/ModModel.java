package net.neoforged.neoforgegradle.dsl;

import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

import javax.inject.Inject;
import java.util.List;

public abstract class ModModel implements Named {
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

    // Do not name getSourceSets or it will conflict with project.sourceSets in scripts.
    public abstract ListProperty<SourceSet> getModSourceSets();

    public void sourceSet(SourceSet sourceSet) {
        sourceSet(sourceSet, getProject());
    }

    public void sourceSet(SourceSet sourceSet, Project project) {
        if (!project.getExtensions().getByType(SourceSetContainer.class).contains(sourceSet)) {
            throw new IllegalArgumentException("Source set " + sourceSet + " does not belong to project " + project);
        }

        getModSourceSets().add(sourceSet);
    }
}
