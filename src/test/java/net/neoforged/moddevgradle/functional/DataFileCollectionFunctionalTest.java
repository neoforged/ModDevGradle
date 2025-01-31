package net.neoforged.moddevgradle.functional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DataFileCollectionFunctionalTest extends AbstractFunctionalTest {
    @TempDir
    File publicationTarget;

    @Test
    public void testPublishAccessTransformerFile() throws IOException {
        Files.writeString(testProjectDir.toPath().resolve("accesstransformer.cfg"), "# hello world");

        publishDataFiles("test", "publish-at", "1.0", """
                neoForge {
                    accessTransformers {
                        publish(project.file("accesstransformer.cfg"))
                    }
                }
                """);

        assertThat(consumeDataFilePublication("accessTransformers", "test:publish-at:1.0")).containsOnly(
                entry("publish-at-1.0-accesstransformer.cfg", "# hello world"));
    }

    @Test
    public void testPublishSignedAccessTransformerFileDoesntProduceGarbageNextToTheFile() throws IOException {
        var atFile = testProjectDir.toPath().resolve("accesstransformer.cfg");
        Files.writeString(atFile, "# hello world");

        publishDataFiles("test", "publish-at", "1.0", """
                neoForge {
                    accessTransformers {
                        publish(project.file("accesstransformer.cfg"))
                    }
                }
                signing {
                    useInMemoryPgpKeys(
                        ""\"
                -----BEGIN PGP PRIVATE KEY BLOCK-----

                lIYEZ50OURYJKwYBBAHaRw8BAQdAjVNqyWTfusmnT3/pNNmjmG9AfOFL8YbFUXCo
                PZO3dCj+BwMCZ5yIZTjHa2r/WIUWn/nr1+5CLJOI1xzscxZOq6Vyvh9m3TWgPKWh
                iW/MM/tGGvOnFt4qD+WNYNaHo3GtbG+KoK0584Ddon60yFEAPCdAUrQXTURHIDxt
                ZGdAbmVvZm9yZ2VkLm5ldD6IkwQTFgoAOxYhBN0jxfacUTgqiXj3CnkqaxjDscQa
                BQJnnQ5RAhsDBQsJCAcCAiICBhUKCQgLAgQWAgMBAh4HAheAAAoJEHkqaxjDscQa
                93YA/ix1FkHrtF+VglGcSBGUvtqfiBzYPvwrazNdURxCZpaVAP4zWPYtbDJP14dP
                s8I/jILSUfE61lVl6Y74tpiEvNm8Bg==
                =3g5S
                -----END PGP PRIVATE KEY BLOCK-----
                        ""\",
                        "123456"
                    )
                    sign publishing.publications.maven
                }
                """, "signing");

        assertThat(atFile.resolveSibling(atFile.getFileName() + ".asc"))
                .doesNotExist();
        var copiedFile = testProjectDir.toPath()
                .resolve("build/copyAccessTransformersPublications/0-accesstransformer.cfg");
        assertThat(copiedFile)
                .hasSameTextualContentAs(atFile);
        assertThat(copiedFile.resolveSibling(copiedFile.getFileName() + ".asc"))
                .exists();
        assertThat(consumeDataFilePublication("accessTransformers", "test:publish-at:1.0")).containsOnly(
                entry("publish-at-1.0-accesstransformer.cfg", "# hello world"));
    }

    @Test
    public void testPublishInterfaceInjectionFile() throws IOException {
        writeProjectFile("interfaces.json", "[]");
        writeProjectFile("subfolder/interfaces.json", "[]");
        Files.writeString(testProjectDir.toPath().resolve("interfaces.json"), "[]");

        publishDataFiles("test", "publish-if", "1.0", """
                def generatedDataFile = tasks.register("generateDataFile") {
                    outputs.file("build/generatedDataFile.json")
                    doFirst {
                        outputs.files.singleFile.text = '{}'
                    }
                }
                neoForge {
                    interfaceInjectionData {
                         publish(file('interfaces.json'))
                         publish(file('subfolder/interfaces.json'))
                         publish(generatedDataFile)
                    }
                }
                """);

        assertThat(consumeDataFilePublication("interfaceInjectionData", "test:publish-if:1.0")).containsOnly(
                entry("publish-if-1.0-interfaceinjection1.json", "[]"),
                entry("publish-if-1.0-interfaceinjection2.json", "[]"),
                entry("publish-if-1.0-interfaceinjection3.json", "{}"));
    }

    @Test
    public void testNoEmptyVariantsArePublished() throws IOException {
        publishDataFiles("test", "publish-empty", "1.0", "");

        // Load the Gradle modules file
        var modulePath = publicationTarget.toPath().resolve("test/publish-empty/1.0/publish-empty-1.0.module");
        var moduleContent = new Gson().fromJson(Files.readString(modulePath), JsonObject.class);
        var variants = moduleContent.getAsJsonArray("variants");
        assertThat(variants.asList())
                .extracting(e -> ((JsonObject) e).getAsJsonPrimitive("name").getAsString())
                .containsOnly("apiElements", "runtimeElements");
    }

    private Map<String, String> consumeDataFilePublication(String configurationName, String gav) throws IOException {
        clearProjectDir();

        // Now try to consume it
        writeGroovySettingsScript("""
                plugins {
                    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
                }
                rootProject.name = "consume-if"
                """);
        writeGroovyBuildScript("""
                plugins {
                    id "net.neoforged.moddev"
                }
                dependencies {
                    {1} "{2}"
                }
                repositories {
                    maven { url = file("{0}") }
                }
                tasks.register("copyDataFiles", Copy) {
                    from(configurations.named("{1}"))
                    into("build/dataFiles")
                }
                """, publicationTarget, configurationName, gav);

        GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir)
                .withArguments("copyDataFiles")
                .withDebug(true)
                .build();

        var dataFilesDir = new File(testProjectDir, "build/dataFiles");
        var files = dataFilesDir.list();
        if (files == null) {
            return Map.of();
        }
        var result = new HashMap<String, String>();
        for (String file : files) {
            result.put(file, Files.readString(dataFilesDir.toPath().resolve(file)));
        }
        return result;
    }

    private void publishDataFiles(String groupId,
            String artifactId,
            String version,
            @Language("groovy") String buildScriptBody,
            String... extraPlugins) throws IOException {
        String extraPluginLines = Stream.of(extraPlugins)
                .map("id \"%s\""::formatted)
                .collect(Collectors.joining("\n"));
        writeGroovySettingsScript("""
                plugins {
                    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
                }
                rootProject.name = "{0}"
                """, artifactId);
        writeGroovyBuildScript("""
                plugins {
                    id "net.neoforged.moddev"
                    id "maven-publish"
                    {4}
                }
                group = "{0}"
                version = "{1}"
                neoForge {
                    version = "{DEFAULT_NEOFORGE_VERSION}"
                }
                publishing {
                    publications {
                        maven(MavenPublication) {
                            from components.java
                        }
                    }
                    repositories {
                        maven {
                            url file("{3}")
                        }
                    }
                }
                {2}
                """, groupId, version, buildScriptBody, publicationTarget, extraPluginLines);

        var result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir)
                .withArguments("publish")
                .withDebug(true)
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":publish").getOutcome());
    }
}
