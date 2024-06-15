package net.neoforged.moddevgradle.boot;

import org.gradle.api.Project;

public class RepositoriesPlugin extends TrampolinePlugin<Project> {
    public RepositoriesPlugin() {
        super("net.neoforged.moddevgradle.internal.RepositoriesPlugin");
    }
}
