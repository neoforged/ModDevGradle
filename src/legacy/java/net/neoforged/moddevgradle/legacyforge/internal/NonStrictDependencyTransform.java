package net.neoforged.moddevgradle.legacyforge.internal;

import org.gradle.api.artifacts.CacheableRule;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataRule;

/**
 * Relaxes all strict version constraints to just be required.
 */
@CacheableRule
public class NonStrictDependencyTransform implements ComponentMetadataRule {
    @Override
    public void execute(ComponentMetadataContext context) {
        context.getDetails().allVariants(variant -> {
            variant.withDependencies(dependencies -> {
                for (var dependency : dependencies) {
                    dependency.version(version -> {
                        version.prefer(version.getStrictVersion());
                        version.require(version.getStrictVersion());
                        version.strictly("");
                    });
                }
            });
        });
    }
}
