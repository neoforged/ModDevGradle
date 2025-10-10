package net.neoforged.moddevgradle.internal;

import java.util.Set;
import java.util.function.Consumer;
import net.neoforged.moddevgradle.dsl.ModModel;
import net.neoforged.moddevgradle.dsl.RunModel;
import net.neoforged.moddevgradle.internal.utils.VersionCapabilitiesInternal;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

/**
 * Internal API provided to the NeoForge development build scripts.
 * <strong>This is NOT API for normal mod development projects!</strong>
 * <p>
 * This allows us to a) make sure NeoDev doesn't use internals it's not supposed to and
 * b) evolve the internal API while not having to modify NeoDev.
 */
public final class NeoDevFacade {
    private NeoDevFacade() {}

    public static void setupRuns(Project project,
            Provider<Directory> argFileDir,
            DomainObjectCollection<RunModel> runs,
            Object runTemplatesSourceFile,
            Consumer<Configuration> configureModulePath,
            Consumer<Configuration> configureAdditionalClasspath,
            Provider<RegularFile> assetPropertiesFile,
            Provider<String> neoFormVersion) {
        ModDevRunWorkflow.setupRuns(
                project,
                Branding.NEODEV,
                argFileDir,
                runs,
                runTemplatesSourceFile,
                configureModulePath,
                configureAdditionalClasspath,
                assetPropertiesFile,
                neoFormVersion.map(VersionCapabilitiesInternal::ofNeoFormVersion).getOrElse(VersionCapabilitiesInternal.latest()));
    }

    public static void setupTestTask(Project project,
            Provider<Directory> argFileDir,
            TaskProvider<Test> testTask,
            Object runTemplatesSourceFile,
            Provider<Set<ModModel>> loadedMods,
            Provider<ModModel> testedMod,
            Consumer<Configuration> configureModulePath,
            Consumer<Configuration> configureAdditionalClasspath,
            Provider<RegularFile> assetPropertiesFile,
            Provider<String> neoFormVersion) {
        ModDevRunWorkflow.setupTestTask(
                project,
                Branding.NEODEV,
                runTemplatesSourceFile,
                testTask,
                loadedMods,
                testedMod,
                argFileDir,
                configureModulePath,
                configureAdditionalClasspath,
                assetPropertiesFile,
                neoFormVersion.map(VersionCapabilitiesInternal::ofNeoFormVersion).getOrElse(VersionCapabilitiesInternal.latest()));
    }

    public static void runTaskOnProjectSync(Project project, Object task) {
        IdeIntegration.of(project, Branding.NEODEV).runTaskOnProjectSync(task);
    }
}
