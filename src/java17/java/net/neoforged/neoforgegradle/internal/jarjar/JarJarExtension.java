package net.neoforged.neoforgegradle.internal.jarjar;

import net.neoforged.neoforgegradle.dsl.JarJar;
import net.neoforged.neoforgegradle.dsl.JarJarFeature;
import org.gradle.api.Project;
import org.gradle.api.attributes.Attribute;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

public class JarJarExtension extends DefaultJarJarFeature implements JarJar {
    public static final Attribute<String> JAR_JAR_RANGE_ATTRIBUTE = Attribute.of("jarJarRange", String.class);

    private final Map<String, DefaultJarJarFeature> features = new HashMap<>();

    @Inject
    public JarJarExtension(final Project project) {
        super(project, "");
        features.put("", this);
    }

    public JarJarFeature forFeature(String featureName) {
        if (featureName == null || featureName.isEmpty()) {
            return this;
        }
        return features.computeIfAbsent(featureName, f -> {
            DefaultJarJarFeature feature = new DefaultJarJarFeature(project, f);
            feature.createTaskAndConfiguration();
            return feature;
        });
    }
}
