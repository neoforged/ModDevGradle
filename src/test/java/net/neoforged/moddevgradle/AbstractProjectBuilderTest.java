package net.neoforged.moddevgradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractListAssert;
import org.assertj.core.api.ObjectAssert;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.FileCollectionDependency;

public abstract class AbstractProjectBuilderTest {
    protected Project project;

    protected final AbstractListAssert<?, List<? extends String>, String, ObjectAssert<String>> assertThatDependencies(String configurationName) {
        var configuration = project.getConfigurations().getByName(configurationName);
        return assertThat(configuration.getAllDependencies())
                .extracting(this::describeDependency);
    }

    protected final String describeDependency(Dependency dependency) {
        String result;
        if (dependency instanceof FileCollectionDependency fileCollectionDependency) {
            result = fileCollectionDependency.getFiles().getFiles()
                    .stream()
                    .map(f -> project.getProjectDir().toPath().relativize(f.toPath()).toString().replace('\\', '/'))
                    .collect(Collectors.joining(";"));
        } else if (dependency instanceof ExternalModuleDependency moduleDependency) {
            result = moduleDependency.getGroup()
                    + ":" + moduleDependency.getName()
                    + ":" + moduleDependency.getVersion()
                    + formatCapabilities(moduleDependency);
        } else {
            result = dependency.toString();
        }

        if (dependency.getReason() != null) {
            result += " (" + dependency.getReason() + ")";
        }
        return result;
    }

    protected final String formatCapabilities(ExternalModuleDependency moduleDependency) {
        var capabilities = moduleDependency.getRequestedCapabilities();
        if (capabilities.isEmpty()) {
            return "";
        }

        var mainVersion = moduleDependency.getVersion();
        return "[" +
                capabilities.stream().map(cap -> {
                    if (Objects.equals(mainVersion, cap.getVersion()) || cap.getVersion() == null) {
                        return cap.getGroup() + ":" + cap.getName();
                    } else {
                        return cap.getGroup() + ":" + cap.getName() + ":" + cap.getVersion();
                    }
                }).collect(Collectors.joining(",")) + "]";
    }
}
