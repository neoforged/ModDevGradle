package net.neoforged.moddevgradle.internal.utils;

import java.util.Objects;
import java.util.regex.Pattern;

public final class MinecraftVersionUtils {
    private MinecraftVersionUtils() {
    }

    private static final Pattern RELEASE_PATTERN = Pattern.compile("1\\.(\\d+)(?:.(\\d+))?(?:-.*)?$");

    record VersionCapabilities(int javaVersion, boolean splitDataRuns) {
    }

    /**
     * Tries to get the version capabilities from a given version number of one of the following modules:
     * <ul>
     *     <li>Vanilla Minecraft Version</li>
     *     <li>MCP Version (which is a Vanilla Minecraft Version)</li>
     *     <li>NeoForm Version (which corresponds to Vanilla Minecraft Version followed by a separator and additional info)</li>
     *     <li>NeoForge Version</li>
     *     <li>Minecraft Forge Version</li>
     * </ul>
     *
     */
    public static VersionCapabilities fromVersion(String version) {
        
    }

    /**
     * Checks whether the provided NeoForm version should have split client and server data runs.
     */
    public static boolean hasSplitDataRuns(String neoFormVersion) {
        // Snapshots starting from 24w45a
        if (neoFormVersion.length() >= 5 && neoFormVersion.charAt(2) == 'w') {
            try {
                var year = Integer.parseInt(neoFormVersion.substring(0, 2));
                var week = Integer.parseInt(neoFormVersion.substring(3, 5));

                return year > 24 || (year == 24 && week >= 45);
            } catch (NumberFormatException ignored) {
            }
        }
        // Releases starting from 1.21.4
        var matcher = RELEASE_PATTERN.matcher(neoFormVersion);
        if (matcher.find()) {
            try {
                int minor = Integer.parseInt(matcher.group(1));
                // If there is no patch version, the second group has a null value
                int patch = Integer.parseInt(Objects.requireNonNullElse(matcher.group(2), "0"));

                return minor > 21 || (minor == 21 && patch >= 4);
            } catch (NumberFormatException ignored) {
            }
        }
        // Assume other version patterns are newer and therefore split
        return true;
    }
}
