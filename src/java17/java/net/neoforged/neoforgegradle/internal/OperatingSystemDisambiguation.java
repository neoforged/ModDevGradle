package net.neoforged.neoforgegradle.internal;


import org.gradle.api.GradleException;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.MultipleCandidatesDetails;

public abstract class OperatingSystemDisambiguation implements AttributeDisambiguationRule<String> {
    @Override
    public void execute(MultipleCandidatesDetails<String> details) {
        var osName = System.getProperty("os.name");
        // The following matches the logic in Apache Commons Lang 3 SystemUtils
        if (osName.startsWith("Linux") || osName.startsWith("LINUX")) {
            osName = "linux";
        } else if (osName.startsWith("Mac OS X")) {
            osName = "macosx";
        } else if (osName.startsWith("Windows")) {
            osName = "windows";
        } else {
            throw new GradleException("Unsupported operating system: " + osName);
        }

        details.closestMatch(osName);
    }
}
