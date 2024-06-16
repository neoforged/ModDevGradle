package net.neoforged.moddevgradle.internal.jarjar;

import net.neoforged.jarjar.metadata.ContainedJarIdentifier;
import net.neoforged.jarjar.metadata.ContainedJarMetadata;
import net.neoforged.jarjar.metadata.ContainedVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class ResolvedJarJarArtifact {

    private final File file;
    private final String embeddedFilename;
    private final String version;
    private final String versionRange;
    private final String group;
    private final String artifact;

    public ResolvedJarJarArtifact(File file, String embeddedFilename, String version, String versionRange, String group, String artifact) {
        this.file = file;
        this.embeddedFilename = embeddedFilename;
        this.version = version;
        this.versionRange = versionRange;
        this.group = group;
        this.artifact = artifact;
    }

    public ContainedJarIdentifier createContainedJarIdentifier() {
        return new ContainedJarIdentifier(group, artifact);
    }

    public ContainedVersion createContainedVersion() {
        try {
            return new ContainedVersion(
                    VersionRange.createFromVersionSpec(versionRange),
                    new DefaultArtifactVersion(version)
            );
        } catch (InvalidVersionSpecificationException e) {
            throw new RuntimeException(e);
        }
    }

    public ContainedJarMetadata createContainerMetadata() {
        return new ContainedJarMetadata(createContainedJarIdentifier(), createContainedVersion(), "META-INF/jarjar/"+embeddedFilename, isObfuscated(file));
    }

    @InputFile
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public File getFile() {
        return file;
    }

    @Input
    public String getEmbeddedFilename() {
        return embeddedFilename;
    }

    @Input
    public String getVersion() {
        return version;
    }

    @Input
    public String getVersionRange() {
        return versionRange;
    }

    @Input
    public String getGroup() {
        return group;
    }

    @Input
    public String getArtifact() {
        return artifact;
    }

    private static boolean isObfuscated(final File dependency) {
        try(final JarFile jarFile = new JarFile(dependency)) {
            final Manifest manifest = jarFile.getManifest();
            return manifest.getMainAttributes().containsKey("Obfuscated-By");
        } catch (IOException e) {
            throw new RuntimeException("Could not read jar file for dependency", e);
        }
    }
}
