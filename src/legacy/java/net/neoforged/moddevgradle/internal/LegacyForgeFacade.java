package net.neoforged.moddevgradle.internal;

import net.neoforged.moddevgradle.dsl.RunModel;
import org.gradle.api.Project;

public class LegacyForgeFacade {
    public static void configureRun(Project project, RunModel run) {
        // This will explicitly be replaced in RunUtils to make this work for IDEs
        run.getEnvironment().put("MOD_CLASSES", RunUtils.getGradleModFoldersProvider(project, run.getLoadedMods(), null).getClassesArgument());

        // Old BSL versions before 2022 (i.e. on 1.18.2) did not export any packages, causing DevLaunch to be unable to access the main method
        run.getJvmArguments().addAll("--add-exports", "cpw.mods.bootstraplauncher/cpw.mods.bootstraplauncher=ALL-UNNAMED");
    }
}
