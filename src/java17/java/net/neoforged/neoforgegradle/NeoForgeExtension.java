package net.neoforged.neoforgegradle;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class NeoForgeExtension {
    private final NamedDomainObjectContainer<Run> runs;

    @Inject
    public NeoForgeExtension(Project project) {
        runs = project.container(Run.class);
    }

    abstract Property<String> getVersion();

    abstract Property<String> getNeoFormVersion();

    public NamedDomainObjectSet<Run> getRuns() {
        return runs;
    }
}
