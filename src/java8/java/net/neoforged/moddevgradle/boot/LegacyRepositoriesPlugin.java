package net.neoforged.moddevgradle.boot;

import org.gradle.api.plugins.PluginAware;

public class LegacyRepositoriesPlugin extends TrampolinePlugin<PluginAware> {
    public LegacyRepositoriesPlugin() {
        super("net.neoforged.moddevgradle.legacyforge.internal.LegacyRepositoriesPlugin");
    }
}
