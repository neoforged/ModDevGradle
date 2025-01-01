package net.neoforged.moddevgradle.functional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

public abstract class AbstractFunctionalTest {
    static final String DEFAULT_NEOFORGE_VERSION = "21.0.133-beta";

    static final Map<String, String> DEFAULT_PLACEHOLDERS = Map.of(
            "DEFAULT_NEOFORGE_VERSION", DEFAULT_NEOFORGE_VERSION);

    @TempDir
    protected File testProjectDir;
    protected File settingsFile;
    protected File buildFile;

    @BeforeEach
    final void setBaseFiles() {
        settingsFile = new File(testProjectDir, "settings.gradle");
        buildFile = new File(testProjectDir, "build.gradle");
    }

    protected final void writeFile(File destination, String content) throws IOException {
        Files.writeString(destination.toPath(), content);
    }

    protected final void writeProjectFile(String relativePath, String content) throws IOException {
        var destination = testProjectDir.toPath().resolve(relativePath);
        Files.createDirectories(destination.getParent());
        Files.writeString(destination, content);
    }

    void writeGroovySettingsScript(@Language("gradle") String text, Object... args) throws IOException {
        writeFile(settingsFile, interpolateTemplate(text, args));
    }

    void writeGroovyBuildScript(@Language("gradle") String text, Object... args) throws IOException {
        writeFile(buildFile, interpolateTemplate(text, args));
    }

    void writeKotlinBuildScript(@Language("kotlin") String text, Object... args) throws IOException {
        writeFile(buildFile, interpolateTemplate(text, args));
    }

    private static String interpolateTemplate(String text, Object[] args) {
        var m = Pattern.compile("\\{(\\d+|[A-Z_]+)}");
        var body = m.matcher(text).replaceAll(matchResult -> {
            Object arg;
            var key = matchResult.group(1);
            try {
                int index = Integer.parseUnsignedInt(key);
                arg = args[index];
            } catch (NumberFormatException ignored) {
                arg = DEFAULT_PLACEHOLDERS.get(key);
                if (arg == null) {
                    throw new IllegalArgumentException("Invalid placeholder: " + key);
                }
            }
            if (arg instanceof Path path) {
                arg = path.toAbsolutePath().toString().replace('\\', '/');
            } else if (arg instanceof File file) {
                arg = file.getAbsolutePath().replace('\\', '/');
            }
            return Matcher.quoteReplacement(arg.toString());
        });
        return body;
    }

    protected final void clearProjectDir() {
        clearContent(testProjectDir);
    }

    private void clearContent(File file) {
        var content = file.listFiles();
        if (content != null) {
            for (var child : content) {
                if (child.isDirectory()) {
                    clearContent(child);
                }
                child.delete();
            }
        }
    }
}
