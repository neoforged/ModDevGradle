package net.neoforged.moddevgradle.dsl;

import javax.inject.Inject;
import net.neoforged.moddevgradle.internal.utils.PropertyUtils;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.jetbrains.annotations.ApiStatus;

/**
 * Allows configuration of Parchment mappings for userdev.
 */
@ApiStatus.NonExtendable
public abstract class Parchment {
    @Inject
    public Parchment(Project project) {
        getParchmentArtifact().convention(
                project.getProviders().gradleProperty("neoForge.parchment.parchmentArtifact").orElse(
                        getMinecraftVersion().orElse("")
                                .zip(getMappingsVersion().orElse(""), (minecraftVersion, mappingVersion) -> {
                                    if (!minecraftVersion.isEmpty() && mappingVersion.isEmpty()) {
                                        throw new InvalidUserDataException("If you set neoForge.parchment.minecraftVersion, you also must set mappingVersion");
                                    } else if (minecraftVersion.isEmpty() && !mappingVersion.isEmpty()) {
                                        throw new InvalidUserDataException("If you set neoForge.parchment.mappingVersion, you also must set minecraftVersion");
                                    } else if (minecraftVersion.isEmpty() && mappingVersion.isEmpty()) {
                                        return null;
                                    }
                                    return "org.parchmentmc.data"
                                            + ":" + "parchment-" + minecraftVersion
                                            + ":" + mappingVersion
                                            + "@zip";
                                })));
        getMinecraftVersion().convention(
                project.getProviders().gradleProperty("neoForge.parchment.minecraftVersion"));
        getMappingsVersion().convention(
                project.getProviders().gradleProperty("neoForge.parchment.mappingsVersion"));
        getConflictResolutionPrefix().convention(
                project.getProviders().gradleProperty("neoForge.parchment.conflictResolutionPrefix").orElse("p_"));
        getEnabled().convention(getParchmentArtifact()
                .map(s -> !s.isEmpty()).orElse(PropertyUtils.getBooleanProperty(project, "neoForge.parchment.enabled").orElse(false)));
    }

    /**
     * Artifact coordinates for parchment mappings.
     */
    @Input
    @Optional
    public abstract Property<String> getParchmentArtifact();

    /**
     * Minecraft version of parchment to use. This property is
     * ignored if {@link #getParchmentArtifact()} is set explicitly.
     */
    @Input
    @Optional
    public abstract Property<String> getMinecraftVersion();

    /**
     * Version of default parchment mappings to use.
     * This property is ignored if {@link #getParchmentArtifact()} is set explicitly.
     */
    @Input
    @Optional
    public abstract Property<String> getMappingsVersion();

    /**
     * The string that parameters are prefixed with when they conflict with other names inside the method.
     * Defaults to {@code p_}. You can set this property to an empty string to disable conflict resolution,
     * for example, when you use the checked version of parchment, which already includes prefixes.
     */
    @Input
    @Optional
    public abstract Property<String> getConflictResolutionPrefix();

    /**
     * Enables or disables the system. It is enabled by default if a {@link #getParchmentArtifact()} is specified.
     */
    @Input
    public abstract Property<Boolean> getEnabled();
}
