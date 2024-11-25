package net.neoforged.moddevgradle.internal.utils;

import java.util.Objects;
import java.util.regex.Pattern;

public final class VersionUtils {
    private VersionUtils() {}

    private static final Pattern RELEASE_PATTERN = Pattern.compile("1\\.(\\d+)(?:.(\\d+))?(?:-.*)?$");

    /**
     * Checks whether the provided NeoForm version should have a single data run,
     * rather than separate client and server data runs.
     */
    public static boolean hasSingleDataRun(String neoFormVersion) {
        // Snapshots before 24w45a
        if (neoFormVersion.length() >= 5 && neoFormVersion.charAt(2) == 'w') {
            try {
                var year = Integer.parseInt(neoFormVersion.substring(0, 2));
                var week = Integer.parseInt(neoFormVersion.substring(3, 5));

                return year < 24 || (year == 24 && week < 45);
            } catch (NumberFormatException ignored) {}
        }
        // Releases before 1.21.4
        var matcher = RELEASE_PATTERN.matcher(neoFormVersion);
        if (matcher.find()) {
            try {
                int minor = Integer.parseInt(matcher.group(1));
                // If there is no patch version, the second group has a null value
                int patch = Integer.parseInt(Objects.requireNonNullElse(matcher.group(2), "0"));

                return minor < 21 || (minor == 21 && patch <= 3);
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }
}
