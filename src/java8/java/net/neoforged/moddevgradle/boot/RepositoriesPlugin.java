package net.neoforged.moddevgradle.boot;

import org.gradle.api.plugins.PluginAware;

public class RepositoriesPlugin extends TrampolinePlugin<PluginAware> {
    public RepositoriesPlugin() {
        super("net.neoforged.moddevgradle.internal.RepositoriesPlugin");
    }
}
