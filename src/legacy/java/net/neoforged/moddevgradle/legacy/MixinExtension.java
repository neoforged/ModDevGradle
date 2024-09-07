package net.neoforged.moddevgradle.legacy;

import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.tasks.Jar;
import org.gradle.process.CommandLineArgumentProvider;

import javax.inject.Inject;
import java.util.List;

public abstract class MixinExtension {
    private final Project project;
    private final Provider<RegularFile> srgToOfficial;

    @Inject
    protected MixinExtension(Project project, Provider<RegularFile> srgToOfficial) {
        this.project = project;
        this.srgToOfficial = srgToOfficial;
    }

    public abstract ListProperty<String> getConfigs();

    protected abstract ConfigurableFileCollection getExtraMappingFiles();

    public void config(String name) {
        getConfigs().add(name);
    }

    public void add(SourceSet sourceSet, String refmap) {
        var mappingFile = project.getLayout().getBuildDirectory().dir("mixin").map(d -> d.file(refmap + ".mappings.tsrg"));
        var refMapFile = project.getLayout().getBuildDirectory().dir("mixin").map(d -> d.file(refmap));

        var compilerArgs = project.getObjects().newInstance(MixinCompilerArgs.class);
        compilerArgs.getRefmap().set(refMapFile);
        compilerArgs.getOutMappings().set(mappingFile);
        compilerArgs.getInMappings().set(srgToOfficial);

        getExtraMappingFiles().from(mappingFile);

        project.getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class).configure(compile -> {
            compile.getOptions().getCompilerArgumentProviders().add(compilerArgs);
        });

        // We use matching because there's no guarantee each sourceset will have a jar task
        project.getTasks().withType(Jar.class).matching(jar -> jar.getName().equals(sourceSet.getJarTaskName())).configureEach(jar -> {
            jar.from(refMapFile);
        });
    }
}

abstract class MixinCompilerArgs implements CommandLineArgumentProvider {
    @OutputFile
    protected abstract RegularFileProperty getOutMappings();

    @OutputFile
    protected abstract RegularFileProperty getRefmap();

    /**
     * {@return SRG -> official TSRGv1 mappings file of the game}
     */
    @InputFile
    protected abstract RegularFileProperty getInMappings();

    @Override
    public Iterable<String> asArguments() {
        return List.of(
                "-AreobfTsrgFile=" + getInMappings().get().getAsFile().getAbsolutePath(),
                "-AAoutTsrgFile=" + getOutMappings().get().getAsFile().getAbsolutePath(),
                "-AoutRefMapFile=" + getRefmap().get().getAsFile().getAbsolutePath(),
                "-AmappingTypes=tsrg",
                "-ApluginVersion=0.7.38" // Not sure what this is used for, but MixinGradle gives it to the AP. Latest as of time of writing
        );
    }
}
