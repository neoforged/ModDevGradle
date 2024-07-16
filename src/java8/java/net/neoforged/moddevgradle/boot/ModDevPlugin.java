package net.neoforged.moddevgradle.boot;

import org.gradle.api.Project;

public final class ModDevPlugin extends TrampolinePlugin<Project> {
    public ModDevPlugin() {
        super("net.neoforged.moddevgradle.internal.ModDevPlugin");
    }
}
