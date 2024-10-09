package net.neoforged.minecraftdependencies;

import javax.inject.Inject;

abstract class DistributionDisambiguationRule extends DefaultValueDisambiguationRule<MinecraftDistribution> {
    @Inject
    public DistributionDisambiguationRule(MinecraftDistribution defaultValue) {
        super(defaultValue);
    }
}
