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
                Version: Keybase OpenPGP v1.0.0
                Comment: https://keybase.io/crypto

                xcFGBGd+VIwBBADHWomubMSopv74iFNSFwfRM4ZPx83Mjl7/2rAEQQFvhEHkctOP
                ufyw+BGrtogK/JNiG7rZRgtANlSu8wh33Ges8jLH4t68h7WhiKp6B0Cln1/D/+Tc
                1pD1GfHgBng8aaVRcglyfWvwZV296fXoco5a0kfDtd9lqgCnC8LnH7p1pQARAQAB
                /gkDCHrqjBL2HZEqYKaFG9N8sj0/rLNPAvk2SRkuTlC0PW5c1XLOk1DKpgoLdHfW
                DrQGfs2wRHCYRVFaVWt8OX1fYMLqst4Gp0KT5yTXPZihSmYAdyGPiwFoxDqY+q40
                ApY2WQ3R0ACQhPynca/d1ETpNOeKMQvFjEZ8psZzu/CPlhVbRqOb6gPFkTAmi8cC
                wXhw7TGrdrqwVz8KlYMBX9UOCy16s2p6E0zA4LERWJQojnLDMHAz47jcyoubtNhP
                daqKt1L7kuANyIWz50t+vUxLFa3KJN1RlFzWK+VbG9Fstjo1WObMWyESPRgnIhyp
                pfYD6Mw+iw4PQ8eMTgBIvgw86v5jLhEAZbXpTtDdUj2kTWtCaJ9JwsXq3zF0B8Rr
                ufwxiI/UfdNYWCOi0tNLTuP13NAlIY4SzwG37pAl8VqVrf62JzO6JcYfrcPx5Lci
                PtkPbcHRTK/XdS2Sbj02Y1xGjEKKOBr2ntUNW7HTSy+MNKKaXlXn7BbNCWEgPGFA
                YS5hPsKtBBMBCgAXBQJnflSMAhsvAwsJBwMVCggCHgECF4AACgkQZr+MKK58JxvO
                AQP+PYoFWpelD/mP4ATXUn2ZPyFgjYYeGBjxzwdNsEe9fFD3TmEyWp1E3zN72Au7
                xYUI2mFFSSb9EV0N50VFlWQlAr+i46VAqdvAbUobZTs8gNSp8aKE0cbPzv8uFsjy
                vKn/gILT3ygD4Jjxc9VUVbyRDXYJUZQEayVfox9LaHm1eBbHwUYEZ35UjAEEALzd
                /yf8/reistaC7dlMcmGfMYEwZZQkBu94oMyHLN37PzZIWBcYb6BbIRPrvGb2tjWH
                Ar2iF77GsVEX/bPMQR42Hb/1vcwberTPF/5Iu8IDqKQlfJPOdYYUaTPf7ujW9iM9
                wdt2XpHl9IV4M2/TswKFguhUAm0Y1OpT2wyUqfLxABEBAAH+CQMIysaTUerHbc1g
                AgBH4QVspyForglvc0emcydzROhQDFB3YEgqsKz9vfQussqcmOMAInaSQFM2Uup0
                e2hKkgyaKUZiny3ZKG8nwfYYJgUcpXmW10Y9Z2tCCa/QKZH7Qqr+QUO5SVmX+7GI
                OzwDjxjaSnzG2U6unu2spUJRO5LjHzIevrkuwBQZSS9TlyORG4AA0x8MJgG7ydhY
                bhlZ1vXE8c7XFvzW7OcvRTEuB+DhErUcX/Yp0GlXfU9HiIckr8D75nxXTJqJtKZS
                RYbOFQiLnfRtlwAj37I41nIWjubqxnBONZBOZLn8OfGZmMH5wShyzUOX04DQ3HcR
                AuSeZva3f6xkFZMx5KgiZuEuqWG1aIeGhLrbFj8OSWL+DSwIvHtpsJPV304iD9E1
                Du8y73Pn44bi8oNvooEiYz//Cq4PZ+OdmzIB4ASma6syGJlcFS66TMUW6Pis1sWK
                1WxhFdbNtYvuNO1tuxDYcaGumyurHBPtY9qRycLAgwQYAQoADwUCZ35UjAUJDwmc
                AAIbLgCoCRBmv4wornwnG50gBBkBCgAGBQJnflSMAAoJEAEZK+RSu2wz/ukD/RAY
                fai3ojMvIyUb2PA85NnSIImtJ5HyH8o0KvUdpaDzVUPp2fV791mib8qVMfG4jymK
                KQ/m8XxktBNCyyqFuo2H8wRgLYLxNVV8PePOOxzRCzxsVangxlTufx35OjgCt5X5
                WOZEhmLxDJ4oHHrJarhjCy76GDUV699RecB8iWlm130EAKIW/ls2kCR5lvFdVAKx
                H2+iCtkMgNzyuahZZ7pH/IzjblKiEwYIFqTcHdzoN/3PzrJ2DR6/Ks1kG+s8Nukn
                Mq4+pJ7M47HCPCShFbmJjfQVtrgXJ3b8e8Ku46sLnRwwlpZKt+3at+r9ugnhuKFw
                XmFNU5QaiYQoSDmFT3WjpMGOx8FGBGd+VIwBBADNwY2Uk7aK6WzncM+uuw/SI981
                l6AEuMQlSutHPeBp4y0ljTsri/ObO/atQbKJMe++zuOcgykgd5TJGlvofenmml/4
                icsDUYe6UFRkG9pEI1yg7V79kbqgpe70efTBkILmSjTyzkzmz19pVZON15p5eYhb
                /bbGCClZuZAAC3mb9wARAQAB/gkDCOnWaYcrAnnjYEMKm8IKLhjmsxkHLSLVJJ9S
                KLiHo4oHRg4vWx1NUuXqc4j7gQxZ0D9+I1cjevXPDhyx5EO++zCN1wU5jg5Nu3C7
                Bs2zYqoijEq5wD+8GRw8eYfh0x/eFVCIsmDWn2C8I7AkKX2Qto2N7iSI1X3mmUMe
                ZKbTdF1Qp7phvYOrxKNHX6h4UTH8JjFu3BaEmz6sfM2i7lhGWYiloFqEtWQlrPXQ
                x12jWZItlL+aTwjFTDLKIwsR/1N/V0O3K8X6INFPtAbvyZrpZoZExLjqzewyMQwJ
                0tBJ4VNMmJP4/Igz3QU55B99lSaDBWEjd1bBMErm2RugBhiL6r3DuGvUaMKLJr1F
                s2yjhSKBnKnCxqGw2e5RGYv0eFBgTWLwbamrPjdjc/aziFj8Kh2BaUCvVLMyxCP4
                EjWTAH7mYgdhJzDkcCkNp2lQwI92FdJo/9xRZBQuxkEhRDSTPuBd+IlnrrJ24ywl
                SsBUoCORS9SmSg/CwIMEGAEKAA8FAmd+VIwFCQ8JnAACGy4AqAkQZr+MKK58Jxud
                IAQZAQoABgUCZ35UjAAKCRC4U+9ks5MVHAqbA/9dSjfQLQJOgYT0YKmOWkY9tobP
                nvIYq+KfnM1ZhJ65KqiPL7w9gHs+O/74/jd12lgHCV3YLx2wcqGAiLuBqyez4lC+
                yZgTuHwbyPB5PZainmG5x+fKtv07po0iRXIf87Kmo+jHS/TcVRl2Vkxez8hM803B
                qW15QGWKp3hFTgS83/hWA/9ELQ36Cb6XmUgKk2cquNG14syyXEyXX5PgOoGGUISe
                ORGT5SgjVAgakreRmDjjOOiTm/ikNWsLAABRu6+y543OG1woz/LytqHfLq6Oq5s7
                q4aZRcRG0ncu8va8lFLK1AWaY7zHjee2lpmfT/260J++cZbO7XAI/ADCT8/Z30BO
                Zg==
                =QLGC
                -----END PGP PRIVATE KEY BLOCK-----
                        ""\",
                        "password"
                    )
                    sign publishing.publications.maven
                }
                """);

        assertThat(Files.exists(atFile.resolveSibling(atFile.getFileName() + ".asc")))
                .isFalse();
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
            @Language("groovy") String buildScriptBody) throws IOException {
        writeGroovySettingsScript("""
                plugins {
                    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
                }
                rootProject.name = "{0}"
                """, artifactId);
        // TODO we should probably not just always apply the signing plugin?
        writeGroovyBuildScript("""
                plugins {
                    id "net.neoforged.moddev"
                    id "maven-publish"
                    id "signing"
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
                """, groupId, version, buildScriptBody, publicationTarget);

        var result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir)
                .withArguments("publish")
                .withDebug(true)
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":publish").getOutcome());
    }
}
