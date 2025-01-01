package net.neoforged.moddevgradle.internal.utils;

import java.nio.charset.Charset;
import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class StringUtils {
    private StringUtils() {}

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
