package net.neoforged.moddevgradle.legacyforge.internal;

import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.MultipleCandidatesDetails;

import javax.inject.Inject;

/**
 * This disambiguation rule will prefer NAMED over SRG when both are present.
 */
class MappingsDisambiguationRule implements AttributeDisambiguationRule<MinecraftMappings> {
    private final MinecraftMappings named;

    @Inject
    MappingsDisambiguationRule(MinecraftMappings named) {
        this.named = named;
    }

    @Override
    public void execute(MultipleCandidatesDetails<MinecraftMappings> details) {
        var consumerValue = details.getConsumerValue();
        if (consumerValue == null) {
            if (details.getCandidateValues().contains(named)) {
                details.closestMatch(named);
            }
        }
    }
}
