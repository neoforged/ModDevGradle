package net.neoforged.moddevgradle.internal;

import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.MultipleCandidatesDetails;

/**
 * We generally will use "client" dependencies when we have to decide between client and server,
 * since client libraries will usually be a superset of the server libraries.
 */
public abstract class DistributionDisambiguation implements AttributeDisambiguationRule<String> {
    @Override
    public void execute(MultipleCandidatesDetails<String> details) {
        details.closestMatch("client");
    }
}
