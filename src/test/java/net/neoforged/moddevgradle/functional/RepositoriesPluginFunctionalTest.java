package net.neoforged.moddevgradle.functional;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;

public class RepositoriesPluginFunctionalTest extends AbstractFunctionalTest {
    private static final Pattern REPOSITORY_ORDER_PATTERN = Pattern.compile("REPOSITORY_ORDER=(.+)");
    private static final List<String> MANAGED_REPOSITORIES = List.of("NeoForged Releases");

    @Test
    void placesMavenCentralBeforeManagedRepositories() throws IOException {
        writeFile(settingsFile, "rootProject.name = 'repository-order-test'");
        writeGroovyBuildScript("""
                plugins {
                    id "net.neoforged.moddev.repositories"
                }

                repositories {
                    mavenCentral()
                }

                tasks.register("printRepositoryOrder") {
                    doLast {
                        println("REPOSITORY_ORDER=" + repositories.collect {
                            def urlPart = it.hasProperty("url") ? it.url.toString() : ""
                            return it.name + "@" + urlPart
                        }.join("|"))
                    }
                }
                """);

        BuildResult result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir)
                .withArguments("printRepositoryOrder")
                .build();

        List<String> repositoryNames = extractRepositoryOrder(result);
        assertMavenCentralBeforeManagedRepositories(repositoryNames);
    }

    @Test
    void placesMavenCentralBeforeManagedRepositoriesInSettings() throws IOException {
        writeGroovySettingsScript("""
                plugins {
                    id "net.neoforged.moddev.repositories"
                }

                rootProject.name = "repository-order-settings-test"

                dependencyResolutionManagement {
                    repositories {
                        mavenCentral()
                    }
                }

                gradle.settingsEvaluated {
                    println("REPOSITORY_ORDER=" + dependencyResolutionManagement.repositories.collect {
                        def urlPart = it.hasProperty("url") ? it.url.toString() : ""
                        return it.name + "@" + urlPart
                    }.join("|"))
                }
                """);
        writeGroovyBuildScript("");

        BuildResult result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(testProjectDir)
                .withArguments("help")
                .build();

        List<String> repositoryNames = extractRepositoryOrder(result);
        assertMavenCentralBeforeManagedRepositories(repositoryNames);
    }

    private static List<String> extractRepositoryOrder(BuildResult result) {
        var matcher = REPOSITORY_ORDER_PATTERN.matcher(result.getOutput());
        assertThat(matcher.find())
                .as("Expected repository order marker in Gradle output")
                .isTrue();
        return List.of(matcher.group(1).split("\\|"));
    }

    private static void assertMavenCentralBeforeManagedRepositories(List<String> repositoryNames) {
        int mavenCentralIndex = -1;
        for (int i = 0; i < repositoryNames.size(); i++) {
            String repository = repositoryNames.get(i);
            if (repository.contains("repo.maven.apache.org/maven2") || repository.contains("repo1.maven.org/maven2")) {
                mavenCentralIndex = i;
                break;
            }
        }
        int firstManagedIndex = repositoryNames.size();
        for (String managedRepository : MANAGED_REPOSITORIES) {
            for (int i = 0; i < repositoryNames.size(); i++) {
                if (repositoryNames.get(i).startsWith(managedRepository + "@") && i < firstManagedIndex) {
                    firstManagedIndex = i;
                }
            }
        }

        assertThat(mavenCentralIndex)
                .as("Expected mavenCentral repository to exist. Actual order: %s", repositoryNames)
                .isGreaterThanOrEqualTo(0);
        assertThat(firstManagedIndex)
                .as("Expected at least one managed repository to exist. Actual order: %s", repositoryNames)
                .isLessThan(repositoryNames.size());
        assertThat(mavenCentralIndex)
                .as("Expected mavenCentral before managed repositories. Actual order: %s", repositoryNames)
                .isLessThan(firstManagedIndex);
    }
}
