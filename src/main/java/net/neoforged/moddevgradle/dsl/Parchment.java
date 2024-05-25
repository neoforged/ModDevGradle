package net.neoforged.moddevgradle.dsl;

import net.neoforged.moddevgradle.internal.utils.PropertyUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.jetbrains.annotations.ApiStatus;

import javax.inject.Inject;
import java.net.URI;

/**
 * Allows configuration of Parchment mappings for userdev.
 */
@ApiStatus.NonExtendable
public abstract class Parchment {
    @Inject
    public Parchment(Project project) {
        getParchmentArtifact().convention(
                project.getProviders().gradleProperty("neoForge.parchment.parchmentArtifact").orElse(
                        getMinecraftVersion()
                                .zip(getMappingsVersion(), (minecraftVersion, mappingVersion) -> {
                                    return "org.parchmentmc.data"
                                           + ":" + "parchment-" + minecraftVersion
                                           + ":" + mappingVersion
                                           // We need the checked variant for now since it resolves
                                           // parameters conflicting with local variables by prefixing everything with "p"
                                           + ":checked"
                                           + "@zip";
                                })
                )
        );
        getMinecraftVersion().convention(
                project.getProviders().gradleProperty("neoForge.parchment.minecraftVersion")
        );
        getMappingsVersion().convention(
                project.getProviders().gradleProperty("neoForge.parchment.mappingsVersion")
        );
        getAddRepository().convention(
                PropertyUtils.getBooleanProperty(project, "neoForge.parchment.addRepository").orElse(true)
        );
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
     * Mapping version of default parchment to use. This property is
     * ignored if {@link #getParchmentArtifact()} is set explicitly.
     */
    @Input
    @Optional
    public abstract Property<String> getMappingsVersion();

    /**
     * If enabled (the default), the parchment repository will automatically be added to the project,
     * if {@link #getEnabled()} is true.
     */
    @Internal
    public abstract Property<Boolean> getAddRepository();

    /**
     * Enables or disables the system. It is enabled by default if a {@link #getParchmentArtifact()} is specified.
     */
    @Input
    public abstract Property<Boolean> getEnabled();

}
