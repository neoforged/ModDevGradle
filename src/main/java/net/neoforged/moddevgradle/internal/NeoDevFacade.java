package net.neoforged.moddevgradle.internal;

import net.neoforged.moddevgradle.dsl.ModModel;
import net.neoforged.moddevgradle.dsl.RunModel;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

import java.util.function.Consumer;

/**
 * Internal API provided to the NeoForge development build scripts.
 * <strong>This is NOT API for normal mod development projects!</strong>
 * <p>
 * This allows us to a) make sure NeoDev doesn't use internals it's not supposed to and
 * b) evolve the internal API while not having to modify NeoDev.
 */
public final class NeoDevFacade {
    private NeoDevFacade() {
    }

    public static void setupRuns(Project project,
                                 Provider<Directory> argFileDir,
                                 DomainObjectCollection<RunModel> runs,
                                 Object runTemplatesSourceFile,
                                 Consumer<Configuration> configureModulePath,
                                 Consumer<Configuration> configureAdditionalClasspath,
                                 Provider<RegularFile> assetPropertiesFile
    ) {
        ModDevPlugin.setupRuns(
                project,
                argFileDir,
                runs,
                runTemplatesSourceFile,
                configureModulePath,
                configureAdditionalClasspath,
                assetPropertiesFile
        );
    }

    public static void setupTestTask(Project project,
                                     Provider<Directory> argFileDir,
                                     TaskProvider<Test> testTask,
                                     Object runTemplatesSourceFile,
                                     SetProperty<ModModel> loadedMods,
                                     Property<ModModel> testedMod,
                                     Consumer<Configuration> configureModulePath,
                                     Consumer<Configuration> configureAdditionalClasspath,
                                     Provider<RegularFile> assetPropertiesFile
    ) {
        ModDevPlugin.setupTestTask(
                project,
                runTemplatesSourceFile,
                testTask,
                loadedMods,
                testedMod,
                argFileDir,
                configureModulePath,
                configureAdditionalClasspath,
                assetPropertiesFile
        );
    }

    public static void runTaskOnProjectSync(Project project, Object task) {
        IdeIntegration.of(project).runTaskOnProjectSync(task);
    }
}
