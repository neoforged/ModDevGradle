package net.neoforged.minecraftdependencies;

import javax.inject.Inject;

abstract class OperatingSystemDisambiguationRule extends DefaultValueDisambiguationRule<OperatingSystem> {
    @Inject
    public OperatingSystemDisambiguationRule(OperatingSystem defaultValue) {
        super(defaultValue);
    }
}
