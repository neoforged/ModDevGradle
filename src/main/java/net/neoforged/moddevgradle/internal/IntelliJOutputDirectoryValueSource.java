package net.neoforged.moddevgradle.internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;
import javax.inject.Inject;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.InputSource;

/**
 * Checks the IntelliJ project files for the setting that determines whether 1) the build is delegated
 * and 2) the configured output directory.
 * <p>
 * We need to know where the IDE would place its own compiled output files.
 * Delegated builds use Gradles output directories, while non-delegated builds default to subdirectories of {@code out/}.
 */
abstract class IntelliJOutputDirectoryValueSource implements ValueSource<String, IntelliJOutputDirectoryValueSource.Params> {
    // TODO: Loom has unit tests for this... Probably a good idea!
    @Language("xpath")
    private static final String IDEA_DELEGATED_BUILD_XPATH = "/project/component[@name='GradleSettings']/option[@name='linkedExternalProjectsSettings']/GradleProjectSettings/option[@name='delegatedBuild']/@value";
    @Language("xpath")
    private static final String IDEA_OUTPUT_XPATH = "/project/component[@name='ProjectRootManager']/output/@url";

    interface Params extends ValueSourceParameters {
        Property<String> getProjectDir();
    }

    @Inject
    public IntelliJOutputDirectoryValueSource() {}

    /**
     * Returns a function that maps a project to the configured output directory,
     * only if "Build and run using" is set to "IDEA".
     * In other cases, returns {@code null}.
     */
    @Nullable
    static Function<Project, File> getIntellijOutputDirectory(Project project) {
        var outputDirSetting = project.getProviders().of(IntelliJOutputDirectoryValueSource.class, spec -> {
            spec.getParameters().getProjectDir().set(getRootGradleProjectDir(project).getAbsolutePath());
        });

        if (!outputDirSetting.isPresent()) {
            return null;
        }

        var outputDirTemplate = outputDirSetting.get();
        return p -> new File(outputDirTemplate.replace("$PROJECT_DIR$", p.getProjectDir().getAbsolutePath()));
    }

    @Override
    public @Nullable String obtain() {
        var gradleProjectDir = new File(getParameters().getProjectDir().get());

        // Check if an IntelliJ project exists at the given Gradle projects directory
        var ideaDir = new File(gradleProjectDir, ".idea");
        if (!ideaDir.exists()) {
            return null;
        }

        // Check if IntelliJ is configured to build with Gradle.
        var gradleXml = new File(ideaDir, "gradle.xml");
        var delegatedBuild = evaluateXPath(gradleXml, IDEA_DELEGATED_BUILD_XPATH);
        if (!"false".equals(delegatedBuild)) {
            return null;
        }

        // Find configured output path
        var miscXml = new File(ideaDir, "misc.xml");
        String outputDirUrl = evaluateXPath(miscXml, IDEA_OUTPUT_XPATH);
        if (outputDirUrl == null) {
            // Apparently IntelliJ defaults to out/ now?
            outputDirUrl = "file://$PROJECT_DIR$/out";
        }

        // The output dir can start with something like "//C:\"; File can handle it.
        return outputDirUrl.replaceAll("^file:", "");
    }

    @Nullable
    private static String evaluateXPath(File file, @Language("xpath") String expression) {
        try (var fis = new FileInputStream(file)) {
            String result = XPathFactory.newInstance().newXPath().evaluate(expression, new InputSource(fis));
            return result.isBlank() ? null : result;
        } catch (FileNotFoundException | XPathExpressionException ignored) {
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to evaluate xpath " + expression + " on file " + file, e);
        }
    }

    /**
     * Tries to find the Gradle project home that most likely contains the IntelliJ project.
     * In composite build scenarios, the composite root has a higher chance of being the IntelliJ directory,
     * while otherwise it's the root project.
     */
    private static File getRootGradleProjectDir(Project project) {
        // Always try the root of a composite build first, since it has the highest chance
        var root = project.getGradle().getParent();
        if (root != null) {
            while (root.getParent() != null) {
                root = root.getParent();
            }

            return root.getRootProject().getProjectDir();
        }

        // As a fallback or in case of not using composite builds, try the root project folder
        return project.getRootDir();
    }
}
