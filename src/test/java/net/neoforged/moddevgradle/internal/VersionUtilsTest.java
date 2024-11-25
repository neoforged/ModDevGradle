package net.neoforged.moddevgradle.internal;

import net.neoforged.moddevgradle.internal.utils.VersionUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionUtilsTest {
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
        assertThat(VersionUtils.hasSplitDataRuns(neoFormVersion))
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
        assertThat(VersionUtils.hasSplitDataRuns(neoFormVersion))
                .isTrue();
    }
}
