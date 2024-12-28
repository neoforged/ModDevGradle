This document describes the breaking changes made across major versions of ModDevGradle.
For a full list of changes, and in which versions they were introduced,
please refer to the changelog, which can be found on the [project page](https://projects.neoforged.net/neoforged/moddevgradle).

## ModDevGradle 2
The breaking changes in this major version should not affect most projects.
Nonetheless, every single breaking change is documented here, along with a suggested fix.

- Modding now needs to be enabled before dependencies are made available, and the NeoForge/NeoForm versions will
  be fixed at the point in time when modding is enabled. Setting `neoForge.version` or `neoForge.neoFormVersion` will
  enable modding when those properties are set. For more advanced use cases, the `neoForge.enable { ... }` block can
  be used, i.e. to not enable modding for the `main` source set. You can only enable modding once for one version
  of NeoForge/NeoForm per project.
- Changes to access transformer and interface injection data publishing.
  - `accessTransformers.publish` and `interfaceInjectionData.publish` syntax was changed.
  - `accessTransformers.published` and `interfaceInjectionData.published` were removed.
  - This publishing feature was broken in ModDevGradle 1, and these changes were made to fix it.
  - To fix: Refer to the [README](README.md#publication-of-access-transformers) for documentation of the new syntax.
- Parchment: Specifying only the Minecraft version or only the mapping version will now fail.
  - This is meant to catch usage mistakes.
- Run `beforeTask`s do not run on IDE project sync anymore.
  - To run a task on sync, use `neoForge.ideSyncTask <task>`.
- Removal of `dependency` and `extendsFrom` inside the `neoForge.mods {}` block.
  - These functions generally do not work, and were removed to reduce confusion.
  - `sourceSet <sourceSet>` should be used instead. If this is not sufficient, please open an issue.
- `mods` cannot contain the same source set multiple times.
  - This is meant to catch usage mistakes.
- The `mods` set in runs was renamed to `loadedMods` to solve the name clash with `mods` in the `neoForge` block. This makes it easier to define which mods should be loaded. Available mods can now be referred to using `mods.<name>` instead of `neoForge.mods.<name>`.
  - Example fix:
```diff
  neoForge {
      runs {
          client {
              /* ... */
-             mods = [neoForge.mods.mod1, neoForge.mods.mod2]
+             loadedMods = [mods.mod1, mods.mod2]
          }
      }
  }
```
- The `neoFormRuntime` property on the `neoForge` extension has been moved to the top-level. This should only affect advanced users. This was done in an effort to make the NFRT-specific tasks in MDG more reusable by projects not making use of a generic mod development environment. The `additionalResults` property moved to `neoForge.additionalMinecraftArtifacts` since it is more sensible to apply this only to MDGs `createMinecraftArtifacts` task rather than all NFRT tasks including user-created ones.
  - To fix, move your NFRT settings block to the project, and move `additionalResults` to `additionalMinecraftArtifacts` in the `neoForge` extension.
- The `idea` plugin is only applied when syncing with IntelliJ.
  - If you want to use the `idea {  }` block in your build script, add the `idea` plugin explicitly.
