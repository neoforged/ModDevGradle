package net.neoforged.minecraftdependencies;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Applies defaults for the Gradle attributes introduced by the <a href="https://github.com/neoforged/GradleMinecraftDependencies">Minecraft Dependencies modules</a>.
 * <p>
 * The defaults are:
 * <ul>
 * <li>{@code net.neoforged.distribution} defaults to {@code client}</li>
 * <li>{@code net.neoforged.operatingsystem} defaults to the current operating system</li>
 * </ul>
 */
public class MinecraftDependenciesPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getDependencies().attributesSchema(attributesSchema -> {
            // Set up a disambiguation that by default selects the client distribution libraries
            // This happens under the assumption that client is usually a superset of server.
            var defaultDistribution = project.getObjects().named(MinecraftDistribution.class, MinecraftDistribution.CLIENT);
            attributesSchema.attribute(MinecraftDistribution.ATTRIBUTE).getDisambiguationRules().add(DistributionDisambiguationRule.class, spec -> spec.params(
                    defaultDistribution));

            var defaultOperatingSystem = project.getObjects().named(OperatingSystem.class, getDefaultOperatingSystem());
            attributesSchema.attribute(OperatingSystem.ATTRIBUTE).getDisambiguationRules().add(OperatingSystemDisambiguationRule.class, spec -> spec.params(
                    defaultOperatingSystem));
        });
    }

    private static String getDefaultOperatingSystem() {
        return switch (net.neoforged.moddevgradle.internal.utils.OperatingSystem.current()) {
            case LINUX -> OperatingSystem.LINUX;
            case MACOS -> OperatingSystem.MACOSX;
            case WINDOWS -> OperatingSystem.WINDOWS;
        };
    }
}
