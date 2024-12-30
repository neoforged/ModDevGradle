# ModDevGradle Legacy Forge Plugin
ModDevGradle has a secondary plugin (ID: `net.neoforged.moddev.legacyforge`, released alongside the normal plugin with the same version)
that adds support for developing mods against MinecraftForge and Vanilla Minecraft versions 1.17 up to 1.20.1.  

The legacy plugin is an "addon" plugin, meaning it operates on top of the normal plugin. This means that the APIs normally used
are also available when using the legacy plugin.

## Basic Usage for MinecraftForge Mods
An example `build.gradle` file for developing a mod against MinecraftForge for 1.20.1 is provided below:
```groovy
plugins {
    // Apply the plugin. You can find the latest version at https://projects.neoforged.net/neoforged/ModDevGradle
    id 'net.neoforged.moddev.legacyforge' version '2.0.28-beta'
}

legacyForge {
    // Develop against MinecraftForge version 47.3.0 for 1.20.1 (the versions can be found at https://files.minecraftforge.net/)
    version = "1.20.1-47.3.0"
    
    // Validate AT files and raise errors when they have invalid targets
    // This option is false by default, but turning it on is recommended
    validateAccessTransformers = true

    runs {
        client {
            client()
        }
        data {
            data()
        }
        server {
            server()
        }
    }

    mods {
        testproject {
            sourceSet sourceSets.main
        }
    }
}
```

## Reobfuscating artifacts
Forge used SRG mappings as intermediary mappings in 1.20.1 and below. While your mod is developed against the mappings provided
by Mojang (known as official mappings), you need to reobfuscate it to SRG mappings for it to work in production.  
Reobfuscation will automatically be configured for the `jar` task; the non-obfuscated jar will be moved to `build/devlibs`
and will not be published in favour of the reobfuscated variant. You should upload the `reobfJar` task's output when using a
task to upload to a mod hosting platform.

You may reobfuscate other jar tasks using `obfuscation.reobfuscate(TaskProvider<AbstractArchiveTask>, SourceSet, Action<RemapJarTask>)`.

For instance, if you want to reobfuscate a `shadowJar` task:
```groovy
shadowJar {
    archiveClassifier = 'all'
}

obfuscation {
    // Reobfuscate the shadowJar task, using the classpath of the main sourceset for properly remapping inherited members
    // This will place the original shadow jar in build/devlibs while putting this reobfuscated version in build/libs
    reobfuscate(tasks.named('shadowJar'), sourceSets.main)
}
```

When reobfuscating a jar, it will be replaced in publications with the obfuscated version to avoid publishing jars that aren't mapped to SRG.

## Remapping Mod Dependencies
As published mods are using intermediary mappings, you must remap them to official mappings before being able to use them as a dependencies.  
ModDevGradle creates configurations that will automatically remap dependencies added to them from SRG mappings to official mappings.

**IMPORTANT:** These configurations are not transitive, you will have to manually add transitive dependencies of the mods you are adding.

The following configurations are created automatically and are children of the configurations without the `mod` prefix:
- `modImplementation`
- `modRuntimeOnly`
- `modCompileOnly`
- `modApi` (only if the `java-library` plugin is applied)
- `modCompileOnlyApi` (only if the `java-library` plugin is applied)

You may create your own remapping configurations using `obfuscation.createRemappingConfiguration(Configuration)`:
```groovy
configurations {
    // Create a custom configuration named "custom"
    custom
}

obfuscation {
    // Create a configuration named "modCustom" that remaps its dependencies and then adds them to the "custom" configuration
    createRemappingConfiguration(configurations.custom)
}
```

## Vanilla Mode

You can get dependencies for Vanilla Minecraft added to your project by using the `mcpVersion` property instead of
setting the `version` property.

```groovy
legacyForge {
    // This adds Minecraft 1.20.1 as a dependency to the main source set.
    mcpVersion = "1.20.1"
}
```

## Mixins

You need to create so-called "refmaps" for Mixin, which convert the names you used to declare injection points and reference other parts of Minecraft code to the names used at runtime (SRG).

This is usually done by including the Mixin annotation processor in your build:

```groovy
dependencies {
    annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'
    // If you have additional source sets that contain Mixins, you also need to apply the AP to those
    // For example if you have a "client" source set:
    clientAnnotationProcessor 'org.spongepowered:mixin:0.8.5:processor'
}
```

You need to let the AP know about your Mixin configuration files, and how you'd like your refmap to be named for each 
of the source sets that contain mixins:

```groovy
mixin {
    add sourceSets.main, 'mixins.mymod.refmap.json'
    config 'mixins.mymod.json' // This can be done for multiple configs
}
```

Please note, you also have to add the `MixinConfigs` attribute to your Jar manifest for your Mixins to load in production. Such as this way:

```groovy
jar {
    manifest.attributes([
        "MixinConfigs": "mixins.mymod.json"
    ])
}
```

## Effects of enabling legacy forge modding

Enabling modding in the legacyForge extension triggers the creation of various intermediary (SRG) to named (official) mapping files used by various parts of the toolchain, such as
mod reobfuscation and runtime naming services.

Reobfuscation to the intermediary mappings will automatically be configured for the `jar` task, the non-obfuscated jar will have a `-dev` classifier
and will not be published in favour of the reobfuscated variant.
