package net.neoforged.moddevgradle.dsl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParchmentTest {
    Project project;
    Parchment parchment;

    @BeforeEach
    void setUp() {
        project = ProjectBuilder.builder().build();
        parchment = project.getObjects().newInstance(Parchment.class, project);
    }

    @Test
    void testNothingSetMeansNoArtifactAndNoError() {
        assertFalse(parchment.getParchmentArtifact().isPresent());
        assertFalse(parchment.getEnabled().get());
    }

    @Test
    void testSettingOnlyMinecraftVersionCausesError() {
        parchment.getMinecraftVersion().set("1.2.3");
        assertThatThrownBy(() -> parchment.getParchmentArtifact().isPresent())
                .hasRootCauseMessage("If you set neoForge.parchment.minecraftVersion, you also must set mappingVersion");
    }

    @Test
    void testSettingOnlyMappingVersionCausesError() {
        parchment.getMappingsVersion().set("1.2.3");
        assertThatThrownBy(() -> parchment.getParchmentArtifact().isPresent())
                .hasRootCauseMessage("If you set neoForge.parchment.mappingVersion, you also must set minecraftVersion");
    }

    @Test
    void testSettingBothMinecraftVersionAndMappingVersionResultsInArtifact() {
        parchment.getMappingsVersion().set("1.2.3");
        parchment.getMinecraftVersion().set("2.3.4");
        assertEquals("org.parchmentmc.data:parchment-2.3.4:1.2.3@zip", parchment.getParchmentArtifact().get());
    }
}
