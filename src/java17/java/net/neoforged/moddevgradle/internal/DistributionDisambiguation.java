package net.neoforged.moddevgradle.internal;

import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.MultipleCandidatesDetails;

public abstract class DistributionDisambiguation implements AttributeDisambiguationRule<String> {
    @Override
    public void execute(MultipleCandidatesDetails<String> details) {
        details.closestMatch("client");
    }
}
