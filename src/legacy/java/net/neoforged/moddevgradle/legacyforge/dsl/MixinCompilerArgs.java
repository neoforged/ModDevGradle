package net.neoforged.moddevgradle.legacyforge.dsl;

import java.util.List;
import javax.inject.Inject;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.CommandLineArgumentProvider;

abstract class MixinCompilerArgs implements CommandLineArgumentProvider {
    @Inject
    public MixinCompilerArgs() {}

    @OutputFile
    protected abstract RegularFileProperty getOutMappings();

    @OutputFile
    protected abstract RegularFileProperty getRefmap();

    /**
     * {@return official -> SRG TSRGv2 mappings file of the game}
     */
    @InputFile
    @PathSensitive(PathSensitivity.NAME_ONLY)
    protected abstract RegularFileProperty getInMappings();

    @Override
    public Iterable<String> asArguments() {
        return List.of(
                "-AreobfTsrgFile=" + getInMappings().get().getAsFile().getAbsolutePath(),
                "-AoutTsrgFile=" + getOutMappings().get().getAsFile().getAbsolutePath(),
                "-AoutRefMapFile=" + getRefmap().get().getAsFile().getAbsolutePath(),
                "-AmappingTypes=tsrg",
                "-ApluginVersion=0.7", // Not sure what this is used for, but MixinGradle gives it to the AP. Latest as of time of writing
                "-AdefaultObfuscationEnv=searge");
    }
}
