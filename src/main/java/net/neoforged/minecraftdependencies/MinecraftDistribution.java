package net.neoforged.minecraftdependencies;

import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;

/**
 * The source of this attribute is the list of dependencies declared by the server and client Minecraft distributions.
 *
 * @see <a href="https://github.com/neoforged/GradleMinecraftDependencies/blob/999449cc54c5c01fff1a66406be6e72872b75979/buildSrc/src/main/groovy/net/neoforged/minecraftdependencies/GenerateModuleMetadata.groovy#L83">GradleMinecraftDependencies project</a>
 */
public interface MinecraftDistribution extends Named {
    Attribute<MinecraftDistribution> ATTRIBUTE = Attribute.of("net.neoforged.distribution", MinecraftDistribution.class);

    String CLIENT = "client";
    String SERVER = "server";
}
