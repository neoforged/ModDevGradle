package net.neoforged.neoforgegradle;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Properties;

public final class RunUtils {
    private RunUtils() {
    }

    public static AssetProperties loadAssetProperties(File file) {
        Properties assetProperties = new Properties();
        try (var input = new BufferedInputStream(new FileInputStream(file))) {
            assetProperties.load(input);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load asset properties", e);
        }
        if (!assetProperties.containsKey("assets_root")) {
            throw new IllegalStateException("Asset properties file does not contain assets_root");
        }
        if (!assetProperties.containsKey("asset_index")) {
            throw new IllegalStateException("Asset properties file does not contain asset_index");
        }

        return new AssetProperties(
                assetProperties.getProperty("asset_index"),
                assetProperties.getProperty("assets_root")
        );
    }

    public static File writeLog4j2Configuration(File runDir) throws IOException {
        var log4j2Xml = new File(runDir, "log4j2.xml");

        Files.writeString(log4j2Xml.toPath(), """
                <?xml version="1.0" encoding="UTF-8"?>
                <Configuration status="warn" shutdownHook="disable">
                    <filters>
                        <ThresholdFilter level="WARN" onMatch="ACCEPT" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="NETWORK_PACKETS" onMatch="${sys:forge.logging.marker.networking:-DENY}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="CLASSLOADING" onMatch="${sys:forge.logging.marker.classloading:-DENY}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="LAUNCHPLUGIN" onMatch="${sys:forge.logging.marker.launchplugin:-DENY}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="CLASSDUMP" onMatch="${sys:forge.logging.marker.classdump:-DENY}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="AXFORM" onMatch="${sys:forge.logging.marker.axform:-DENY}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="EVENTBUS" onMatch="${sys:forge.logging.marker.eventbus:-DENY}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="DISTXFORM" onMatch="${sys:forge.logging.marker.distxform:-DENY}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="SCAN" onMatch="${sys:forge.logging.marker.scan:-DENY}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="REGISTRIES" onMatch="${sys:forge.logging.marker.registries:-DENY}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="REGISTRYDUMP" onMatch="${sys:forge.logging.marker.registrydump:-DENY}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="SPLASH" onMatch="${sys:forge.logging.marker.splash:-DENY}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="RESOURCE-CACHE" onMatch="${sys:forge.logging.marker.resource.cache:-DENY}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="FORGEMOD" onMatch="${sys:forge.logging.marker.forgemod:-ACCEPT}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="LOADING" onMatch="${sys:forge.logging.marker.loading:-ACCEPT}" onMismatch="NEUTRAL"/>
                        <MarkerFilter marker="CORE" onMatch="${sys:forge.logging.marker.core:-ACCEPT}" onMismatch="NEUTRAL"/>
                    </filters>
                    <Appenders>
                        <Console name="Console">
                            <PatternLayout>
                                <LoggerNamePatternSelector defaultPattern="%highlightForge{[%d{HH:mm:ss}] [%t/%level] [%c{2.}/%markerSimpleName]: %minecraftFormatting{%msg{nolookup}}%n%tEx}">
                                    <!-- don't include the full logger name for Mojang's logs since they use full class names and it's very verbose -->
                                    <PatternMatch key="net.minecraft." pattern="%highlightForge{[%d{HH:mm:ss}] [%t/%level] [minecraft/%logger{1}]: %minecraftFormatting{%msg{nolookup}}%n%tEx}"/>
                                    <PatternMatch key="com.mojang." pattern="%highlightForge{[%d{HH:mm:ss}] [%t/%level] [mojang/%logger{1}]: %minecraftFormatting{%msg{nolookup}}%n%tEx}"/>
                                </LoggerNamePatternSelector>
                            </PatternLayout>
                        </Console>
                        <Queue name="ServerGuiConsole" ignoreExceptions="true">
                            <PatternLayout>
                                <LoggerNamePatternSelector defaultPattern="[%d{HH:mm:ss}] [%t/%level] [%c{2.}/%markerSimpleName]: %minecraftFormatting{%msg{nolookup}}{strip}%n">
                                    <!-- don't include the full logger name for Mojang's logs since they use full class names and it's very verbose -->
                                    <PatternMatch key="net.minecraft." pattern="[%d{HH:mm:ss}] [%t/%level] [minecraft/%logger{1}]: %minecraftFormatting{%msg{nolookup}}{strip}%n"/>
                                    <PatternMatch key="com.mojang." pattern="[%d{HH:mm:ss}] [%t/%level] [mojang/%logger{1}]: %minecraftFormatting{%msg{nolookup}}{strip}%n"/>
                                </LoggerNamePatternSelector>
                            </PatternLayout>
                        </Queue>
                        <RollingRandomAccessFile name="File" fileName="logs/latest.log" filePattern="logs/%d{yyyy-MM-dd}-%i.log.gz">
                            <PatternLayout pattern="[%d{ddMMMyyyy HH:mm:ss.SSS}] [%t/%level] [%logger/%markerSimpleName]: %minecraftFormatting{%msg{nolookup}}{strip}%n%xEx"/>
                            <Policies>
                                <TimeBasedTriggeringPolicy/>
                                <OnStartupTriggeringPolicy/>
                            </Policies>
                            <DefaultRolloverStrategy max="99" fileIndex="min"/>
                        </RollingRandomAccessFile>
                        <RollingRandomAccessFile name="DebugFile" fileName="logs/debug.log" filePattern="logs/debug-%i.log.gz">
                            <PatternLayout pattern="[%d{ddMMMyyyy HH:mm:ss.SSS}] [%t/%level] [%logger/%markerSimpleName]: %minecraftFormatting{%msg{nolookup}}{strip}%n%xEx"/>
                            <Policies>
                                <OnStartupTriggeringPolicy/>
                                <SizeBasedTriggeringPolicy size="200MB"/>
                            </Policies>
                            <DefaultRolloverStrategy max="5" fileIndex="min"/>
                        </RollingRandomAccessFile>
                    </Appenders>
                    <Loggers>
                        <!-- make sure mojang's logging is set to 'info' so that their LOGGER.isDebugEnabled() behavior isn't active -->
                        <Logger level="${sys:forge.logging.mojang.level:-info}" name="com.mojang"/>
                        <Logger level="${sys:forge.logging.mojang.level:-info}" name="net.minecraft"/>
                        <Logger level="${sys:forge.logging.classtransformer.level:-info}" name="cpw.mods.modlauncher.ClassTransformer"/>

                        <!-- Netty reflects into JDK internals, and it's causing useless DEBUG-level error stacktraces. We just ignore them -->
                        <Logger name="io.netty.util.internal.PlatformDependent0">
                            <filters>
                                <RegexFilter regex="^direct buffer constructor: unavailable$" onMatch="DENY" onMismatch="NEUTRAL" />
                                <RegexFilter regex="^jdk\\.internal\\.misc\\.Unsafe\\.allocateUninitializedArray\\(int\\): unavailable$" onMatch="DENY" onMismatch="NEUTRAL" />
                            </filters>
                        </Logger>

                        <Root level="debug">
                            <AppenderRef ref="Console" level="debug"/>
                            <AppenderRef ref="ServerGuiConsole" level="${sys:forge.logging.console.level:-info}"/>
                            <AppenderRef ref="File" level="${sys:forge.logging.file.level:-info}"/>
                            <AppenderRef ref="DebugFile" level="${sys:forge.logging.debugFile.level:-debug}"/>
                        </Root>
                    </Loggers>
                </Configuration>

                """);

        return log4j2Xml;
    }
}

record AssetProperties(String assetIndex, String assetsRoot) {
}
