package net.neoforged.moddevgradle.internal.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import net.neoforged.moddevgradle.internal.generated.MinecraftVersionList;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class VersionCapabilitiesInternalTest {
    @ParameterizedTest()
    @CsvSource({
            "1.21.4,21",
            "1.21.4-pre1-20241120.190508,21",
            "1.21.3,21",
            "24w45a,21",
            "24w44a,21",
            "1.21.3-pre1,21",
            "25w01a,21",
            "23w07a,17",
            "1.20,17",
            "1.20-pre1,17",
            "1.21,21",
            "1.21-pre1-20240529.150918,21",
            "1.21-pre1,21",
            "1.22,21",
            "1.22-pre1,21",
            "1.0,8",
            "1.14.2 Pre-Release 2,8",
            "21w19a,16",
    })
    public void testJavaVersion(String neoFormVersion, int javaVersion) {
        assertThat(VersionCapabilitiesInternal.ofNeoFormVersion(neoFormVersion).javaVersion())
                .isEqualTo(javaVersion);
    }

    @ParameterizedTest()
    @CsvSource({
            "21.0.3-beta,1.21",
            "21.4.8-beta,1.21.4",
            "21.4.10-beta-pr-1744-gh-1582,1.21.4",
            "21.4.10,1.21.4",
            "26.1.10,1.26.1",
            "26.0.10,1.26",
    })
    public void testNeoForgeVersionParsing(String neoForgeVersion, String minecraftVersion) {
        var caps = VersionCapabilitiesInternal.ofNeoForgeVersion(neoForgeVersion);
        assertEquals(minecraftVersion, caps.minecraftVersion());
    }

    @ParameterizedTest()
    @CsvSource({
            "1.7.2-10.12.2.1161-mc172,1.7.2",
            "1.10-12.18.0.2000-1.10.0,1.10",
            "1.12.2-14.23.5.2860,1.12.2",
            "1.20.1-47.3.12,1.20.1",
    })
    public void testForgeVersionParsing(String forgeVersion, String minecraftVersion) {
        var idx = VersionCapabilitiesInternal.indexOfForgeVersion(forgeVersion);
        String actual;
        if (idx == -1) {
            actual = null;
        } else {
            actual = MinecraftVersionList.VERSIONS.get(idx);
        }
        assertEquals(minecraftVersion, actual);
    }

    @ParameterizedTest()
    @CsvSource({
            // This checks that a separator must follow the prefix match since this matches the 1.20.1 prefix, but
            // should not be recognized as such.
            "1.20.12-20241017.134216,1.20.12",
            "29w31a,29w31a",
            "29w31a-20230819.124900,29w31a",
            "1.20.1-20241017.134216,1.20.1",
            "1.20.12,1.20.12",
            "1.20.1,1.20.1",
            "1.20.1-rc1,1.20.1-rc1",
            "1.99.1-20241017.134216,1.99.1",
            "1.99.0-20241017.134216,1.99.0",
            // Dynamic version
            "1.99.0-+,1.99.0",
    })
    public void testNeoFormVersionParsing(String neoFormVersion, String minecraftVersion) {
        var actual = VersionCapabilitiesInternal.ofNeoFormVersion(neoFormVersion);
        assertEquals(minecraftVersion, actual.minecraftVersion());
    }

    @ParameterizedTest()
    @CsvSource({
            "1.21.4,true",
            "1.21.4-pre1-20241120.190508,true",
            "1.21.3,false",
            "24w45a,true",
            "24w44a,false",
            "1.21.3-pre1,false",
            "25w01a,true",
            "23w07a,false",
            "1.20,false",
            "1.20-pre1,false",
            "1.21,false",
            "1.21-pre1-20240529.150918,false",
            "1.21-pre1,false",
            "1.22,true",
            "1.22-pre1,true"
    })
    public void testSplitDataRunsCorrectness(String neoFormVersion, boolean splitDataRuns) {
        assertThat(VersionCapabilitiesInternal.ofNeoFormVersion(neoFormVersion).splitDataRuns())
                .isEqualTo(splitDataRuns);
    }

    @ParameterizedTest
    @CsvSource({
            "1",
            "1.",
            "1.21.",
            "test",
            "24w",
            "24w5",
            "24w50",
            "2aw50",
            "24242",
    })
    public void testSplitDataRunsDoesNotCrash(String neoFormVersion) {
        assertThat(VersionCapabilitiesInternal.ofNeoFormVersion(neoFormVersion).splitDataRuns())
                .isTrue();
    }
}
