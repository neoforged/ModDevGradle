package net.neoforged.neoforgegradle.dsl;

import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

// TODO: the concept is copied over from NG since it is the most reliable way of doing this.
// Unfortunately it is also an annoying duplication of information.
// Maybe we should attempt to parse the IDEA xmls like Loom does.
// TODO: At least, we might want to clear the gradle outputs if we build with intellij and the intellij outputs
// if we build with Gradle to avoid loading the wrong version of classes and resources.
public abstract class ExtraIdeaModel {
    @Inject
    public ExtraIdeaModel(Project project) {
        getRunWithIdea().convention(true);
        getOutDirectory().convention(project.getLayout().getProjectDirectory().dir("out"));
    }

    public abstract Property<Boolean> getRunWithIdea();

    public abstract DirectoryProperty getOutDirectory();
}
