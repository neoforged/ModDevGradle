package net.neoforged.moddevgradle.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RunUtilsTest {
    @ParameterizedTest
    @CsvSource(textBlock = """
            ""|\\"\\"
            |
            a b|"a b"
            a" b|"a\\" b"
            a\\b|a\\\\b
            """, delimiter = '|')
    public void testEscape(String unescaped, String escaped) {
        escaped = escaped == null ? "" : escaped;
        unescaped = unescaped == null ? "" : unescaped;

        assertEquals(escaped, RunUtils.escapeJvmArg(unescaped));
    }
}
