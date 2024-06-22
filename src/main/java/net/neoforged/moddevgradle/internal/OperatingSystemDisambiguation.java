package net.neoforged.moddevgradle.internal;

import net.neoforged.moddevgradle.internal.utils.OperatingSystem;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.MultipleCandidatesDetails;

/**
 * This disambiguation rule will select native dependencies based on the operating system Gradle is currently running on.
 */
public abstract class OperatingSystemDisambiguation implements AttributeDisambiguationRule<String> {
    @Override
    public void execute(MultipleCandidatesDetails<String> details) {
        details.closestMatch(switch (OperatingSystem.current()) {
            case LINUX -> "linux";
            case MACOS -> "osx";
            case WINDOWS -> "windows";
        });
    }
}
