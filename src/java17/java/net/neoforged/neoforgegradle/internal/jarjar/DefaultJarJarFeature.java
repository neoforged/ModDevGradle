package net.neoforged.neoforgegradle.internal.jarjar;

import net.neoforged.neoforgegradle.dsl.JarJarFeature;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DefaultJarJarFeature implements JarJarFeature {
    public static final String JAR_JAR_TASK_NAME = "jarJar";
    public static final String JAR_JAR_GROUP = "jarjar";
    public static final String JAR_JAR_DEFAULT_CONFIGURATION_NAME = "jarJar";

    protected final Project project;
    protected final String prefix;
    private boolean disabled;
    private boolean enabled;
    private boolean disableDefaultSources;
    private PublishArtifact addedToPublication;
    private final List<PublishArtifact> removedFromPublication = new ArrayList<>();

    @Inject
    public DefaultJarJarFeature(final Project project, final String prefix) {
        this.project = project;
        this.prefix = prefix;
    }

    @Override
    public void enable() {
        if (!this.disabled)
            enable(true);
    }

    private void enable(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        final JarJarTask task = (JarJarTask) project.getTasks().findByPath(withPrefix("jarJar"));
        Configuration runtimeElements = project.getConfigurations().findByName(withPrefix((JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME)));
        if (task != null) {
            if (runtimeElements != null) {
                if (enabled) {
                    removedFromPublication.clear();
                    removedFromPublication.addAll(runtimeElements.getArtifacts());
                    runtimeElements.getArtifacts().clear();
                    project.artifacts(handler ->
                            addedToPublication = handler.add(withPrefix(JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME), task, artifact ->
                                    artifact.builtBy(task)
                            )
                    );
                } else {
                    runtimeElements.getArtifacts().remove(addedToPublication);
                    runtimeElements.getArtifacts().addAll(removedFromPublication);
                }
            }
            if (!task.getEnabled() == enabled) {
                task.setEnabled(enabled);
            }
        }
    }

    @Override
    public void disable() {
        disable(true);
    }

    @Override
    public void disable(boolean disable) {
        this.disabled = disable;
        if (disable) {
            enable(false);
        }
    }

    @Override
    public boolean getDefaultSourcesDisabled() {
        return this.disableDefaultSources;
    }

    @Override
    public void disableDefaultSources() {
        disableDefaultSources(true);
    }

    @Override
    public void disableDefaultSources(boolean value) {
        this.disableDefaultSources = value;
    }

    protected String withPrefix(String name) {
        if (this.prefix.isEmpty()) {
            return name;
        } else {
            return this.prefix + name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
        }
    }

    public void createTaskAndConfiguration() {
        final Configuration configuration = project.getConfigurations().create(withPrefix(JAR_JAR_DEFAULT_CONFIGURATION_NAME));
        configuration.setTransitive(false);
        configuration.getAllDependencies().configureEach(dep ->
                this.enable()
        );
        // jarJar configurations should be resolvable, but ought not to be exposed to consumers;
        // as it has attributes, it could conflict with normal exposed configurations
        configuration.setCanBeResolved(true);
        configuration.setCanBeConsumed(false);

        JavaPluginExtension javaPlugin = project.getExtensions().getByType(JavaPluginExtension.class);

        configuration.attributes(attributes -> {
            // Unfortunately, while we can hopefully rely on disambiguation rules to get us some of these, others run
            // into issues. The target JVM version is the most worrying - we don't want to pull in a variant for a newer
            // jvm version. We could copy DefaultJvmFeature, and search for the target version of the compile task,
            // but this is difficult - we only have a feature name, not the linked source set. For this reason, we use
            // the toolchain version, which is the most likely to be correct.
            attributes.attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaPlugin.getToolchain().getLanguageVersion().map(JavaLanguageVersion::asInt));
            attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.getObjects().named(LibraryElements.class, LibraryElements.JAR));
            attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
            attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, project.getObjects().named(Bundling.class, Bundling.EXTERNAL));
        });

        TaskProvider<JarJarTask> jarJarTask = project.getTasks().register(withPrefix(JAR_JAR_TASK_NAME), JarJarTask.class, jarJar -> {
            jarJar.setGroup(JAR_JAR_GROUP);
            jarJar.setDescription("Create a combined JAR of project and selected dependencies");
            jarJar.getArchiveClassifier().convention(prefix.isEmpty() ? "all" : prefix + "-all");

            if (!this.getDefaultSourcesDisabled()) {
                Jar jarTask = (Jar) project.getTasks().getByName(withPrefix(JavaPlugin.JAR_TASK_NAME));
                jarJar.dependsOn(jarTask);
                jarJar.getManifest().from(jarTask.getManifest());
                jarJar.from(project.zipTree(jarTask.getArchiveFile()).matching(patternFilterable -> {
                    patternFilterable.exclude("META-INF/MANIFEST.MF");
                }));
            }

            jarJar.configuration(configuration);

            jarJar.setEnabled(false);
        });

        project.getTasks().named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME, t -> {
            t.dependsOn(jarJarTask);
        });
    }
}
