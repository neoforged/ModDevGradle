package net.neoforged.moddevgradle.legacyforge.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import net.neoforged.moddevgradle.legacyforge.internal.LegacyForgeModDevPlugin;
import net.neoforged.moddevgradle.legacyforge.tasks.RemapJar;
import org.gradle.jvm.tasks.Jar;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class MixinMappingTest {
    /**
     * Tests that we expect the Mixin AP to create for each refmap is added to the reobfuscation tasks
     * (both the default reobfJar and custom ones).
     */
    @Test
    public void testMixinMappingsArePropagatedToObfuscationTasks() {
        var project = ProjectBuilder.builder().build();
        project.getPlugins().apply(LegacyForgeModDevPlugin.class);

        var obfuscation = ExtensionUtils.getExtension(project, LegacyForgeModDevPlugin.OBFUSCATION_EXTENSION, ObfuscationExtension.class);
        var sourceSets = ExtensionUtils.getSourceSets(project);
        var mixinExtension = ExtensionUtils.getExtension(project, LegacyForgeModDevPlugin.MIXIN_EXTENSION, MixinExtension.class);
        var mainSourceSet = sourceSets.getByName("main");
        mixinExtension.add(mainSourceSet, "testmod.refmap.json");

        var someJarTask = project.getTasks().register("someJar", Jar.class);
        var customRemapJarTask = obfuscation.reobfuscate(someJarTask, mainSourceSet).get();

        var remapJarTask = (RemapJar) project.getTasks().getByName("reobfSomeJar");

        // The main named->intermediary mappings for the game
        var namedToIntermediary = project.getLayout().getBuildDirectory().file("moddev/artifacts/namedToIntermediate.tsrg").get().getAsFile();
        var mixinApMappings = project.getLayout().getBuildDirectory().file("mixin/testmod.refmap.json.mappings.tsrg").get().getAsFile();

        // Enable modding to actually wire up the tasks
        ExtensionUtils.getExtension(project, LegacyForgeModDevPlugin.LEGACYFORGE_EXTENSION, LegacyForgeExtension.class).setVersion("1.20.1-47.11");

        // The mapping file produced by the Mixin AP should be added as an input to both Jar tasks.
        var otherMappings = customRemapJarTask.getRemapOperation().getMappings().getFiles();
        assertThat(otherMappings).containsOnly(namedToIntermediary, mixinApMappings);
        var mappings = remapJarTask.getRemapOperation().getMappings().getFiles();
        assertThat(mappings).containsOnly(namedToIntermediary, mixinApMappings);
    }
}
