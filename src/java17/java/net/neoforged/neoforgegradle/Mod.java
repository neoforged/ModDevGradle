package net.neoforged.neoforgegradle;

import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.language.jvm.tasks.ProcessResources;

import javax.inject.Inject;

public abstract class Mod implements Named {
    @Inject
    public Mod() {
        // TODO: We could potentially do a bit of name validation
    }

    @Inject
    protected abstract Project getProject();

    @Override
    @Input
    public abstract String getName();

    @Classpath
    public abstract ConfigurableFileCollection getModFiles();

    public void sourceSet(SourceSet sourceSet) {
        sourceSet(sourceSet, getProject());
    }

    public void sourceSet(SourceSet sourceSet, Project project) {
        if (!project.getExtensions().getByType(SourceSetContainer.class).contains(sourceSet)) {
            throw new IllegalArgumentException("Source set " + sourceSet + " does not belong to project " + project);
        }

        // TODO: Is this correct for resources? We want to capture the task dependency.
        // TODO: Probably not correct; we don't want to capture the dependency for the writeArgs task, only for the run task?
        getModFiles().from(project.getTasks()
                .named(sourceSet.getProcessResourcesTaskName(), ProcessResources.class)
                .map(ProcessResources::getDestinationDir));
        // TODO: For classes I don't know how to capture the dependency on the compile task(s)...
        getModFiles().from(sourceSet.getOutput().getClassesDirs());
    }
}
