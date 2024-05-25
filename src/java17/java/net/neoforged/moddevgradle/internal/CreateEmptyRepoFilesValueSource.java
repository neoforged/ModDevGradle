package net.neoforged.moddevgradle.internal;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/**
 * Gradle complains if dependencies in the local maven repository we use to store the NFRT results are not
 * present at configuration time. To work around this, we create empty files in the repo, which then
 * get replaced later when the actual task to create them is run.
 * <p>
 * Doing this in the normal plugin code will record a configuration dependency on the empty file, leading to
 * unnecessary invalidations of the configuration cache.
 * <p>
 * Gradle itself suggests using a value source in such cases, which is what we implement here. The return value
 * of the value source is always constant, as we actually don't depend on the files at config time.
 */
abstract class CreateEmptyRepoFilesValueSource
        implements ValueSource<String, CreateEmptyRepoFilesValueSource.Params> {

    @Inject
    public CreateEmptyRepoFilesValueSource() {
    }

    interface Params extends ValueSourceParameters {
        DirectoryProperty getRepoDirectory();
    }

    @Nullable
    @Override
    public String obtain() {
        var repoDir = getParameters().getRepoDirectory().get().getAsFile().toPath();

        var emptyJarFile = repoDir.resolve("minecraft/neoforge-minecraft-joined/local/neoforge-minecraft-joined-local.jar");
        try {
            Files.createDirectories(emptyJarFile.getParent());
            Files.createFile(emptyJarFile);
        } catch (FileAlreadyExistsException ignored) {
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        var pomFile = repoDir.resolve("minecraft/neoforge-minecraft-joined/local/neoforge-minecraft-joined-local.pom");
        try {
            Files.createDirectories(pomFile.getParent());
            Files.writeString(pomFile, """
                    <project xmlns="http://maven.apache.org/POM/4.0.0"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                        <modelVersion>4.0.0</modelVersion>

                        <groupId>minecraft</groupId>
                        <artifactId>neoforge-minecraft-joined</artifactId>
                        <version>local</version>
                        <packaging>jar</packaging>
                    </project>
                    """, StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException ignored) {
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // See class-level comment as to why this is constant
        return "";
    }
}
