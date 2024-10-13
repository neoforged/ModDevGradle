package net.neoforged.moddevgradle.boot;

import org.gradle.api.Project;

public class LegacyModDevPlugin extends TrampolinePlugin<Project> {
    public LegacyModDevPlugin() {
        super("net.neoforged.moddevgradle.legacy.LegacyModDevPlugin");
    }
}
