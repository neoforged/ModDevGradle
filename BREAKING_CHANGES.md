This document describes the breaking changes made across major versions of ModDevGradle.
For a full list of changes, and in which versions they were introduced,
please refer to the changelog, which can be found on the [project page](https://projects.neoforged.net/neoforged/moddevgradle).

## ModDevGradle 2
MDG 2 received a few breaking changes that should not affect most users.
Nonetheless, every single breaking change is documented here, along with a suggested fix.

- Changes to access transformer and interface injection data publishing.
  - `accessTransformers.publish` and `interfaceInjectionData.publish` syntax was changed.
  - `accessTransformers.published` and `interfaceInjectionData.published` were removed.
  - This publishing feature was broken in ModDevGradle 1, and these changes were made to fix it.
  - To fix: Refer to the [README](README.md#publication-of-access-transformers) for documentation of the new syntax.
- Parchment: Specifying only the Minecraft version or only the mapping version will now fail.
  - This is meant to catch usage mistakes.
