package net.neoforged.moddevgradle.internal.utils;

import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.capabilities.Capability;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

@ApiStatus.Internal
public final class DependencyUtils {
    private DependencyUtils() {
    }

    /**
     * Given a resolved artifact, try to guess which Maven GAV it was resolved from.
     */
    public static String guessMavenGav(final ResolvedArtifactResult result) {
        final String artifactId;
        String ext = "";
        String classifier = null;

        String filename = result.getFile().getName();
        final int startOfExt = filename.lastIndexOf('.');
        if (startOfExt != -1) {
            ext = filename.substring(startOfExt + 1);
            filename = filename.substring(0, startOfExt);
        }

        if (result.getId() instanceof final ModuleComponentArtifactIdentifier moduleId) {
            final String artifact = moduleId.getComponentIdentifier().getModule();
            final String version = moduleId.getComponentIdentifier().getVersion();
            final String expectedBasename = artifact + "-" + version;

            if (filename.startsWith(expectedBasename + "-")) {
                classifier = filename.substring((expectedBasename + "-").length());
            }
            artifactId = moduleId.getComponentIdentifier().getGroup() + ":" + artifact + ":" + version;
        } else {
            // When we encounter a project reference, the component identifier does not expose the group or module name.
            // But we can access the list of capabilities associated with the published variant the artifact originates from.
            // If the capability was not overridden, this will be the project GAV. If it is *not* the project GAV,
            // it will be at least in valid GAV format, not crashing NFRT when it parses the manifest. It will just be ignored.
            final List<Capability> capabilities = result.getVariant().getCapabilities();
            if (capabilities.size() == 1) {
                final Capability capability = capabilities.get(0);
                artifactId = capability.getGroup() + ":" + capability.getName() + ":" + capability.getVersion();
            } else {
                artifactId = result.getId().getComponentIdentifier().toString();
            }
        }
        String gav = artifactId;
        if (classifier != null) {
            gav += ":" + classifier;
        }
        if (!"jar".equals(ext)) {
            gav += "@" + ext;
        }
        return gav;
    }
}
