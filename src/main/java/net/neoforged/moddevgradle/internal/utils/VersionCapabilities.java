package net.neoforged.moddevgradle.internal.utils;

import net.neoforged.moddevgradle.internal.generated.MinecraftVersionList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.regex.Pattern;

/**
 * Models the changing capabilities of the modding platform and Vanilla, which we tie to the Minecraft version.
 * @param javaVersion Which Java version Vanilla uses to compile and run.
 * @param splitDataRuns Whether Vanilla has separate main classes for generating client and server data.
 */
public record VersionCapabilities(int javaVersion, boolean splitDataRuns) implements Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(VersionCapabilities.class);

    private static final VersionCapabilities LATEST = new VersionCapabilities(21, true);

    private static final Pattern NEOFORGE_PATTERN = Pattern.compile("^(\\d+\\.\\d+)\\.\\d+(|-.*)$");

    private static final int MC_24W45A_INDEX = getReferenceVersionIndex("24w45a");
    private static final int MC_24W14A_INDEX = getReferenceVersionIndex("24w14a");
    private static final int MC_1_18_PRE2_INDEX = getReferenceVersionIndex("1.18-pre2");
    private static final int MC_21W19A_INDEX = getReferenceVersionIndex("21w19a");

    public static VersionCapabilities latest() {
        return LATEST;
    }

    public static VersionCapabilities ofMinecraftVersion(String minecraftVersion) {
        var versionIndex = MinecraftVersionList.VERSIONS.indexOf(minecraftVersion);
        if (versionIndex == -1) {
            LOG.info("Minecraft Version {} is unknown. Assuming latest capabilities.", versionIndex);
            return LATEST;
        }

        return ofVersionIndex(versionIndex);
    }

    public static VersionCapabilities ofVersionIndex(int versionIndex) {
        var javaVersion = getJavaVersion(versionIndex);
        var splitData = hasSplitDataEntrypoints(versionIndex);

        return new VersionCapabilities(javaVersion, splitData);
    }

    static int getJavaVersion(int versionIndex) {
        if (versionIndex <= MC_24W14A_INDEX) {
            return 21;
        } else if (versionIndex <= MC_1_18_PRE2_INDEX) {
            return 17;
        } else if (versionIndex <= MC_21W19A_INDEX) {
            return 16;
        } else {
            return 8;
        }
    }

    static boolean hasSplitDataEntrypoints(int versionIndex) {
        return versionIndex <= MC_24W45A_INDEX;
    }

    static int indexOfNeoForgeVersion(String version) {
        // NeoForge omits the "1." at the start of the Minecraft version and just adds an incrementing last digit
        var matcher = NEOFORGE_PATTERN.matcher(version);
        if (!matcher.matches()) {
            return -1;
        }

        var mcVersion = "1." + matcher.group(1);
        // Versions such as 21.0.0 are for Minecraft 1.21 and NOT 1.21.0, therefore we strip the trailing .0
        if (mcVersion.endsWith(".0")) {
            mcVersion = mcVersion.substring(0, mcVersion.length() - 2);
        }
        return MinecraftVersionList.VERSIONS.indexOf(mcVersion);
    }

    public static VersionCapabilities ofNeoForgeVersion(String version) {
        var index = indexOfNeoForgeVersion(version);
        if (index == -1) {
            LOG.warn("Failed to parse MC version from NeoForge version {}. Using capabilities of latest known Minecraft version.", version);
            return LATEST;
        }

        return ofVersionIndex(index);
    }

    static int indexOfNeoFormVersion(String version) {
        // Examples: 1.21-<timestamp>
        return MinecraftVersionList.indexOfByPrefix(version, "-");
    }

    public static VersionCapabilities ofNeoFormVersion(String version) {
        var index = indexOfNeoFormVersion(version);
        if (index == -1) {
            LOG.warn("Failed to parse MC version from NeoForm version {}. Using capabilities of latest known Minecraft version.", version);
            return LATEST;
        }

        return ofVersionIndex(index);
    }

    static int indexOfForgeVersion(String version) {
        // Forge versions are generally "<mc-version>-<forge-version-and-stuff>"
        return MinecraftVersionList.indexOfByPrefix(version, "-");
    }

    public static VersionCapabilities ofForgeVersion(String version) {
        var index = indexOfForgeVersion(version);
        if (index == -1) {
            LOG.warn("Failed to parse MC version from Forge version {}. Using capabilities of latest known Minecraft version.", version);
            return LATEST;
        }

        return ofVersionIndex(index);
    }

    private static int getReferenceVersionIndex(String v) {
        var idx = MinecraftVersionList.VERSIONS.indexOf(v);
        if (idx == -1) {
            throw new IllegalArgumentException("Reference version " + v + " is not in version list!");
        }
        return idx;
    }
}
