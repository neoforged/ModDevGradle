package net.neoforged.neoforgegradle;

import com.google.gson.Gson;
import org.gradle.api.GradleException;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

public record UserDevConfig(String mcp, String sources, String universal, List<String> libraries, List<String> modules,
                            Map<String, UserDevRunType> runs) implements Serializable {
    public static UserDevConfig from(File userDevFile) {
        try (var zf = new ZipFile(userDevFile)) {
            var configEntry = zf.getEntry("config.json");
            if (configEntry == null) {
                throw new IOException("The NeoForge Userdev artifact is missing a config.json file");
            }

            try (var in = zf.getInputStream(configEntry);
                 var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return new Gson().fromJson(reader, UserDevConfig.class);
            }
        } catch (Exception e) {
            throw new GradleException("Failed to read NeoForge config file from " + userDevFile, e);
        }
    }
}

