package net.neoforged.moddevgradle.internal.utils;

import org.jetbrains.annotations.ApiStatus;

import java.nio.charset.Charset;
import java.util.Locale;
import java.util.regex.Pattern;

@ApiStatus.Internal
public final class StringUtils {
    private StringUtils() {
    }

    private static final Pattern NOT_LETTERS = Pattern.compile("\\P{L}");

    /**
     * Get the platform native charset. To see how this differs from the default charset,
     * see https://openjdk.org/jeps/400. This property cannot be overriden via system
     * property.
     */
    public static Charset getNativeCharset() {
        return NativeEncodingHolder.charset;
    }

    private static class NativeEncodingHolder {
        static final Charset charset;

        static {
            var nativeEncoding = System.getProperty("native.encoding");
            if (nativeEncoding == null) {
                throw new IllegalStateException("The native.encoding system property is not available, but should be since Java 17!");
            }
            charset = Charset.forName(nativeEncoding);
        }
    }

    /**
     * Converts an arbitrary input string to a sanitized camel case string.
     */
    public static String toCamelCase(String input, boolean lower) {
        var parts = NOT_LETTERS.split(input);
        if (parts.length > 0 && lower) {
            parts[0] = uncapitalize(parts[0]);
        }
        for (int i = lower ? 1 : 0; i < parts.length; ++i) {
            parts[i] = capitalize(parts[i]);
        }
        return String.join("", parts);
    }

    public static String capitalize(String input) {
        if (input.isEmpty()) {
            return "";
        }
        return input.substring(0, 1).toUpperCase(Locale.ROOT) + input.substring(1);
    }

    public static String uncapitalize(String input) {
        if (input.isEmpty()) {
            return "";
        }
        return input.substring(0, 1).toLowerCase(Locale.ROOT) + input.substring(1);
    }
}
