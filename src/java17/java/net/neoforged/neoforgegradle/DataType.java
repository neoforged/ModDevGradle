package net.neoforged.neoforgegradle;

import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;

// TODO: this is probably going away and replaced by capabilities
public interface DataType extends Named {
    Attribute<DataType> DATA_TYPE_ATTRIBUTE = Attribute.of("net.neoforged.datatype", DataType.class);
    String NEOFORGE_MODDEV_BUNDLE = "neoforge-moddev-bundle";
    String NEOFORGE_MODDEV_CONFIG = "neoforge-moddev-config";
    String NEOFORGE_MODDED_LIBRARIES = "neoforge-moddev-libraries";
}
