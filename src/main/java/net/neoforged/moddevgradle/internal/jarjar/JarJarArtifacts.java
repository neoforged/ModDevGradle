package net.neoforged.moddevgradle.internal.jarjar;

import net.neoforged.jarjar.metadata.ContainedJarIdentifier;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class JarJarArtifacts {
    private transient final SetProperty<ResolvedComponentResult> includedRootComponents;
    private transient final SetProperty<ResolvedArtifactResult> includedArtifacts;


    @Internal
    protected SetProperty<ResolvedComponentResult> getIncludedRootComponents() {
        return includedRootComponents;
    }

    @Internal
    protected SetProperty<ResolvedArtifactResult> getIncludedArtifacts() {
        return includedArtifacts;
    }

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Nested
    public abstract ListProperty<ResolvedJarJarArtifact> getResolvedArtifacts();

    public JarJarArtifacts() {
        includedRootComponents = getObjectFactory().setProperty(ResolvedComponentResult.class);
        includedArtifacts = getObjectFactory().setProperty(ResolvedArtifactResult.class);

        includedArtifacts.finalizeValueOnRead();
        includedRootComponents.finalizeValueOnRead();

        getResolvedArtifacts().set(getIncludedRootComponents().zip(getIncludedArtifacts(), JarJarArtifacts::getIncludedJars));
    }

    public void configuration(Configuration jarJarConfiguration) {
        getIncludedArtifacts().addAll(jarJarConfiguration.getIncoming().artifactView(config -> {
            config.attributes(
                    attr -> attr.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
            );
        }).getArtifacts().getResolvedArtifacts());
        getIncludedRootComponents().add(jarJarConfiguration.getIncoming().getResolutionResult().getRootComponent());
    }

    public void setConfigurations(Collection<? extends Configuration> configurations) {
        includedRootComponents.empty();
        includedArtifacts.empty();
        for (Configuration configuration : configurations) {
            configuration(configuration);
        }
    }

    private static List<ResolvedJarJarArtifact> getIncludedJars(Set<ResolvedComponentResult> rootComponents, Set<ResolvedArtifactResult> artifacts) {
        Map<ContainedJarIdentifier, String> versions = new HashMap<>();
        Map<ContainedJarIdentifier, String> versionRanges = new HashMap<>();
        Set<ContainedJarIdentifier> knownIdentifiers = new HashSet<>();

        for (ResolvedComponentResult rootComponent : rootComponents) {
            collectFromComponent(rootComponent, knownIdentifiers, versions, versionRanges);
        }
        List<ResolvedJarJarArtifact> data = new ArrayList<>();
        for (ResolvedArtifactResult result : artifacts) {
            ResolvedVariantResult variant = result.getVariant();

            ArtifactIdentifier artifactIdentifier = capabilityOrModule(variant);
            if (artifactIdentifier == null) {
                continue;
            }

            ContainedJarIdentifier jarIdentifier = new ContainedJarIdentifier(artifactIdentifier.group(), artifactIdentifier.name());
            if (!knownIdentifiers.contains(jarIdentifier)) {
                continue;
            }

            String version = versions.getOrDefault(jarIdentifier, getVersionFrom(variant));
            String versionRange = versionRanges.getOrDefault(jarIdentifier, makeOpenRange(variant));

            if (version != null && versionRange != null) {
                var embeddedFilename = getEmbeddedFilename(result, jarIdentifier);

                data.add(new ResolvedJarJarArtifact(result.getFile(), embeddedFilename, version, versionRange, jarIdentifier.group(), jarIdentifier.artifact()));
            }
        }
        return data.stream()
                .sorted(Comparator.comparing(d -> d.getGroup() + ":" + d.getArtifact()))
                .collect(Collectors.toList());
    }

    private static String getEmbeddedFilename(ResolvedArtifactResult result, ContainedJarIdentifier jarIdentifier) {
        // When we include subprojects, we add the group to the front of the filename in an attempt to avoid
        // ambiguous module-names between jar-files embedded by different mods.
        // Example: two mods call their submodule "coremod" and end up with a "coremod.jar", and neither set
        // an Automatic-Module-Name.
        String embeddedFilename = result.getFile().getName();
        if (result.getId().getComponentIdentifier() instanceof ProjectComponentIdentifier) {
            embeddedFilename = jarIdentifier.group() + "." + result.getFile().getName();
        }
        return embeddedFilename;
    }

    private static void collectFromComponent(ResolvedComponentResult rootComponent, Set<ContainedJarIdentifier> knownIdentifiers, Map<ContainedJarIdentifier, String> versions, Map<ContainedJarIdentifier, String> versionRanges) {
        for (DependencyResult result : rootComponent.getDependencies()) {
            if (!(result instanceof ResolvedDependencyResult resolvedResult)) {
                continue;
            }
            ComponentSelector requested = resolvedResult.getRequested();
            ResolvedVariantResult variant = resolvedResult.getResolvedVariant();

            ArtifactIdentifier artifactIdentifier = capabilityOrModule(variant);
            if (artifactIdentifier == null) {
                continue;
            }

            ContainedJarIdentifier jarIdentifier = new ContainedJarIdentifier(artifactIdentifier.group(), artifactIdentifier.name());
            knownIdentifiers.add(jarIdentifier);

            String versionRange = null;
            if (requested instanceof ModuleComponentSelector requestedModule) {
                var constraint = requestedModule.getVersionConstraint();
                if (!constraint.getStrictVersion().isEmpty()) {
                    versionRange = validateVersionRange(constraint.getStrictVersion(), requestedModule);
                } else if (!constraint.getRequiredVersion().isEmpty()) {
                    versionRange = validateVersionRange(constraint.getRequiredVersion(), requestedModule);
                } else if (!constraint.getPreferredVersion().isEmpty()) {
                    versionRange = validateVersionRange(constraint.getPreferredVersion(), requestedModule);
                } else {
                    versionRange = validateVersionRange(requestedModule.getVersion(), requestedModule);
                }
            }

            // If no range was specified, or this is a project-dependency, use a loose version range
            if (versionRange == null) {
                versionRange = makeOpenRange(variant);
            }

            String version = getVersionFrom(variant);

            if (version != null) {
                versions.put(jarIdentifier, version);
            }
            if (versionRange != null) {
                versionRanges.put(jarIdentifier, versionRange);
            }
        }
    }

    private static @Nullable ArtifactIdentifier capabilityOrModule(final ResolvedVariantResult variant) {
        ArtifactIdentifier moduleIdentifier = null;
        if (variant.getOwner() instanceof ModuleComponentIdentifier moduleComponentIdentifier) {
            moduleIdentifier = new ArtifactIdentifier(
                    moduleComponentIdentifier.getGroup(),
                    moduleComponentIdentifier.getModule(),
                    moduleComponentIdentifier.getVersion()
            );
        }

        List<ArtifactIdentifier> capabilityIdentifiers = variant.getCapabilities().stream()
                .map(capability -> new ArtifactIdentifier(
                        capability.getGroup(),
                        capability.getName(),
                        capability.getVersion()
                ))
                .toList();

        if (moduleIdentifier != null && capabilityIdentifiers.contains(moduleIdentifier)) {
            return moduleIdentifier;
        } else if (capabilityIdentifiers.isEmpty()) {
            return null;
        }
        return capabilityIdentifiers.get(0);
    }

    private static @Nullable String moduleOrCapabilityVersion(final ResolvedVariantResult variant) {
        @Nullable ArtifactIdentifier identifier = capabilityOrModule(variant);
        if (identifier != null) {
            return identifier.version();
        }
        return null;
    }

    private static @Nullable String makeOpenRange(final ResolvedVariantResult variant) {
        String baseVersion = moduleOrCapabilityVersion(variant);

        if (baseVersion == null) {
            return null;
        }

        return "[" + baseVersion + ",)";
    }

    private static @Nullable String getVersionFrom(final ResolvedVariantResult variant) {
        return moduleOrCapabilityVersion(variant);
    }

    private static String validateVersionRange(String range, ModuleComponentSelector module) {
        var errorPrefix = "Unsupported version constraint '" + range + "' on Jar-in-Jar dependency " + module.getModuleIdentifier() + ": ";

        VersionRange data;
        try {
            data = VersionRange.createFromVersionSpec(range);
        } catch (InvalidVersionSpecificationException e) {
            throw new GradleException(errorPrefix + e.getMessage());
        }

        if (!data.hasRestrictions()) {
            throw new GradleException(errorPrefix + "no restrictions");
        } else if (data.getRecommendedVersion() != null) {
            throw new GradleException(errorPrefix + "recommended versions are unsupported");
        } else if (range.endsWith("+") || range.startsWith("latest.")) {
            throw new GradleException(errorPrefix + "dynamic versions are unsupported");
        }

        return range;
    }

    /**
     * Simple artifact identifier class which only references group, name and version.
     */
    private record ArtifactIdentifier(String group, String name, String version) {
    }
}
