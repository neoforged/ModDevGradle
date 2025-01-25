package net.neoforged.moddevgradle.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;

public abstract class CopyDataFile extends DefaultTask {
    @InputFiles
    public abstract ListProperty<RegularFile> getInputFiles();

    @OutputFiles
    public abstract ListProperty<RegularFile> getOutputFiles();

    @TaskAction
    public void doCopy() throws IOException {
        var inputs = getInputFiles().get();
        var outputs = getOutputFiles().get();
        if (inputs.size() != outputs.size()) throw new RuntimeException("Lists length dont match.");

        for (int i = 0; i < inputs.size(); i++) {
            var in = inputs.get(i).getAsFile().toPath();
            var out = outputs.get(i).getAsFile().toPath();
            Files.createDirectories(out.getParent());
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
