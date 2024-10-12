package net.neoforged.moddevgradle.internal;

import com.google.gson.Gson;
import org.gradle.api.GradleException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.Map;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Sourced from the userdev config json. The run templates are the only thing that we use.
 */
public record UserDevConfig(Map<String, UserDevRunType> runs) implements Serializable {
    public static UserDevConfig from(File userDevFile) {
        // For backwards compatibility reasons we also support loading this from the userdev jar
        if (userDevFile.getName().endsWith(".jar")) {
            try (var zf = new ZipFile(userDevFile)) {
                var configJson = zf.getEntry("config.json");
                if (configJson != null) {
                    try (var in = zf.getInputStream(configJson);
                         var reader = new BufferedReader(new InputStreamReader(in))) {
                        return new Gson().fromJson(reader, UserDevConfig.class);
                    } catch (Exception e) {
                        throw new GradleException("Failed to read NeoForge config file from " + userDevFile, e);
                    }
                }
            } catch (IOException e) {
                throw new GradleException("Failed to read NeoForge config file from " + userDevFile, e);
            }
        }

        try (var reader = Files.newBufferedReader(userDevFile.toPath())) {
            return new Gson().fromJson(reader, UserDevConfig.class);
        } catch (Exception e) {
            throw new GradleException("Failed to read NeoForge config file from " + userDevFile, e);
        }
    }
}

