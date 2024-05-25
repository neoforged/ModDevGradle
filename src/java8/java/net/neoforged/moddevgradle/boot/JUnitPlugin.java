package net.neoforged.moddevgradle.boot;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * This is just a trampoline to perform the Java and Gradle version check, and is compiled for Java 8 in the real Jar.
 */
public class JUnitPlugin extends TrampolinePlugin<Project> {
    public JUnitPlugin() {
        super("net.neoforged.moddevgradle.internal.JUnitPlugin");
    }
}
