package net.neoforged.moddevgradle.boot;

import org.gradle.api.Project;

public class ModDevPlugin extends TrampolinePlugin<Project> {
    public ModDevPlugin() {
        super("net.neoforged.moddevgradle.internal.ModDevPlugin");
    }
}
