package net.neoforged.moddevgradle.internal;

import com.google.gson.Gson;
import org.gradle.api.GradleException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.zip.ZipFile;

/**
 * Sourced from the userdev config json. The run templates are the only thing that we use.
 */
public record UserDevConfig(Map<String, UserDevRunType> runs, String binpatches) implements Serializable {
    public static UserDevConfig from(InputStream in) {
        return new Gson().fromJson(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)), UserDevConfig.class);
    }
}
