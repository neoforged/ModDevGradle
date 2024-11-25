package net.neoforged.moddevgradle.internal;

import net.neoforged.moddevgradle.internal.utils.VersionUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionUtilsTest {
    @ParameterizedTest()
    @CsvSource({
            "1.21.4,false",
            "1.21.4-pre1-20241120.190508,false",
            "1.21.3,true",
            "24w45a,false",
            "24w44a,true",
            "1.21.3-pre1,true",
            "25w01a,false",
            "23w07a,true",
            "1.20,true",
            "1.20-pre1,true",
            "1.21,true",
            "1.21-pre1-20240529.150918,true",
            "1.21-pre1,true",
            "1.22,false",
            "1.22-pre1,false"
    })
    public void testSingleDataVersionCorrectness(String neoFormVersion, boolean singleDataRun) {
        assertThat(VersionUtils.hasSingleDataRun(neoFormVersion))
                .isEqualTo(singleDataRun);
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
    public void testSingleDataVersionDoesNotCrash(String neoFormVersion) {
        assertThat(VersionUtils.hasSingleDataRun(neoFormVersion))
                .isFalse();
    }
}
