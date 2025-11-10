package net.neoforged.moddevgradle.internal;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public enum WorkflowArtifact {
    COMPILED("", "compiled"),
    COMPILED_COMMON("-common", "commonCompiled"),
    COMPILED_CLIENT("-clientOnly", "clientOnlyCompiled"),
    COMPILED_WITH_SOURCES("-merged", "compiledWithSources"),
    COMPILED_WITH_SOURCES_COMMON("-merged-common", "commonCompiledWithSources"),
    COMPILED_WITH_SOURCES_CLIENT("-merged-clientOnly", "clientOnlyCompiledWithSources"),
    SOURCES("-sources", "sources"),
    SOURCES_COMMON("-sources-common", "commonSources"),
    SOURCES_CLIENT("-sources-clientOnly", "clientOnlySources"),
    CLIENT_RESOURCES("-client-extra-aka-minecraft-resources", "clientResources");

    public final String defaultSuffix;
    public final String nfrtOutput;

    WorkflowArtifact(String defaultSuffix, String nfrtOutput) {
        this.defaultSuffix = defaultSuffix;
        this.nfrtOutput = nfrtOutput;
    }
}
