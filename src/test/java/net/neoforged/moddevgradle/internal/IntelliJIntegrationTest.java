package net.neoforged.moddevgradle.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.neoforged.moddevgradle.internal.utils.ExtensionUtils;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IntelliJIntegrationTest {
    @ParameterizedTest
    @CsvSource(textBlock = """
            plain|plain
            with space|with_space
            1.21.1-neoforge|1_21_1-neoforge
            with/slash|with_slash
            """, delimiter = '|')
    void testEscapeModuleNameElement(String element, String escaped) {
        assertEquals(escaped, IntelliJIntegration.escapeModuleNameElement(element));
    }

    @Test
    void testModuleNameOfRootProject() {
        var root = ProjectBuilder.builder().withName("xaeronav").build();
        assertEquals("xaeronav.main", getMainModuleName(root));
    }

    /**
     * IntelliJ escapes each element of the path separately, so a '.' in a subproject name becomes
     * '_'. Joining the raw path would produce "xaeronav.1.21.1-neoforge.main", which is not a
     * module that exists, and the run configuration would be written without a module.
     */
    @Test
    void testModuleNameOfSubprojectContainingDots() {
        var root = ProjectBuilder.builder().withName("xaeronav").build();
        var subproject = ProjectBuilder.builder().withName("1.21.1-neoforge").withParent(root).build();
        assertEquals("xaeronav.1_21_1-neoforge.main", getMainModuleName(subproject));
    }

    @Test
    void testModuleNameOfNestedSubproject() {
        var root = ProjectBuilder.builder().withName("root").build();
        var parent = ProjectBuilder.builder().withName("versions").withParent(root).build();
        var subproject = ProjectBuilder.builder().withName("1.21.1").withParent(parent).build();
        assertEquals("root.versions.1_21_1.main", getMainModuleName(subproject));
    }

    private static String getMainModuleName(Project project) {
        project.getPluginManager().apply("java");
        SourceSet sourceSet = ExtensionUtils.getSourceSets(project).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        return IntelliJIntegration.getIntellijModuleName(project, sourceSet);
    }
}
