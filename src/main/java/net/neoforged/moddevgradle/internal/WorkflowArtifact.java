package net.neoforged.moddevgradle.internal;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public enum WorkflowArtifact {
    COMPILED(""),
    COMPILED_WITH_SOURCES("-merged"),
    COMMON("-common"),
    COMMON_WITH_SOURCES("-common-merged"),
    COMMON_SOURCES("-common-sources"),
    CLIENT("-client"),
    CLIENT_WITH_SOURCES("-client-merged"),
    CLIENT_SOURCES("-client-sources"),
    SOURCES("-sources"),
    CLIENT_RESOURCES("-client-extra-aka-minecraft-resources");

    public final String defaultSuffix;

    WorkflowArtifact(String defaultSuffix) {
        this.defaultSuffix = defaultSuffix;
    }
}
