package net.neoforged.moddevgradle.boot;

import org.gradle.api.Project;

public class RepositoryPlugin extends TrampolinePlugin<Project> {
    public RepositoryPlugin() {
        super("net.neoforged.moddevgradle.internal.RepositoryPlugin");
    }
}
