package net.neoforged.nfrtgradle;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Properties;

public record DownloadedAssetsReference(String assetIndex, String assetsRoot) {
    public static DownloadedAssetsReference loadProperties(File file) {
        Properties assetProperties = new Properties();
        try (var input = new BufferedInputStream(new FileInputStream(file))) {
            assetProperties.load(input);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load asset properties", e);
        }
        if (!assetProperties.containsKey("assets_root")) {
            throw new IllegalStateException("Asset properties file does not contain assets_root");
        }
        if (!assetProperties.containsKey("asset_index")) {
            throw new IllegalStateException("Asset properties file does not contain asset_index");
        }

        return new DownloadedAssetsReference(
                assetProperties.getProperty("asset_index"),
                assetProperties.getProperty("assets_root"));
    }
}
