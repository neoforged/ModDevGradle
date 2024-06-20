package net.neoforged.moddevgradle.internal;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;

import java.io.File;
import java.io.Serializable;
import java.util.Objects;

/**
 * Represents an entry in the Artifact manifest given to NFRT to prevent re-downloading of
 * dependencies that Gradle has already downloaded to its cache.
 * It uses the same format used in NeoForm/NeoForge configuration data to identify an artifact (Maven format).
 */
final class ArtifactManifestEntry implements Serializable {
    @Input
    private final String artifactId;
    @InputFile
    private final File file;

    ArtifactManifestEntry(String artifactId, File file) {
        this.artifactId = artifactId;
        this.file = file;
    }

    @Input
    public String artifactId() {
        return artifactId;
    }

    @InputFile
    public File file() {
        return file;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ArtifactManifestEntry) obj;
        return Objects.equals(this.artifactId, that.artifactId) &&
               Objects.equals(this.file, that.file);
    }

    @Override
    public int hashCode() {
        return Objects.hash(artifactId, file);
    }

    @Override
    public String toString() {
        return "ArtifactManifestEntry[" +
               "artifactId=" + artifactId + ", " +
               "file=" + file + ']';
    }
}
