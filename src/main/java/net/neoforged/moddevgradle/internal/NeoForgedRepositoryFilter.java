package net.neoforged.moddevgradle.internal;

import java.util.Set;
import org.gradle.api.artifacts.repositories.RepositoryContentDescriptor;

/**
 * Controls which modules Gradle may resolve from the NeoForged Maven.
 * <p>
 * The NeoForged Maven mirrors many Maven Central artifacts. Without a content filter,
 * Gradle could resolve arbitrary third-party artifacts from it.
 * <p>
 * This filter is built from two layers:
 * <ul>
 * <li><b>Stable baseline</b> — the full set of modules known to be hosted on the
 * NeoForged Maven. This covers NeoForge artifacts, modding toolchain projects, and
 * their transitive dependencies at the time the plugin was built.</li>
 * <li><b>Dynamic modules</b> — discovered at configuration time by downloading the
 * Gradle Module Metadata for the selected NeoForge and NeoForm Runtime versions.
 * The caller passes these in as a {@code Set<String>} keyed by {@code "group:module"}.
 * This allows new Minecraft releases to work without a plugin update, provided
 * their libraries are also mirrored.</li>
 * </ul>
 */
public class NeoForgedRepositoryFilter {
    /**
     * All modules currently known to be hosted on the NeoForged Maven.
     * When the selected NeoForge version pulls in a library that is not yet listed here,
     * the dynamic discovery path in {@link net.neoforged.moddevgradle.internal.ModDevPlugin}
     * adds it at configuration time.
     */
    // @formatter:off
    private static final String[][] STABLE_MODULES = {
            // --- net.neoforged sub-groups ---
            {"net.neoforged.accesstransformers", "at-modlauncher"},
            {"net.neoforged.accesstransformers", "at-parser"},
            {"net.neoforged.fancymodloader", "earlydisplay"},
            {"net.neoforged.fancymodloader", "junit-fml"},
            {"net.neoforged.fancymodloader", "loader"},
            {"net.neoforged.installertools", "binarypatcher"},
            {"net.neoforged.installertools", "cli-utils"},
            {"net.neoforged.installertools", "installertools"},
            {"net.neoforged.javadoctor", "gson-io"},
            {"net.neoforged.javadoctor", "spec"},
            {"net.neoforged.jst", "jst-cli-bundle"},
            // --- main net.neoforged group ---
            {"net.neoforged", "AutoRenamingTool"},
            {"net.neoforged", "DevLaunch"},
            {"net.neoforged", "JarJarFileSystems"},
            {"net.neoforged", "JarJarMetadata"},
            {"net.neoforged", "JarJarSelector"},
            {"net.neoforged", "accesstransformers"},
            {"net.neoforged", "bus"},
            {"net.neoforged", "coremods"},
            {"net.neoforged", "mergetool"},
            {"net.neoforged", "minecraft-dependencies"},
            {"net.neoforged", "neoforge"},
            {"net.neoforged", "neoform"},
            {"net.neoforged", "neoform-runtime"},
            {"net.neoforged", "srgutils"},
            {"net.neoforged", "testframework"},
            // --- modding toolchain ---
            {"cpw.mods", "bootstraplauncher"},
            {"cpw.mods", "modlauncher"},
            {"cpw.mods", "securejarhandler"},
            {"net.minecraftforge", "mergetool"},
            {"net.minecraftforge", "srgutils"},
            {"net.minecrell", "terminalconsoleappender"},
            {"net.covers1624", "DevLogin"},
            {"net.covers1624", "Quack"},
            {"io.codechicken", "DiffPatch"},
            {"io.github.llamalad7", "mixinextras-neoforge"},
            {"net.fabricmc", "sponge-mixin"},
            {"de.siegmar", "fastcsv"},
            {"com.machinezoo.noexception", "noexception"},
            {"net.jodah", "typetools"},
            {"com.nothome", "javaxdelta"},
            {"trove", "trove"},
            // --- NeoForge-maintained library forks ---
            {"com.electronwill.night-config", "core"},
            {"com.electronwill.night-config", "toml"},
            // --- Transitive dependencies of NFRT external tools ---
            {"it.unimi.dsi", "fastutil"},
            {"org.apache.commons", "commons-compress"},
            {"org.apache.commons", "commons-lang3"},
            {"org.apache.commons", "commons-parent"},
            {"org.tukaani", "xz"},
            {"org.ow2.asm", "asm"},
            {"org.ow2.asm", "asm-analysis"},
            {"org.ow2.asm", "asm-commons"},
            {"org.ow2.asm", "asm-tree"},
            {"org.ow2.asm", "asm-util"},
            {"org.ow2", "ow2"},
            {"org.lz4", "lz4-java"},
            {"org.jcraft", "jorbis"},
            // --- Game libraries & their transitive dependencies ---
            // Transitive dependencies of common game libraries are listed explicitly
            // because the dynamic discovery only sees direct dependencies (a single
            // module-metadata download), not the full transitive tree.
            {"ca.weblite", "java-objc-bridge"},
            {"com.fasterxml.jackson.core", "jackson-annotations"},
            {"com.fasterxml.jackson.core", "jackson-core"},
            {"com.fasterxml.jackson", "jackson-base"},
            {"com.fasterxml.jackson", "jackson-bom"},
            {"com.fasterxml.jackson", "jackson-parent"},
            {"com.fasterxml", "oss-parent"},
            {"com.github.oshi", "oshi-core"},
            {"com.github.oshi", "oshi-parent"},
            {"com.google.code.findbugs", "jsr305"},
            {"com.google.code.gson", "gson"},
            {"com.google.code.gson", "gson-parent"},
            {"com.google.errorprone", "error_prone_annotations"},
            {"com.google.errorprone", "error_prone_parent"},
            {"com.google.guava", "failureaccess"},
            {"com.google.guava", "guava"},
            {"com.google.guava", "guava-parent"},
            {"com.google.guava", "listenablefuture"},
            {"com.google.j2objc", "j2objc-annotations"},
            {"com.ibm.icu", "icu4j"},
            {"com.mojang", "authlib"},
            {"com.mojang", "blocklist"},
            {"com.mojang", "brigadier"},
            {"com.mojang", "datafixerupper"},
            {"com.mojang", "logging"},
            {"com.mojang", "patchy"},
            {"com.mojang", "text2speech"},
            {"commons-codec", "commons-codec"},
            {"commons-io", "commons-io"},
            {"commons-logging", "commons-logging"},
            {"io.fabric8", "kubernetes-client-bom"},
            {"io.netty", "netty-bom"},
            {"io.netty", "netty-buffer"},
            {"io.netty", "netty-codec"},
            {"io.netty", "netty-common"},
            {"io.netty", "netty-handler"},
            {"io.netty", "netty-parent"},
            {"io.netty", "netty-resolver"},
            {"io.netty", "netty-transport"},
            {"io.netty", "netty-transport-classes-epoll"},
            {"io.netty", "netty-transport-native-unix-common"},
            {"jakarta.platform", "jakarta.jakartaee-bom"},
            {"jakarta.platform", "jakartaee-api-parent"},
            {"net.java.dev.jna", "jna"},
            {"net.java.dev.jna", "jna-platform"},
            {"org.antlr", "antlr4-master"},
            {"org.antlr", "antlr4-runtime"},
            {"org.apache.groovy", "groovy-bom"},
            {"org.apache.httpcomponents", "httpclient"},
            {"org.apache.httpcomponents", "httpcomponents-client"},
            {"org.apache.httpcomponents", "httpcomponents-core"},
            {"org.apache.httpcomponents", "httpcomponents-parent"},
            {"org.apache.httpcomponents", "httpcore"},
            {"org.apache.logging.log4j", "log4j"},
            {"org.apache.logging.log4j", "log4j-api"},
            {"org.apache.logging.log4j", "log4j-bom"},
            {"org.apache.logging.log4j", "log4j-core"},
            {"org.apache.logging.log4j", "log4j-slf4j2-impl"},
            {"org.apache.logging", "logging-parent"},
            {"org.apache.maven", "maven"},
            {"org.apache.maven", "maven-artifact"},
            {"org.apache.maven", "maven-parent"},
            {"org.apache", "apache"},
            {"org.apiguardian", "apiguardian-api"},
            {"org.checkerframework", "checker-qual"},
            {"org.codehaus.groovy", "groovy-bom"},
            {"org.codehaus.plexus", "plexus"},
            {"org.codehaus.plexus", "plexus-utils"},
            {"org.commonmark", "commonmark"},
            {"org.commonmark", "commonmark-parent"},
            {"org.eclipse.ee4j", "project"},
            {"org.eclipse.jetty", "jetty-bom"},
            {"org.jetbrains", "annotations"},
            {"org.jline", "jline-parent"},
            {"org.jline", "jline-reader"},
            {"org.jline", "jline-terminal"},
            {"org.joml", "joml"},
            {"org.jspecify", "jspecify"},
            {"org.junit.jupiter", "junit-jupiter"},
            {"org.junit.jupiter", "junit-jupiter-api"},
            {"org.junit.jupiter", "junit-jupiter-engine"},
            {"org.junit.jupiter", "junit-jupiter-params"},
            {"org.junit.platform", "junit-platform-commons"},
            {"org.junit.platform", "junit-platform-engine"},
            {"org.junit.platform", "junit-platform-launcher"},
            {"org.junit", "junit-bom"},
            {"org.lwjgl", "lwjgl"},
            {"org.lwjgl", "lwjgl-bom"},
            {"org.lwjgl", "lwjgl-freetype"},
            {"org.lwjgl", "lwjgl-glfw"},
            {"org.lwjgl", "lwjgl-jemalloc"},
            {"org.lwjgl", "lwjgl-openal"},
            {"org.lwjgl", "lwjgl-opengl"},
            {"org.lwjgl", "lwjgl-stb"},
            {"org.lwjgl", "lwjgl-tinyfd"},
            {"org.mockito", "mockito-bom"},
            {"org.opentest4j", "opentest4j"},
            {"org.slf4j", "slf4j-api"},
            {"org.slf4j", "slf4j-bom"},
            {"org.slf4j", "slf4j-parent"},
            {"org.sonatype.oss", "oss-parent"},
            {"org.springframework", "spring-framework-bom"},
            // --- Other NeoForge-hosted tooling ---
            {"org.parchmentmc.data", "parchment-1.21"},
            {"org.vineflower", "vineflower"},
            {"org.openjdk.nashorn", "nashorn-core"},
            {"net.sf.jopt-simple", "jopt-simple"},
    };
    // @formatter:on

    /**
     * Applies the full filter to the given repository content descriptor: stable
     * known modules plus caller-supplied dynamically discovered modules.
     *
     * @param descriptor     the repository content descriptor to configure
     * @param dynamicModules set of {@code "group:module"} strings discovered at
     *                       configuration time; may be empty but never null
     */
    public static void filter(RepositoryContentDescriptor descriptor, Set<String> dynamicModules) {
        for (var entry : STABLE_MODULES) {
            descriptor.includeModule(entry[0], entry[1]);
        }

        for (var coordinate : dynamicModules) {
            var parts = coordinate.split(":", 2);
            if (parts.length == 2) {
                descriptor.includeModule(parts[0], parts[1]);
            }
        }
    }
}
