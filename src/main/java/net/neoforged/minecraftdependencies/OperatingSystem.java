package net.neoforged.minecraftdependencies;

import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;

/**
 * This attribute is used to differentiate between the different native libraries used by Minecraft.
 * <p>
 * The client in particular uses a rule-based system to declare dependencies that only apply to certain
 * operating systems. We model libraries that are declared using such rules by using this attribute.
 *
 * @see <a href="https://github.com/neoforged/GradleMinecraftDependencies/blob/999449cc54c5c01fff1a66406be6e72872b75979/buildSrc/src/main/groovy/net/neoforged/minecraftdependencies/GenerateModuleMetadata.groovy#L140">GradleMinecraftDependencies</a>
 */
public interface OperatingSystem extends Named {
    Attribute<OperatingSystem> ATTRIBUTE = Attribute.of("net.neoforged.operatingsystem", OperatingSystem.class);

    String LINUX = "linux";
    String MACOSX = "osx";
    String WINDOWS = "windows";
}
