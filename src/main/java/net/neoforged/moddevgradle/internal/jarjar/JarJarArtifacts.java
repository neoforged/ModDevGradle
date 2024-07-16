package net.neoforged.moddevgradle.internal.jarjar;

import net.neoforged.jarjar.metadata.ContainedJarIdentifier;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.Restriction;
import org.apache.maven.artifact.versioning.VersionRange;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.VersionConstraint;
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
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
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
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class JarJarArtifacts {
    private transient final SetProperty<ResolvedComponentResult> includedRootComponents;
    private transient final SetProperty<ResolvedArtifactResult> includedArtifacts;


    @Internal
    protected final SetProperty<ResolvedComponentResult> getIncludedRootComponents() {
        return includedRootComponents;
    }

    @Internal
    protected final SetProperty<ResolvedArtifactResult> getIncludedArtifacts() {
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

    public final void configuration(final Configuration jarJarConfiguration) {
        getIncludedArtifacts().addAll(jarJarConfiguration.getIncoming().artifactView(new Action<ArtifactView.ViewConfiguration>() {
            @Override
            public void execute(ArtifactView.ViewConfiguration config) {
                config.attributes(
                        new Action<AttributeContainer>() {
                            @Override
                            public void execute(AttributeContainer attr) {
                                attr.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
                            }
                        }
                );
            }
        }).getArtifacts().getResolvedArtifacts());
        getIncludedRootComponents().add(jarJarConfiguration.getIncoming().getResolutionResult().getRootComponent());
    }

    public final void setConfigurations(final Collection<? extends Configuration> configurations) {
        includedRootComponents.empty();
        includedArtifacts.empty();
        for (final Configuration configuration : configurations) {
            configuration(configuration);
        }
    }

    private static List<ResolvedJarJarArtifact> getIncludedJars(final Set<ResolvedComponentResult> rootComponents, final Set<ResolvedArtifactResult> artifacts) {
        final Map<ContainedJarIdentifier, String> versions = new HashMap<>();
        final Map<ContainedJarIdentifier, String> versionRanges = new HashMap<>();
        final Set<ContainedJarIdentifier> knownIdentifiers = new HashSet<>();

        for (final ResolvedComponentResult rootComponent : rootComponents) {
            collectFromComponent(rootComponent, knownIdentifiers, versions, versionRanges);
        }
        final ArrayList<ResolvedJarJarArtifact> data = new ArrayList<ResolvedJarJarArtifact>();
        final HashSet<String> filesAdded = new HashSet<String>();
        for (final ResolvedArtifactResult result : artifacts) {
            final ResolvedVariantResult variant = result.getVariant();

            final ArtifactIdentifier artifactIdentifier = capabilityOrModule(variant);
            if (artifactIdentifier == null) {
                continue;
            }

            final ContainedJarIdentifier jarIdentifier = new ContainedJarIdentifier(artifactIdentifier.group(), artifactIdentifier.name());
            if (!knownIdentifiers.contains(jarIdentifier)) {
                continue;
            }

            final String version = versions.getOrDefault(jarIdentifier, getVersionFrom(variant));
            final String versionRange = versionRanges.getOrDefault(jarIdentifier, makeOpenRange(variant));

            if (version != null && versionRange != null) {
                final String embeddedFilename = getEmbeddedFilename(result, jarIdentifier);

                final ResolvedJarJarArtifact dataEntry = new ResolvedJarJarArtifact(result.getFile(), embeddedFilename, version, versionRange, jarIdentifier.group(), jarIdentifier.artifact());
                if (!filesAdded.add(embeddedFilename)) {
                    throw new GradleException("Trying to add multiple files at the same embedded location: " + embeddedFilename);
                }
                data.add(dataEntry);
            }
        }
        return data.stream()
                .sorted(Comparator.comparing(new Function<ResolvedJarJarArtifact, String>() {
                    @Override
                    public String apply(ResolvedJarJarArtifact d) {
                        return d.getGroup() + ":" + d.getArtifact();
                    }
                }))
                .collect(Collectors.toList());
    }

    private static String getEmbeddedFilename(final ResolvedArtifactResult result, final ContainedJarIdentifier jarIdentifier) {
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

    private static void collectFromComponent(final ResolvedComponentResult rootComponent, final Set<ContainedJarIdentifier> knownIdentifiers, final Map<ContainedJarIdentifier, String> versions, final Map<ContainedJarIdentifier, String> versionRanges) {
        for (final DependencyResult result : rootComponent.getDependencies()) {
            if (!(result instanceof final ResolvedDependencyResult resolvedResult)) {
                continue;
            }
            final ComponentSelector requested = resolvedResult.getRequested();
            final ResolvedVariantResult variant = resolvedResult.getResolvedVariant();

            final ArtifactIdentifier artifactIdentifier = capabilityOrModule(variant);
            if (artifactIdentifier == null) {
                continue;
            }

            final ContainedJarIdentifier jarIdentifier = new ContainedJarIdentifier(artifactIdentifier.group(), artifactIdentifier.name());
            knownIdentifiers.add(jarIdentifier);

            String versionRange = null;
            if (requested instanceof final ModuleComponentSelector requestedModule) {
                final String rawVersionRange = getModuleVersionRange(requestedModule);
                final String errorPrefix = "Unsupported version constraint '" + rawVersionRange + "' on Jar-in-Jar dependency " + requestedModule.getModuleIdentifier() + ": ";

                final VersionRange data;
                try {
                    data = VersionRange.createFromVersionSpec(rawVersionRange);
                } catch (final InvalidVersionSpecificationException e) {
                    throw new GradleException(errorPrefix + e.getMessage());
                }

                if (isDynamicVersionRange(data)) {
                    throw new GradleException(errorPrefix + "dynamic versions are unsupported");
                } else if (data.hasRestrictions()) {
                    versionRange = rawVersionRange;
                } else {
                    // Single version is requested -> make an open range with the result of the resolution,
                    // instead of the version that the user specified.
                    versionRange = makeOpenRange(variant);
                }
            }

            // If no range was specified, or this is a project-dependency, use a loose version range
            if (versionRange == null) {
                versionRange = makeOpenRange(variant);
            }

            final String version = getVersionFrom(variant);

            if (version != null) {
                versions.put(jarIdentifier, version);
            }
            if (versionRange != null) {
                versionRanges.put(jarIdentifier, versionRange);
            }
        }
    }

    private static String getModuleVersionRange(final ModuleComponentSelector requestedModule) {
        final VersionConstraint constraint = requestedModule.getVersionConstraint();
        if (!constraint.getStrictVersion().isEmpty()) {
            return constraint.getStrictVersion();
        } else if (!constraint.getRequiredVersion().isEmpty()) {
            return constraint.getRequiredVersion();
        } else if (!constraint.getPreferredVersion().isEmpty()) {
            return constraint.getPreferredVersion();
        } else {
            return requestedModule.getVersion();
        }
    }

    private static @Nullable ArtifactIdentifier capabilityOrModule(final ResolvedVariantResult variant) {
        ArtifactIdentifier moduleIdentifier = null;
        if (variant.getOwner() instanceof final ModuleComponentIdentifier moduleComponentIdentifier) {
            moduleIdentifier = new ArtifactIdentifier(
                    moduleComponentIdentifier.getGroup(),
                    moduleComponentIdentifier.getModule(),
                    moduleComponentIdentifier.getVersion()
            );
        }

        final List<ArtifactIdentifier> capabilityIdentifiers = variant.getCapabilities().stream()
                .map(new Function<Capability, ArtifactIdentifier>() {
                    @Override
                    public ArtifactIdentifier apply(Capability capability) {
                        return new ArtifactIdentifier(
                                capability.getGroup(),
                                capability.getName(),
                                capability.getVersion()
                        );
                    }
                })
                .toList();

        if (moduleIdentifier != null && capabilityIdentifiers.contains(moduleIdentifier)) {
            return moduleIdentifier;
        } else if (capabilityIdentifiers.isEmpty()) {
            return null;
        }
        return capabilityIdentifiers.get(0);
    }

    private static @Nullable String moduleOrCapabilityVersion(final ResolvedVariantResult variant) {
        @Nullable final ArtifactIdentifier identifier = capabilityOrModule(variant);
        if (identifier != null) {
            return identifier.version();
        }
        return null;
    }

    private static @Nullable String makeOpenRange(final ResolvedVariantResult variant) {
        final String baseVersion = moduleOrCapabilityVersion(variant);

        if (baseVersion == null) {
            return null;
        }

        return "[" + baseVersion + ",)";
    }

    private static @Nullable String getVersionFrom(final ResolvedVariantResult variant) {
        return moduleOrCapabilityVersion(variant);
    }

    private static boolean isDynamicVersionRange(final VersionRange data) {
        for (final Restriction restriction : data.getRestrictions()) {
            if (isDynamicVersion(restriction.getLowerBound()) || isDynamicVersion(restriction.getUpperBound())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDynamicVersion(@Nullable final ArtifactVersion version) {
        if (version == null) {
            return false;
        }
        return version.toString().endsWith("+") || version.toString().startsWith("latest.");
    }

    /**
     * Simple artifact identifier class which only references group, name and version.
     */
    private record ArtifactIdentifier(String group, String name, String version) {
    }
}
