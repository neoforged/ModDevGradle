package net.neoforged.moddevgradle.functional;

import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PublishDataFilesTest extends AbstractFunctionalTest {

    @TempDir
    File publicationTarget;

    @Test
    public void testPublishAccessTransformerFile() throws IOException {
        Files.writeString(testProjectDir.toPath().resolve("accesstransformer.cfg"), "# hello world");

        writeGroovySettingsScript("""
                plugins {
                    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
                }
                rootProject.name = "publish-at"
                """);
        writeGroovyBuildScript("""
                plugins {
                    id "net.neoforged.moddev"
                    id "maven-publish"
                }
                group = "test"
                version = "1.0"
                neoForge {
                    version = "{DEFAULT_NEOFORGE_VERSION}"
                    accessTransformers {
                        publish(project.file("accesstransformer.cfg"))
                    }
                }
                publishing {
                    publications {
                        maven(MavenPublication) {
                            from components.java
                        }
                    }
                    repositories {
                        maven {
                            url rootProject.file("{0}")
                        }
                    }
                }
                """, publicationTarget);

        var result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir)
                .withArguments("publish")
                .withDebug(true)
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":publish").getOutcome());
    }

    @Test
    public void testPublishInterfaceInjectionFile() throws IOException {
        writeProjectFile("interfaces.json", "[]");
        writeProjectFile("subfolder/interfaces.json", "[]");
        Files.writeString(testProjectDir.toPath().resolve("interfaces.json"), "[]");

        writeGroovySettingsScript("""
                plugins {
                    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
                }
                rootProject.name = "publish-if"
                """);
        writeGroovyBuildScript("""
                plugins {
                    id "net.neoforged.moddev"
                    id "maven-publish"
                }
                group = "test"
                version = "1.0"
                neoForge {
                    version = "{DEFAULT_NEOFORGE_VERSION}"
                    interfaceInjectionData {
                         publish(file('interfaces.json'))
                         publish(file('subfolder/interfaces.json'))
                    }
                }
                publishing {
                    publications {
                        maven(MavenPublication) {
                            from components.java
                        }
                    }
                    repositories {
                        maven {
                            url file("{0}")
                        }
                    }
                }
                def generatedDataFile = tasks.register("generateDataFile") {
                    outputs.file("build/generatedDataFile.json")
                    doFirst {
                        outputs.files.singleFile.text = '{}'
                    }
                }
                neoForge.interfaceInjectionData.publish(generatedDataFile)
                """, publicationTarget);

        var result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir)
                .withArguments("publish")
                .withDebug(true)
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":publish").getOutcome());

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
                    interfaceInjectionData "test:publish-if:1.0"
                }
                repositories {
                    maven { url = file("{0}") }
                }
                task printFileList {
                    doFirst {
                        println(configurations.interfaceInjectionData.files)
                    }
                }
                """, publicationTarget);

        var consumeResult = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir)
                .withArguments("printFileList")
                .withDebug(true)
                .build();

        System.out.println(consumeResult.getOutput());

    }

}
