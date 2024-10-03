package net.neoforged.nfrtgradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.attributes.Bundling;

/**
 * Applies basic configuration for NFRT tasks.
 */
public class NeoFormRuntimePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        var extension = project.getExtensions().create(NeoFormRuntimeExtension.NAME, NeoFormRuntimeExtension.class);
        var configurations = project.getConfigurations();
        var dependencyFactory = project.getDependencyFactory();

        var nfrtDependency = extension.getVersion().map(version -> dependencyFactory.create("net.neoforged:neoform-runtime:" + version));

        var toolConfiguration = configurations.create("neoFormRuntimeTool", spec -> {
            spec.setDescription("The NeoFormRuntime CLI tool");
            spec.setCanBeConsumed(false);
            spec.setCanBeResolved(true);
            spec.defaultDependencies(dependencies -> {
                dependencies.addLater(nfrtDependency.map(dependency -> dependency.copy().attributes(attributes -> {
                    attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, project.getObjects().named(Bundling.class, Bundling.SHADOWED));
                })));
            });
        });

        var externalToolsConfiguration = configurations.create("neoFormRuntimeExternalTools", spec -> {
            spec.setDescription("The external tools used by NeoFormRuntime");
            spec.setCanBeConsumed(false);
            spec.setCanBeResolved(true);
            spec.getDependencies().addLater(nfrtDependency.map(dep -> dep.copy().capabilities(caps -> {
                caps.requireCapability("net.neoforged:neoform-runtime-external-tools");
            })));
        });

        project.getTasks().withType(NeoFormRuntimeTask.class).configureEach(task -> {
            task.getNeoFormRuntime().convention(toolConfiguration);
            task.getVerbose().convention(extension.getVerbose());
            // Every invocation of NFRT should inherit the tools it's using itself via Gradle
            task.addArtifactsToManifest(externalToolsConfiguration);
        });

        project.getTasks().withType(CreateMinecraftArtifacts.class).configureEach(task -> {
            task.getEnableCache().set(extension.getEnableCache());
            task.getAnalyzeCacheMisses().set(extension.getAnalyzeCacheMisses());
            task.getUseEclipseCompiler().set(extension.getUseEclipseCompiler());
        });
    }
}
