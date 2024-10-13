package net.neoforged.moddevgradle.internal;

import net.neoforged.moddevgradle.dsl.RunModel;
import org.gradle.api.Project;

public class LegacyInternal {
    public static void configureRun(Project project, RunModel run) {
        run.getEnvironment().put("MOD_CLASSES", RunUtils.getGradleModFoldersProvider(project, run.getLoadedMods(), null).getClassesArgument());
    }
}
