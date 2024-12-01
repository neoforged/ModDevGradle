package net.neoforged.moddevgradle.boot;

import org.gradle.api.Project;

public class LegacyForgeModDevPlugin extends TrampolinePlugin<Project> {
    public LegacyForgeModDevPlugin() {
        super("net.neoforged.moddevgradle.legacyforge.internal.LegacyForgeModDevPlugin");
    }
}
