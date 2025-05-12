package net.neoforged.nfrtgradle;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import javax.inject.Inject;
import net.neoforged.moddevgradle.internal.utils.ProblemReportingUtil;
import net.neoforged.problems.FileProblemReporter;
import net.neoforged.problems.Problem;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.problems.Problems;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.ApiStatus;

/**
 * The primary task for creating the Minecraft artifacts that mods will be compiled against,
 * using the NFRT CLI.
 */
@DisableCachingByDefault(because = "Implements its own caching")
@ApiStatus.NonExtendable
public abstract class CreateMinecraftArtifacts extends NeoFormRuntimeTask {
    @Inject
    public CreateMinecraftArtifacts() {
        // When cache is disabled, the task is NEVER up-to-date to aid with debugging problems
        getOutputs().upToDateWhen(task -> ((CreateMinecraftArtifacts) task).getEnableCache().get());
        getEnableCache().convention(true);
        getUseEclipseCompiler().convention(false);
        getAnalyzeCacheMisses().convention(false);
        getValidateAccessTransformers().convention(false);
        getParchmentEnabled().convention(false);
    }

    /**
     * Path to the Java installation to use for running external tools with. This does not have to match
     * NFRTs Java version. I.e. if running NFRT for MC 1.12, this would need to be Java 8, since the decompiler
     * and other tools in that version were not updated to run with Java 21.
     */
    @Input
    @Optional
    public abstract Property<String> getToolsJavaExecutable();

    /**
     * Files added to this collection will be passed to NFRT via the {@code --access-transformer}
     * command line option.
     */
    @InputFiles
    public abstract ConfigurableFileCollection getAccessTransformers();

    /**
     * Files added to this collection will be passed to NFRT via the {@code --validated-access-transformer}
     * command line option and will fail the build if they contain errors.
     * <p>
     * This is a more precise version of setting {@link #getValidateAccessTransformers()} to {@code true}.
     */
    @InputFiles
    public abstract ConfigurableFileCollection getValidatedAccessTransformers();

    /**
     * Files added to this collection will be passed to NFRT via the {@code --interface-injection-data}
     * command line option.
     */
    @InputFiles
    public abstract ConfigurableFileCollection getInterfaceInjectionData();

    /**
     * If set to true, all files from {@link #getAccessTransformers()} are added as validated ATs and will fail the build
     * if they contain errors, or they target non-existent code elements.
     * <p>
     * Defaults to false.
     *
     * @deprecated Prefer the more precise {@link #getValidatedAccessTransformers()} setting.
     */
    @Input
    @Deprecated
    public abstract Property<Boolean> getValidateAccessTransformers();

    /**
     * Enables the use of {@linkplain #getParchmentData Parchment data}.
     * Defaults to false.
     */
    @Input
    public abstract Property<Boolean> getParchmentEnabled();

    /**
     * When {@link #getParchmentEnabled() Parchment is enabled}, this collection is expected to contain a
     * single Parchment data file, which will be passed to NFRT using the {@code --parchment-data}
     * command line parameter.
     */
    @InputFiles
    public abstract ConfigurableFileCollection getParchmentData();

    /**
     * When this property is set to a non-blank string, it will be passed to NFRT using the {@code --parchment-conflict-prefix}
     * command line parameter.
     */
    @Input
    @Optional
    public abstract Property<String> getParchmentConflictResolutionPrefix();

    /**
     * This property can be used to access additional results of the NeoForm process being run by NFRT.
     * The map key is the ID of the result while the value is the output file where that result should be written.
     * Trying to set a non-existent result will result in an error, but print available results to the console.
     * Entries in this map will be passed to NFRT using the {@code --write-result} command line option.
     */
    @OutputFiles
    public abstract MapProperty<String, File> getAdditionalResults();

    /**
     * Gradle dependency notation for the NeoForge userdev artifact.
     * Either this or {@link #getNeoFormArtifact()} must be specified.
     */
    @Input
    @Optional
    public abstract Property<String> getNeoForgeArtifact();

    /**
     * Gradle dependency notation for the NeoForm data artifact.
     * Either this or {@link #getNeoForgeArtifact()} must be specified.
     */
    @Input
    @Optional
    public abstract Property<String> getNeoFormArtifact();

    /**
     * Enables use of the NFRT cache.
     * Defaults to true.
     * Corresponds to the NFRT command line option {@code --disable-cache}.
     */
    @Internal
    public abstract Property<Boolean> getEnableCache();

    /**
     * When the {@linkplain #getEnableCache() cache is enabled} and this is set to true, additional details will
     * be printed to the console when a cache miss occurs.
     * Corresponds to the NFRT command line option {@code --analyze-cache-misses}.
     */
    @Internal
    public abstract Property<Boolean> getAnalyzeCacheMisses();

    /**
     * Set this to true to enable the use of the Eclipse Java Compiler to produce the compiled Minecraft artifacts.
     * Defaults to false.
     * Corresponds to the NFRT command line option {@code --use-eclipse-compiler}.
     */
    @Input
    public abstract Property<Boolean> getUseEclipseCompiler();

    /**
     * This retrieves the result of the NeoForm process that produces a compiled Minecraft jar that includes
     * the Minecraft sources as well.
     * This is useful for working around IntelliJ limitations related to not being able to attach sources as a
     * separate artifact automatically.
     */
    @OutputFile
    @Optional
    public abstract RegularFileProperty getCompiledWithSourcesArtifact();

    /**
     * This retrieves the same as {@link #getCompiledWithSourcesArtifact()}, but doesn't include the sources in the
     * Jar file.
     */
    @OutputFile
    @Optional
    public abstract RegularFileProperty getCompiledArtifact();

    /**
     * This retrieves a Zip-File containing the sources used to compile {@link #getCompiledArtifact()}.
     */
    @OutputFile
    @Optional
    public abstract RegularFileProperty getSourcesArtifact();

    /**
     * Also known as "client-extra". Contains the non-class files from the original Minecraft jar (excluding META-INF).
     */
    @OutputFile
    @Optional
    public abstract RegularFileProperty getResourcesArtifact();

    @Inject
    protected abstract Problems getProblems();

    @TaskAction
    public void createArtifacts() {
        var args = new ArrayList<String>();
        args.add("run");

        if (getToolsJavaExecutable().isPresent()) {
            args.add("--java-executable");
            args.add(getToolsJavaExecutable().get());
        }

        // If an AT path is added twice, the validated variant takes precedence
        var accessTransformersAdded = new HashSet<File>();
        for (var accessTransformer : getValidatedAccessTransformers().getFiles()) {
            if (accessTransformersAdded.add(accessTransformer)) {
                args.add("--validated-access-transformer");
                args.add(accessTransformer.getAbsolutePath());
            }
        }

        for (var accessTransformer : getAccessTransformers().getFiles()) {
            if (accessTransformersAdded.add(accessTransformer)) {
                if (getValidateAccessTransformers().get()) {
                    args.add("--validated-access-transformer");
                } else {
                    args.add("--access-transformer");
                }
                args.add(accessTransformer.getAbsolutePath());
            }
        }

        for (var interfaceInjectionFile : getInterfaceInjectionData().getFiles()) {
            args.add("--interface-injection-data");
            args.add(interfaceInjectionFile.getAbsolutePath());
        }

        if (getParchmentEnabled().get()) {
            var parchmentData = getParchmentData().getFiles();
            if (parchmentData.size() == 1) {
                args.add("--parchment-data");
                args.add(parchmentData.iterator().next().getAbsolutePath());
            } else if (parchmentData.size() > 1) {
                throw new GradleException("More than one parchment data file was specified: " + parchmentData);
            }

            var conflictResolutionPrefix = getParchmentConflictResolutionPrefix().getOrElse("");
            if (getParchmentConflictResolutionPrefix().isPresent() && !conflictResolutionPrefix.isBlank()) {
                args.add("--parchment-conflict-prefix");
                args.add(conflictResolutionPrefix);
            }
        }

        if (!getEnableCache().get()) {
            args.add("--disable-cache");
        }

        if (getAnalyzeCacheMisses().get()) {
            args.add("--analyze-cache-misses");
        }

        if (getUseEclipseCompiler().get()) {
            args.add("--use-eclipse-compiler");
        }

        // Note that it is possible to specify both
        if (getNeoForgeArtifact().isPresent()) {
            Collections.addAll(args, "--neoforge", getNeoForgeArtifact().get());
        }
        if (getNeoFormArtifact().isPresent()) {
            Collections.addAll(args, "--neoform", getNeoFormArtifact().get());
        }
        if (!getNeoFormArtifact().isPresent() && !getNeoForgeArtifact().isPresent()) {
            throw new GradleException("You need to specify at least 'version' or 'neoFormVersion' in the 'neoForge' block of your build script.");
        }

        Collections.addAll(args, "--dist", "joined");

        List<RequestedResult> requestedResults = new ArrayList<>();

        // Add the generic requested results
        getAdditionalResults().get().forEach((name, file) -> requestedResults.add(new RequestedResult(name, file)));

        if (getResourcesArtifact().isPresent()) {
            requestedResults.add(new RequestedResult("clientResources", getResourcesArtifact().get().getAsFile()));
        }

        // NOTE: When we use NeoForm standalone, the result-ids also change, a.k.a. "Vanilla Mode"
        if (getNeoForgeArtifact().isPresent()) {
            if (getCompiledArtifact().isPresent()) {
                requestedResults.add(new RequestedResult("compiledWithNeoForge", getCompiledArtifact().get().getAsFile()));
            }
            if (getSourcesArtifact().isPresent()) {
                requestedResults.add(new RequestedResult("sourcesWithNeoForge", getSourcesArtifact().get().getAsFile()));
            }
            if (getCompiledWithSourcesArtifact().isPresent()) {
                requestedResults.add(new RequestedResult("sourcesAndCompiledWithNeoForge", getCompiledWithSourcesArtifact().get().getAsFile()));
            }
        } else {
            if (getCompiledArtifact().isPresent()) {
                requestedResults.add(new RequestedResult("compiled", getCompiledArtifact().get().getAsFile()));
            }
            if (getResourcesArtifact().isPresent()) {
                requestedResults.add(new RequestedResult("sources", getSourcesArtifact().get().getAsFile()));
            }
            if (getCompiledWithSourcesArtifact().isPresent()) {
                requestedResults.add(new RequestedResult("sourcesAndCompiled", getCompiledWithSourcesArtifact().get().getAsFile()));
            }
        }

        // Request that NFRT write all these results where we want them to be written to
        for (var requestedResult : requestedResults) {
            args.add("--write-result");
            args.add(requestedResult.id() + ":" + requestedResult.destination().getAbsolutePath());
        }

        var problemsReport = new File(getTemporaryDir(), "nfrt-problem-report.json");
        args.add("--problems-report");
        args.add(problemsReport.getAbsolutePath());

        try {
            run(args);
        } finally {
            reportProblems(problemsReport);
        }
    }

    private void reportProblems(File problemsReport) {
        if (!problemsReport.exists()) {
            return; // Not created -> nothing to report
        }

        List<Problem> problems;
        try {
            problems = FileProblemReporter.loadRecords(problemsReport.toPath());
        } catch (IOException e) {
            getLogger().warn("Failed to load NFRT problems report from {}", problemsReport, e);
            return;
        }

        for (Problem problem : problems) {
            ProblemReportingUtil.report(getProblems(), problem);
        }
    }

    record RequestedResult(String id, File destination) {}
}
