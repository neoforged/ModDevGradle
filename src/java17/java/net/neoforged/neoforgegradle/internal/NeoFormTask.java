package net.neoforged.neoforgegradle.internal;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.util.List;

abstract public class NeoFormTask extends DefaultTask {

    @Classpath
    @InputFiles
    abstract ConfigurableFileCollection getNeoFormRuntime();

    protected final void run(List<String> args) {
        var launcher = getJavaToolchainService().launcherFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(21)));

        getExecOperations().javaexec(execSpec -> {
            // See https://github.com/gradle/gradle/issues/28959
            execSpec.jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8");
            execSpec.executable(launcher.get().getExecutablePath().getAsFile());
            execSpec.classpath(getNeoFormRuntime());
            execSpec.args(args);
        });
    }

    @Inject
    protected abstract JavaToolchainService getJavaToolchainService();

    @Inject
    protected abstract ExecOperations getExecOperations();

}
