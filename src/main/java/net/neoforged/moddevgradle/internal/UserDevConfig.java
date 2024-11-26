package net.neoforged.moddevgradle.internal;

import com.google.gson.Gson;
import org.gradle.api.GradleException;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.Map;

/**
 * Sourced from the userdev config json. The run templates are the only thing that we use.
 */
public record UserDevConfig(Map<String, UserDevRunType> runs) implements Serializable {
    public static UserDevConfig from(File userDevFile) {
        try (var reader = Files.newBufferedReader(userDevFile.toPath())) {
            return new Gson().fromJson(reader, UserDevConfig.class);
        } catch (Exception e) {
            throw new GradleException("Failed to read NeoForge config file from " + userDevFile, e);
        }
    }
}

