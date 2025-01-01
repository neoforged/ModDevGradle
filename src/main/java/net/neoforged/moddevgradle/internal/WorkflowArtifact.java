package net.neoforged.moddevgradle.internal;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public enum WorkflowArtifact {
    COMPILED(""),
    COMPILED_WITH_SOURCES("-merged"),
    SOURCES("-sources"),
    CLIENT_RESOURCES("-client-extra-aka-minecraft-resources");

    public final String defaultSuffix;

    WorkflowArtifact(String defaultSuffix) {
        this.defaultSuffix = defaultSuffix;
    }
}
