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

neoForge {
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
Reobfuscation will automatically be configured for the `jar` task; the non-obfuscated jar will have a `-dev` classifier
and will not be published in favour of the reobfuscated variant. You should upload the `reobfJar` task's output when using a
task to upload to a mod hosting platform, or otherwise the jar without a `-dev` classifier if you're uploading it manually.  

You may reobfuscate other jar tasks using `obfuscation.reobfuscate(TaskProvider<AbstractArchiveTask>, SourceSet, Action<RemapJarTask>)`.  
For instance, if you want to reobfuscate a `shadowJar` task:
```groovy
shadowJar {
    // Change the classifier of the shadow jar to be -dev-all as it's not mapped in intermediary and not usable for production
    archiveClassifier = 'dev-all'
}

obfuscation {
    // Reobfuscate the shadowJar task, using the classpath of the main sourceset for properly remapping inherited members
    reobfuscate(tasks.named('shadowJar'), sourceSets.main) {
        // Make the reobfuscated shadowJar have the all classifier
        // You could also change it to an empty string if you want it to not have a classifier (in that case, you will also need to change the classifier of the slim `reobfJar` task
        archiveClassifier = 'all'
    }
}
```

When reobfuscating a jar, it will be replaced in publications with the obfuscated version to avoid publishing jars that aren't mapped to SRG.

## Remapping mod dependencies
As published mods are using intermediary mappings, you must remap them to official mappings before being able to use them as a dependencies.  
ModDevGradle creates configurations that will automatically remap dependencies added to them from SRG mappings to official mappings.  
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

## Effects of applying the legacy plugin
When applied, the legacy plugin will change the base NeoForm and NeoForge artifact coordinates of the `neoForge` extension to
`de.oceanlabs.mcp:mcp_config` and `net.minecraftforge:forge`.  
It will also trigger the creation of various intermediary (SRG) to named (official) mapping files used by various parts of the toolchain, such as
mod reobfuscation and runtime naming services.  
Reobfuscation to the intermediary mappings will automatically be configured for the `jar` task, the non-obfuscated jar will have a `-dev` classifier
and will not be published in favour of the reobfuscated variant.
