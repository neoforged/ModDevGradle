package net.neoforged.moddevgradle.legacyforge.internal;

import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.MultipleCandidatesDetails;

/**
 * This disambiguation rule will prefer NAMED over SRG when both are present.
 */
class MappingsDisambiguationRule implements AttributeDisambiguationRule<MinecraftMappings> {
    @Override
    public void execute(MultipleCandidatesDetails<MinecraftMappings> details) {
        var consumerValue = details.getConsumerValue();
        if (consumerValue == null) {
            if (details.getCandidateValues().contains(MinecraftMappings.NAMED)) {
                details.closestMatch(MinecraftMappings.NAMED);
            }
        }
    }
}
