package net.neoforged.moddevgradle.dsl;

import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import net.neoforged.moddevgradle.internal.ClassifierToFeatureRule;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.jetbrains.annotations.Nullable;

public abstract class DependencyTools {
    private final ComponentMetadataHandler componentMetadataHandler;
    private final Set<ExistingRuleParams> existing = new HashSet<>();

    record ExistingRuleParams(String group, String module, String classifier) {}

    @Inject
    public DependencyTools(ComponentMetadataHandler componentMetadataHandler) {
        this.componentMetadataHandler = componentMetadataHandler;
    }

    @Inject
    protected abstract DependencyFactory getDependencyFactory();

    /**
     * Creates a rule that modifies the metadata of the provided dependency during resolution to create a feature variant
     * with the same artifact as the provided classifier would target. This feature will have the same dependencies as
     * the main feature of the module.
     * 
     * @param group      the group of the module to transform
     * @param module     the name of the module to transform
     * @param version    the version of the dependency to be created
     * @param classifier the classifier to wrap into a feature
     * @return a dependency on the generated feature of the module
     */
    public ExternalModuleDependency mapClassifierToFeature(String group, String module, @Nullable String version, String classifier) {
        makeRule(group, module, classifier);
        var dep = getDependencyFactory().create(group, module, version);
        dep.capabilities(caps -> caps.requireCapability(group + ":" + module + "-" + classifier));
        return dep;
    }

    /**
     * Creates a rule that modifies the metadata of the provided dependency during resolution to create a feature variant
     * with the same artifact as the provided classifier would target. This feature will have the same dependencies as
     * the main feature of the module.
     * 
     * @param notation   the module to transform
     * @param classifier the classifier to wrap into a feature
     * @return a dependency on the generated feature of the module
     */
    public ExternalModuleDependency mapClassifierToFeature(CharSequence notation, String classifier) {
        var dummyDep = getDependencyFactory().create(notation);
        var group = dummyDep.getGroup();
        var module = dummyDep.getName();
        var version = dummyDep.getVersion();
        makeRule(group, module, classifier);
        return mapClassifierToFeature(group, module, version, classifier);
    }

    private void makeRule(String group, String module, String classifier) {
        if (existing.add(new ExistingRuleParams(group, module, classifier))) {
            componentMetadataHandler.withModule(group + ":" + module, ClassifierToFeatureRule.class, config -> config.params(classifier));
        }
    }
}
