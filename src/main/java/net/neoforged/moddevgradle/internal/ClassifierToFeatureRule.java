package net.neoforged.moddevgradle.internal;

import org.gradle.api.Action;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataRule;
import org.gradle.api.artifacts.VariantMetadata;

import javax.inject.Inject;

public abstract class ClassifierToFeatureRule implements ComponentMetadataRule {
    private final String classifier;
    
    @Inject
    public ClassifierToFeatureRule(String classifier) {
        this.classifier = classifier;
    }
    
    @Override
    public void execute(ComponentMetadataContext context) {
        var details = context.getDetails();
        Action<VariantMetadata> createdVariant = variant -> {
            variant.withCapabilities(capabilities -> {
                for (var cap : capabilities.getCapabilities()) {
                    capabilities.removeCapability(cap.getGroup(), cap.getName());
                }
                capabilities.addCapability(details.getId().getGroup(), details.getId().getName() + "-" + classifier, details.getId().getVersion());
            });
            variant.withFiles(files -> {
                files.removeAllFiles();
                files.addFile(details.getId().getName() + "-" + details.getId().getVersion() + "-" + classifier + ".jar");
            });
        };
        // Which of these exists depends on whether the module in question publishes Gradle module metadata or just a
        // maven pom. `maybeAddVariant` is lenient.
        details.maybeAddVariant(classifier+"Runtime", "runtime", createdVariant);
        details.maybeAddVariant(classifier+"RuntimeElements", "runtimeElements", createdVariant);
        details.maybeAddVariant(classifier+"Compile", "compile", createdVariant);
        details.maybeAddVariant(classifier+"ApiElements", "apiElements", createdVariant);
    }
}
