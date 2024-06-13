# Gradle Plugin for Developing Minecraft Mods on NeoForge

## Features

- Creates the necessary artifacts to compile Minecraft mods for [NeoForge](https://neoforged.net/)
- Runs the game from Gradle or IntelliJ for debugging and testing
- Automatically creates and uses a development-friendly logging configuration for the testing the mod
- Supports the [Gradle configuration cache](https://docs.gradle.org/current/userguide/configuration_cache.html) to speed
  up repeated runs of Gradle tasks

## Basic Usage for NeoForge Mods

In `gradle.properties`:

```properties
# Enable Gradle configuration cache if you'd like:
org.gradle.configuration-cache=true
```

In `settings.gradle`:

```groovy
pluginManagement {
    repositories {
        // Add the usual NeoForged maven repository.
        maven { url = 'https://maven.neoforged.net/releases' }
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
    id 'net.neoforged.moddev' version '0.1.74'
}

neoForge {
    // We currently only support NeoForge versions later than 21.0.x
    version = "21.0.0-beta"

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

## Vanilla-Mode

In multi-loader projects, you'll often need a subproject for your cross-loader code. This project will also need
access to Minecraft classes, but without any loader-specific extensions.

This plugin solves that by offering a "Vanilla-mode" which you enable by
specifying a [NeoForm version](https://projects.neoforged.net/neoforged/neoform) instead of a NeoForge version.
NeoForm contains the necessary configuration to produce Minecraft jar-files that you can compile against
that contain no other modifications.

In Vanilla-mode, only the `client`, `server` and `data` run types are supported.
Since the plugin includes no mod loader code in this mode, only basic resource- and data packs will be usable in-game.

In `build.gradle`:

Apply the plugin as usual and use a configuration block like this:

```groovy
neoForge {
    // Look for versions on https://projects.neoforged.net/neoforged/neoform
    neoFormVersion = "1.21.-20240613.152323"

    runs {
        client {
            client()
        }
        server {
            server()
        }
        data {
            data()
        }
    }
}
```

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

            // Add arguments passed to the main method
            programArguments = ["--arg"]
            programArgument("--arg")

            // Add arguments passed to the JVM
            jvmArguments = ["-XX:+AllowEnhancedClassRedefinition"]
            jvmArgument("-XX:+AllowEnhancedClassRedefinition")

            // Add system properties
            systemProperties = [
                    "a.b.c": "xyz"
            ]
            systemProperty("a.b.c", "xyz")

            // Set or add environment variables
            environment = [
                    "FOO_BAR": "123"
            ]
            environment("FOO_BAR", "123")

            // Optionally set the log-level used by the game
            logLevel = org.slf4j.event.Level.DEBUG

            // You can change the name used for this run in your IDE
            ideName = "Run Game Tests"
        
            // Changes the source set whose runtime classpath is used for this run. This defaults to "main"
            // Eclipse does not support having multiple runtime classpaths per project (except for unit tests).
            sourceSet = sourceSets.main
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

### Isolated Source Sets

If you work with source sets that do not extend from `main`, and would like the modding dependencies to be available
in those source sets, you can use the following api:

```
sourceSets {
  anotherSourceSet // example
}

neoForge {
  // ...
  addModdingDependenciesTo(sourceSets.anotherSourceSet)
  
  mods {
    mymod {
      sourceSet sourceSets.main
      // Do not forget to add additional soruce-sets here!
      sourceSet sourceSets.anotherSourceSet
    }
  }
}

dependencies {
  implementation sourceSets.anotherSourceSet.output
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

### Unit testing with JUnit

On top of gametests, this plugin supports unit testing mods with JUnit.

For the minimal setup, add the following code to your build script:

```groovy
// Add a test dependency on the test engine JUnit
dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.7.1'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

// Enable JUnit in Gradle:
test {
    useJUnitPlatform()
}

neoForge {
    unitTest {
        // Enable JUnit support in the moddev plugin
        enable()
        // Configure which mod is being tested.
        // This allows NeoForge to load the test/ classes and resources as belonging to the mod.
        testedMod = mods.<mod name > // <mod name> must match the name in the mods { } block.
    }
}
```

You can now use the `@Test` annotation for your unit tests inside the `test/` folder,
and reference Minecraft classes.

#### Loading a server

With the NeoForge test framework, you can run your unit tests in the context of a Minecraft server:

```groovy
dependencies {
    testImplementation "net.neoforged:testframework:<neoforge version>"
}
```

With this dependency, you can annotate your test class as follows:

```java

@ExtendWith(EphemeralTestServerProvider.class)
public class TestClass {
    @Test
    public void testMethod(MinecraftServer server) {
        // Use server...
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

### Advanced Settings for NFRT

```groovy
neoForge {
    neoFormRuntime {
        // Use specific NFRT version
        // Gradle Property: neoForge.neoFormRuntime.version
        version = "1.2.3"

        // Control use of cache
        // Gradle Property: neoForge.neoFormRuntime.enableCache
        enableCache = false

        // Enable Verbose Output
        // Gradle Property: neoForge.neoFormRuntime.verbose
        verbose = true

        // Use Eclipse Compiler for Minecraft
        // Gradle Property: neoForge.neoFormRuntime.useEclipseCompiler
        useEclipseCompiler = true

        // Print more information when NFRT cannot use a cached result
        // Gradle Property: neoForge.neoFormRuntime.analyzeCacheMisses
        analyzeCacheMisses = true
    }
}
```
