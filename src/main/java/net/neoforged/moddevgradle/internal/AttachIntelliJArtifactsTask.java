package net.neoforged.moddevgradle.internal;

import com.google.gson.Gson;
import groovy.namespace.QName;
import groovy.util.Node;
import groovy.util.NodeList;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.xml.XmlTransformer;
import org.jetbrains.gradle.ext.IdeaLayoutJson;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.Objects;

abstract class AttachIntelliJArtifactsTask extends DefaultTask {
    @Inject
    public AttachIntelliJArtifactsTask() {}

    @Optional
    @InputFile
    public abstract RegularFileProperty getLayoutFile();

    @Nested
    public abstract ListProperty<Entry> getEntries();

    @TaskAction
    public void exec() throws Exception {
        var model = new Gson().fromJson(Files.readString(getLayoutFile().get().getAsFile().toPath()), IdeaLayoutJson.class);

        for (var entry : getEntries().get()) {
            var classesLocation = entry.getClassesLocation().get().getAsFile();
            var sourcesLocation = entry.getSourcesLocation().get().getAsFile();

            for (String srcSet : entry.getSourceSets().get()) {
                var location = model.modulesMap.get(srcSet);
                if (location != null) {
                    var file = new File(location);
                    if (file.exists()) {
                        var trans = new XmlTransformer();
                        trans.addAction(xml -> {
                            System.out.println("hi!!!");
                            var node = xml.asNode();
                            var moduleRoot = (Node) node.getAt(QName.valueOf("component")).stream()
                                    .filter(p -> Objects.equals(((Node) p).get("@name"), "NewModuleRootManager"))
                                    .findFirst()
                                    .orElse(null);
                            if (moduleRoot != null) {
                                var entries = moduleRoot.getAt(QName.valueOf("orderEntry"));
                                for (var e : entries) {
                                    if (e instanceof Node enode && Objects.equals(enode.get("@type"), "module-library")) {
                                        var lib = getNode(enode, "library");
                                        var classes = getNode(lib, "CLASSES");
                                        if (classes != null) {
                                            var root = getNode(classes, "root");
                                            if (root != null) {
                                                var url = root.get("@url");
                                                if (url != null) {
                                                    System.out.println(url + " vs " + classesLocation.getName());
                                                    if (url.toString().endsWith(classesLocation.getName() + "!/")) {
                                                        var sources = getNode(lib, "SOURCES");
                                                        if (sources != null) {
                                                            root = getNode(sources, "root");
                                                            if (root != null) {
                                                                url = root.get("@url");
                                                                if (url.toString().endsWith(sourcesLocation.getName() + "!/")) {
                                                                    System.out.println("ok fine: " + sources);
                                                                    return;
                                                                }
                                                            }
                                                        } else {
                                                            sources = lib.appendNode("SOURCES");
                                                        }
                                                        sources.appendNode("root", Map.of("url", "jar://" + sourcesLocation.getAbsolutePath() + "!/"));
                                                        System.out.println("appending");
                                                        return;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                var lib = moduleRoot.appendNode("orderEntry", Map.of("type", "module-library"))
                                        .appendNode("library");
                                lib.appendNode("CLASSES").appendNode("root", Map.of("url", "jar://" + classesLocation.getAbsolutePath() + "!/"));
                                lib.appendNode("SOURCES").appendNode("root", Map.of("url", "jar://" + sourcesLocation.getAbsolutePath() + "!/"));
                                System.out.println("appended");
                                return;
                            }

                            var lib = node.appendNode("component", Map.of("name", "NewModuleRootManager")).appendNode("orderEntry", Map.of("type", "module-library"))
                                    .appendNode("library");
                            lib.appendNode("CLASSES").appendNode("root", Map.of("url", "jar://" + classesLocation.getAbsolutePath() + "!/"));
                            lib.appendNode("SOURCES").appendNode("root", Map.of("url", "jar://" + sourcesLocation.getAbsolutePath() + "!/"));
                        });
                        var text = Files.readString(file.toPath());
                        try (var out = Files.newOutputStream(file.toPath())) {
                            trans.transform(text, out);
                        }
                    }
                }
            }
        }

        Files.delete(getLayoutFile().get().getAsFile().toPath());
    }

    private groovy.util.Node getNode(groovy.util.Node node, String name) {
        var list = (NodeList) node.get(name);
        return list.isEmpty() ? null : (Node) list.get(0);
    }

    static class Entry {
        private final ListProperty<String> sourceSets;
        private final RegularFileProperty classes, sources;

        public Entry(ObjectFactory factory) {
            this.sourceSets = factory.listProperty(String.class);
            this.classes = factory.fileProperty();
            this.sources = factory.fileProperty();
        }

        @Input
        public ListProperty<String> getSourceSets() {
            return sourceSets;
        }

        @InputFile
        public RegularFileProperty getClassesLocation() {
            return classes;
        }

        @InputFile
        public RegularFileProperty getSourcesLocation() {
            return sources;
        }
    }
}
