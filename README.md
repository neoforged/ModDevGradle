# Gradle Plugin for Developing Minecraft Mods on NeoForge

## Features

- Creates the necessary artifacts to compile Minecraft mods for [NeoForge](https://neoforged.net/)
- Runs the game from Gradle or IntelliJ for debugging and testing
- Automatically creates and uses a development-friendly logging configuration for the testing the mod
- Supports the [Gradle configuration cache](https://docs.gradle.org/current/userguide/configuration_cache.html) to speed
  up repeated runs of Gradle tasks

## Basic Usage

In `gradle.properties`:

```properties
# Enable Gradle configuration cache:
org.gradle.configuration-cache=true
```

In `settings.gradle`:

```groovy
pluginManagement {
    repositories {
        // Add the usual NeoForged maven repository.
        maven { url = 'https://maven.neoforged.net/releases' }
        // Add the maven repository for the ModDevGradle plugin.
        maven {
            name 'Maven for PR #1' // https://github.com/neoforged/ModDevGradle/pull/1
            url 'https://prmaven.neoforged.net/ModDevGradle/pr1'
            content {
                includeModule('net.neoforged.moddev', 'net.neoforged.moddev.gradle.plugin')
                includeModule('net.neoforged.moddev.junit', 'net.neoforged.moddev.junit.gradle.plugin')
                includeModule('net.neoforged', 'moddev-gradle')
            }
        }
    }
}

plugins {
    // This plugin allows Gradle to automatically download arbitrary versions of Java for you
    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
}
```

In `build.gradle`:

```groovy
plugins {
    // Apply the plugin. You can find the latest version at https://github.com/neoforged/ModDevGradle/packages/2159800.
    id 'net.neoforged.moddev' version '0.1.24-pr-1-pr-publish'
}

neoForge {
    // For now we require a special NeoForge build. You can find the latest version at https://github.com/neoforged/NeoForge/pull/959. 
    version = "20.6.84-beta-pr-959-features-gradle-metadata"

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

See the example code in [the test project](./testproject/build.gradle).

## More Configuration

### Runs

Any number of runs can be added in the `neoForge { runs { ... } }` block.

Every run must have a type. Currently, the supported types are `client`, `data`, `gameTestServer`, `server`.
The run type can be set as follows:

```groovy
neoForge {
    runs {
        < run name > {
            // This is the standard syntax:
            type = "gameTestServer"
            // Client, data and server runs can use a shorthand instead:
            // client()
            // data()
            // server()
        
            // Optionally set the log-level used by the game
            logLevel = org.slf4j.event.Level.INFO
        }
    }
}
```

Please have a look at [RunModel.java](src/java17/java/net/neoforged/moddevgradle/dsl/RunModel.java) for the list of
supported properties.
Here is an example that sets a system property to change the log level to debug:

```groovy
neoForge {
    runs {
        configureEach {
            systemProperty 'forge.logging.console.level', 'debug'
        }
    }
}
```

### Better Minecraft Parameter Names / Javadoc (Parchment)

You can use community-sourced parameter-names and Javadoc for Minecraft source code
from [ParchmentMC](https://parchmentmc.org/docs/getting-started).

The easiest way is setting the Parchment version in your gradle.properties:

```properties
neoForge.parchment.minecraftVersion=1.20.6
neoForge.parchment.mappingsVersion=2024.05.01
```

Alternatively, you can set it in your build.gradle:

```groovy
neoForge {
  // [...]
  
  parchment {
    // Get versions from https://parchmentmc.org/docs/getting-started
    // Omit the "v"-prefix in mappingsVersion
    minecraftVersion = "1.20.6"
    mappingsVersion = "2024.05.01"
  }
}
```

## Advanced Tips & Tricks

### Overriding Platform Libraries

For testing during the development of NeoForge and its various platform libraries, it can be useful to globally
override the version to an unreleased one. This works:

```groovy
configurations.all {
    resolutionStrategy {
        force 'cpw.mods:securejarhandler:2.1.43'
    }
}
```
