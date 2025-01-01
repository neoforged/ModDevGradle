package net.neoforged.moddevgradle.legacyforge.dsl;

import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.tasks.Jar;

public abstract class MixinExtension {
    private final Project project;
    private final Provider<RegularFile> officialToSrg;
    private final ConfigurableFileCollection extraMappingFiles;

    @Inject
    public MixinExtension(Project project,
            Provider<RegularFile> officialToSrg,
            ConfigurableFileCollection extraMappingFiles) {
        this.project = project;
        this.officialToSrg = officialToSrg;
        this.extraMappingFiles = extraMappingFiles;
    }

    public abstract ListProperty<String> getConfigs();

    public void config(String name) {
        getConfigs().add(name);
    }

    public Provider<RegularFile> add(SourceSet sourceSet, String refmap) {
        var mappingFile = project.getLayout().getBuildDirectory().dir("mixin").map(d -> d.file(refmap + ".mappings.tsrg"));
        var refMapFile = project.getLayout().getBuildDirectory().dir("mixin").map(d -> d.file(refmap));

        var compilerArgs = project.getObjects().newInstance(MixinCompilerArgs.class);
        compilerArgs.getRefmap().set(refMapFile);
        compilerArgs.getOutMappings().set(mappingFile);
        compilerArgs.getInMappings().set(officialToSrg);

        extraMappingFiles.from(mappingFile);

        project.getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class).configure(compile -> {
            compile.getOptions().getCompilerArgumentProviders().add(compilerArgs);
        });

        // We use matching because there's no guarantee each sourceset will have a jar task
        project.getTasks().withType(Jar.class).matching(jar -> jar.getName().equals(sourceSet.getJarTaskName())).configureEach(jar -> {
            jar.from(refMapFile);
        });

        return refMapFile;
    }
}
