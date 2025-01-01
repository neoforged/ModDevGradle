package net.neoforged.minecraftdependencies;

import javax.inject.Inject;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.MultipleCandidatesDetails;

/**
 * Sets a default value for an attribute if no value is requested.
 */
abstract class DefaultValueDisambiguationRule<T> implements AttributeDisambiguationRule<T> {
    private final T defaultValue;

    @Inject
    public DefaultValueDisambiguationRule(T defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public void execute(MultipleCandidatesDetails<T> details) {
        var consumerValue = details.getConsumerValue();
        if (consumerValue != null && details.getCandidateValues().contains(consumerValue)) {
            details.closestMatch(consumerValue);
        } else {
            for (var candidateValue : details.getCandidateValues()) {
                if (candidateValue.equals(defaultValue)) {
                    details.closestMatch(candidateValue);
                    return;
                }
            }
        }
    }
}
