package net.neoforged.moddevgradle.internal.utils;

import net.neoforged.moddevgradle.internal.generated.MinecraftVersionList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

public record VersionCapabilities(int javaVersion, boolean splitDataRuns) {
    private static final Logger LOG = LoggerFactory.getLogger(VersionCapabilities.class);

    private static final VersionCapabilities LATEST = new VersionCapabilities(21, true);

    private static final Pattern NEOFORGE_PATTERN = Pattern.compile("^(\\d+\\.\\d+)\\.\\d+(|-.*)$");

    private static final int MC_24W45A_INDEX = getReferenceVersionIndex("24w45a");
    private static final int MC_24W14A_INDEX = getReferenceVersionIndex("24w14a");
    private static final int MC_1_18_PRE2_INDEX = getReferenceVersionIndex("1.18-pre2");
    private static final int MC_21W19A_INDEX = getReferenceVersionIndex("21w19a");

    /**
     * Minecraft version optionally followed by a separator (. or -) and other information.
     * This will parse 1.21.2-rc as 1.21.2
     */
    private static final Pattern LEADING_RELEASE_PATTERN = Pattern.compile("^(\\d+\\.\\d+)\\.\\d+(|[.-].*)$");

    public static VersionCapabilities ofMinecraftVersion(String minecraftVersion) {
        var versionIndex = getReferenceVersionIndex(minecraftVersion);
        if (versionIndex == -1) {
            LOG.info("Minecraft Version {} is unknown. Assuming latest capabilities.", versionIndex);
            return LATEST;
        }

        var javaVersion = getJavaVersion(versionIndex);
        var splitData = hasSplitDataEntrypoints(versionIndex);

        return new VersionCapabilities(javaVersion, splitData);
    }

    static int getJavaVersion(int versionIndex) {
        if (versionIndex >= MC_24W14A_INDEX) {
            return 21;
        } else if (versionIndex >= MC_1_18_PRE2_INDEX) {
            return 17;
        } else if (versionIndex >= MC_21W19A_INDEX) {
            return 16;
        } else {
            return 8;
        }
    }

    static boolean hasSplitDataEntrypoints(int versionIndex) {
        return versionIndex >= MC_24W45A_INDEX;
    }

    public static VersionCapabilities ofNeoForgeVersion(String version) {
        // NeoForge omits the "1." at the start of the Minecraft version and just adds an incrementing last digit
        var matcher = NEOFORGE_PATTERN.matcher(version);
        if (!matcher.matches()) {
            LOG.warn("Failed to parse MC version from NeoForge version {}. Using capabilities of latest known Minecraft version.", version);
            return LATEST;
        }

        var minecraftVersion = "1." + matcher.group(1);

        return ofMinecraftVersion(minecraftVersion);
    }

    public static VersionCapabilities ofNeoFormVersion(String version) {
        var matcher = LEADING_RELEASE_PATTERN.matcher(version);
        if (matcher.matches()) {
            return ofMinecraftVersion(matcher.group(1));
        }

        LOG.warn("Failed to parse MC version from NeoForm version {}. Using capabilities of latest known Minecraft version.", version);
        return LATEST;
    }

    public static VersionCapabilities ofForgeVersion(String forgeVersion) {
        return ofMinecraftVersion(forgeVersion);
    }

    private static int getReferenceVersionIndex(String v) {
        var idx = MinecraftVersionList.VERSIONS.indexOf(v);
        if (idx == -1) {
            throw new IllegalArgumentException("Reference version " + v + " is not in version list!");
        }
        return idx;
    }
}
