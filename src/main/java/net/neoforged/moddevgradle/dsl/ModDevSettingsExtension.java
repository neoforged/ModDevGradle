package net.neoforged.moddevgradle.dsl;

import javax.inject.Inject;
import org.gradle.api.initialization.Settings;
import org.gradle.api.model.ObjectFactory;

public abstract class ModDevSettingsExtension {
    public static final String NAME = "modDev";

    private final DependencyTools dependencyTools;

    @Inject
    public ModDevSettingsExtension(Settings settings) {
        this.dependencyTools = getObjects().newInstance(DependencyTools.class, settings.getDependencyResolutionManagement().getComponents());
    }

    public DependencyTools getDependencyTools() {
        return this.dependencyTools;
    }

    @Inject
    protected abstract ObjectFactory getObjects();
}
