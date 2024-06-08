package net.neoforged.moddevgradle.internal.utils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * When launching other Java programs externally, we have to pass through system properties that
 * change network settings, such as proxies and TLS trust settings.
 */
public class NetworkSettingPassthrough {

    /**
     * The list of system properties to pass through to NFRT if set.
     * Refer to <a href="https://docs.oracle.com/en%2Fjava%2Fjavase%2F17%2Fdocs%2Fapi%2F%2F/java.base/java/net/doc-files/net-properties.html">JRE Networking Properties</a>.
     */
    private static final Set<String> PROPERTIES = Set.of(
            "socksProxyHost",
            "socksProxyPort",
            "socksProxyVersion"
    );

    private static final List<String> PREFIXES = List.of(
            // HTTPS and HTTP proxy settings
            "http.",
            "https.",
            // All kinds of debugging properties for the network stack
            "java.net.",
            // All TLS related properties, like telling it to use Windows trust store
            // See https://docs.oracle.com/en/java/javase/17/security/java-secure-socket-extension-jsse-reference-guide.html#GUID-A41282C3-19A3-400A-A40F-86F4DA22ABA9__SYSTEMPROPERTIESANDCUSTOMIZEITEMSIN-DCEEB591
            "javax.net.ssl.",
            "jdk.tls."
    );

    private NetworkSettingPassthrough() {
    }

    public static Map<String, String> getNetworkSystemProperties() {
        var properties = System.getProperties();
        return properties.stringPropertyNames().stream()
                .filter(name -> {
                    if (PROPERTIES.contains(name)) {
                        return true;
                    }
                    for (String prefix : PREFIXES) {
                        if (name.startsWith(prefix)) {
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toMap(Function.identity(), properties::getProperty));

    }
}
