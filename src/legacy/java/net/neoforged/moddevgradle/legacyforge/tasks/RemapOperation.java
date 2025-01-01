package net.neoforged.moddevgradle.legacyforge.tasks;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import net.neoforged.moddevgradle.internal.utils.NetworkSettingPassthrough;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.ExecOperations;

public abstract class RemapOperation implements Serializable {
    @Inject
    public RemapOperation() {}

    @Input
    public abstract Property<ToolType> getToolType();

    @Classpath
    @InputFiles
    public abstract ConfigurableFileCollection getToolClasspath();

    @Internal
    protected abstract RegularFileProperty getLogFile();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getMappings();

    public void execute(ExecOperations operations, File input, File output, FileCollection libraries) throws IOException {
        final List<String> args = new ArrayList<>();

        args.addAll(Arrays.asList("--input", input.getAbsolutePath()));
        args.addAll(Arrays.asList("--output", output.getAbsolutePath()));
        if (getToolType().get() == ToolType.ART) {
            getMappings().forEach(file -> args.addAll(Arrays.asList("--names", file.getAbsolutePath())));
            libraries.forEach(lib -> args.addAll(Arrays.asList("--lib", lib.getAbsolutePath())));
            args.add("--disable-abstract-param");
            args.add("--strip-sigs");
        } else {
            args.addAll(Arrays.asList("--task", "SRG_TO_MCP"));
            getMappings().forEach(file -> args.addAll(Arrays.asList("--mcp", file.getAbsolutePath())));
            args.add("--strip-signatures");
        }

        try (var log = getLogFile().isPresent() ? Files.newOutputStream(getLogFile().get().getAsFile().toPath()) : new OutputStream() {
            @Override
            public void write(int b) {}
        }) {
            operations.javaexec(execSpec -> {
                // Pass through network properties
                execSpec.systemProperties(NetworkSettingPassthrough.getNetworkSystemProperties());

                // See https://github.com/gradle/gradle/issues/28959
                execSpec.jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8");

                execSpec.classpath(getToolClasspath());
                execSpec.args(args);
                execSpec.setStandardOutput(log);
            }).rethrowFailure().assertNormalExitValue();
        }
    }

    public enum ToolType {
        ART,
        INSTALLER_TOOLS
    }
}
