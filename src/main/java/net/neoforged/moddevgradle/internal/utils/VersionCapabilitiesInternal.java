package net.neoforged.moddevgradle.internal.utils;

import java.io.Serializable;
import java.util.regex.Pattern;
import net.neoforged.moddevgradle.dsl.VersionCapabilities;
import net.neoforged.moddevgradle.internal.generated.MinecraftVersionList;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/**
 * Models the changing capabilities of the modding platform and Vanilla, which we tie to the Minecraft version.
 *
 * @param minecraftVersion The Minecraft version.
 * @param javaVersion      Which Java version Vanilla uses to compile and run.
 * @param splitDataRuns    Whether Vanilla has separate main classes for generating client and server data.
 * @param testFixtures     If the NeoForge version for this Minecraft version supports test fixtures.
 */
public record VersionCapabilitiesInternal(String minecraftVersion, int javaVersion, boolean splitDataRuns,
        boolean testFixtures, boolean modLocatorRework, boolean legacyClasspath) implements VersionCapabilities, Serializable {

    private static final Logger LOG = Logging.getLogger(VersionCapabilitiesInternal.class);

    private static final VersionCapabilitiesInternal LATEST = ofVersionIndex(0);

    private static final Pattern NEOFORGE_PATTERN = Pattern.compile("^(\\d+\\.\\d+)\\.\\d+(|-.*)$");
    // Strips NeoForm timestamp suffixes OR dynamic version markers
    private static final Pattern NEOFORM_PATTERN = Pattern.compile("^(.*)-(?:\\+|\\d{8}\\.\\d{6})$");

    private static final int MC_1_21_9_INDEX = getReferenceVersionIndex("1.21.9");
    private static final int MC_24W45A_INDEX = getReferenceVersionIndex("24w45a");
    private static final int MC_1_20_5_INDEX = getReferenceVersionIndex("1.20.5");
    private static final int MC_24W14A_INDEX = getReferenceVersionIndex("24w14a");
    private static final int MC_1_20_4_INDEX = getReferenceVersionIndex("1.20.4");
    private static final int MC_1_18_PRE2_INDEX = getReferenceVersionIndex("1.18-pre2");
    private static final int MC_21W19A_INDEX = getReferenceVersionIndex("21w19a");
    public static VersionCapabilitiesInternal latest() {
        return LATEST;
    }

    public static VersionCapabilitiesInternal ofMinecraftVersion(String minecraftVersion) {
        var versionIndex = MinecraftVersionList.VERSIONS.indexOf(minecraftVersion);
        if (versionIndex == -1) {
            LOG.lifecycle("Minecraft Version {} is unknown. Assuming latest capabilities.", versionIndex);
            return LATEST.withMinecraftVersion(minecraftVersion);
        }

        return ofVersionIndex(versionIndex);
    }

    public static VersionCapabilitiesInternal ofVersionIndex(int versionIndex) {
        var minecraftVersion = MinecraftVersionList.VERSIONS.get(versionIndex);
        return ofVersionIndex(versionIndex, minecraftVersion);
    }

    public static VersionCapabilitiesInternal ofVersionIndex(int versionIndex, String minecraftVersion) {
        var javaVersion = getJavaVersion(versionIndex);
        var splitData = hasSplitDataEntrypoints(versionIndex);
        var testFixtures = hasTestFixtures(versionIndex);
        var modLocatorRework = hasModLocatorRework(versionIndex);
        var legacyClasspath = hasLegacyClasspath(versionIndex);

        return new VersionCapabilitiesInternal(minecraftVersion, javaVersion, splitData, testFixtures, modLocatorRework, legacyClasspath);
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

    static boolean hasTestFixtures(int versionIndex) {
        return versionIndex <= MC_1_20_4_INDEX;
    }

    static boolean hasModLocatorRework(int versionIndex) {
        return versionIndex <= MC_1_20_5_INDEX;
    }

    static boolean hasLegacyClasspath(int versionIndex) {
        return versionIndex > MC_1_21_9_INDEX;
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

    public static VersionCapabilitiesInternal ofNeoForgeVersion(String version) {
        var index = indexOfNeoForgeVersion(version);
        if (index == -1) {
            var capabilities = LATEST;
            var m = NEOFORGE_PATTERN.matcher(version);
            if (m.matches()) {
                var minecraftVersion = "1." + m.group(1);
                if (minecraftVersion.endsWith(".0")) {
                    minecraftVersion = minecraftVersion.substring(0, minecraftVersion.length() - 2);
                }
                capabilities = capabilities.withMinecraftVersion(minecraftVersion);
            }

            LOG.lifecycle("Failed to parse MC version from NeoForge version {}. Using capabilities of latest known Minecraft version with Minecraft version {}.", version, capabilities.minecraftVersion());
            return capabilities;
        }

        return ofVersionIndex(index);
    }

    public static VersionCapabilitiesInternal ofNeoFormVersion(String version) {
        // Examples: 1.21-<timestamp>
        var index = MinecraftVersionList.indexOfByPrefix(version, "-");

        if (index == -1) {
            var capabilities = LATEST;
            var m = NEOFORM_PATTERN.matcher(version);
            if (m.matches()) {
                capabilities = capabilities.withMinecraftVersion(m.group(1));
            } else {
                capabilities = capabilities.withMinecraftVersion(version);
            }

            LOG.lifecycle("Failed to parse MC version from NeoForm version {}. Using capabilities of latest known Minecraft version with Minecraft version {}.", version, capabilities.minecraftVersion());
            return capabilities;
        }

        return ofVersionIndex(index);
    }

    static int indexOfForgeVersion(String version) {
        // Forge versions are generally "<mc-version>-<forge-version-and-stuff>"
        return MinecraftVersionList.indexOfByPrefix(version, "-");
    }

    public static VersionCapabilitiesInternal ofForgeVersion(String version) {
        var index = indexOfForgeVersion(version);
        if (index == -1) {
            LOG.lifecycle("Failed to parse MC version from Forge version {}. Using capabilities of latest known Minecraft version.", version);
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

    public VersionCapabilitiesInternal withMinecraftVersion(String minecraftVersion) {
        return new VersionCapabilitiesInternal(
                minecraftVersion,
                javaVersion,
                splitDataRuns,
                testFixtures,
                modLocatorRework,
                legacyClasspath);
    }
}
