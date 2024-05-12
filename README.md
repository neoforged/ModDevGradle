# Gradle Plugin for Developing Minecraft Mods on NeoForge

## Features

- Automatically creates and uses a development-friendly logging configuration for the game

## Usage

In `gradle.properties`:

```properties
org.gradle.configuration-cache=true
```

In `settings.gradle`:

```groovy
plugins {
    // This plugin allows Gradle to automatically download arbitrary versions of Java for you
    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
}
```

In `build.gradle`:

```groovy
plugins {
    id 'net.neoforged.neoforgegradle.moddev'
}

repositories {
    mavenLocal()
}

neoForge {
    version = "<version>"
    
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
