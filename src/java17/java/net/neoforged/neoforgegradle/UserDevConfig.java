package net.neoforged.neoforgegradle;

import com.google.gson.Gson;
import org.gradle.api.GradleException;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

public record UserDevConfig(String mcp, String sources, String universal, List<String> libraries, List<String> modules,
                            Map<String, UserDevRunType> runs) implements Serializable {
    public static UserDevConfig from(File userDevFile) {
        try (var reader = Files.newBufferedReader(userDevFile.toPath())) {
            return new Gson().fromJson(reader, UserDevConfig.class);
        } catch (Exception e) {
            throw new GradleException("Failed to read NeoForge config file from " + userDevFile, e);
        }
    }
}

