package net.neoforged.moddevgradle.tasks;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.neoforged.moddevgradle.internal.UserDevConfig;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.jetbrains.annotations.ApiStatus;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipFile;

/**
 * The primary task for creating the Minecraft artifacts that mods will be compiled against,
 * using the NFRT CLI.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public abstract class CreateMinecraftJar extends DefaultTask {
    @Inject
    public CreateMinecraftJar() {
    }

    /**
     * The installertools Jar needed to create the Minecraft jar.
     */
    @Classpath
    public abstract ConfigurableFileCollection getInstallerTools();

    /**
     * Files added to this collection will be applied to the Minecraft jar.
     */
    @InputFiles
    public abstract ConfigurableFileCollection getAccessTransformers();

    /**
     * Interface injection data files to apply to the Minecraft jar.
     */
    @InputFiles
    public abstract ConfigurableFileCollection getInterfaceInjectionData();

    /**
     * This must contain a single file, which has one of the following supported formats:
     * <ul>
     * <li>NeoForge Userdev jar file, containing a Userdev config.json file</li>
     * </ul>
     */
    @Classpath
    public abstract ConfigurableFileCollection getNeoForgeUserDevConfig();

    /**
     * This must contain a single file, which has one of the following supported formats:
     * <ul>
     * <li>NeoForm config.json file.</li>
     * <li>NeoForm data zip file.</li>
     * </ul>
     */
    @Classpath
    public abstract RegularFileProperty getNeoFormConfig();

    /**
     * The Minecraft jar.
     */
    @OutputFile
    public abstract RegularFileProperty getMinecraftJar();

    @Inject
    @ApiStatus.Internal
    protected abstract ExecOperations getExecOperations();

    @TaskAction
    public void createArtifacts() throws IOException {

        File neoFormConfig = getNeoFormConfig().getAsFile().get();

        // Parse the neoFormConfig JSON and extract the minecraft field
        String minecraftVersion;
        try (var neoFormZip = new ZipFile(neoFormConfig)) {
            var configEntry = neoFormZip.getEntry("config.json");
            if (configEntry == null) {
                throw new InvalidUserCodeException("NeoForm config file does not contain a config.json entry.");
            }
            try (var inputStream = neoFormZip.getInputStream(configEntry);
                 var reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                Gson gson = new Gson();
                JsonObject jsonObject = gson.fromJson(reader, JsonObject.class);
                minecraftVersion = jsonObject.get("version").getAsString();
            }
        }

        HttpClient client = HttpClient.newHttpClient();
        Gson gson = new Gson();

        // Download version manifest
        HttpRequest manifestRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
                .GET()
                .build();

        String manifestResponse;
        try {
            manifestResponse = client.send(manifestRequest, HttpResponse.BodyHandlers.ofString()).body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading version manifest", e);
        }

        // Parse manifest and find the matching version
        JsonObject manifest = gson.fromJson(manifestResponse, JsonObject.class);
        JsonArray versions = manifest.getAsJsonArray("versions");

        String versionUrl = null;
        for (var versionElement : versions) {
            JsonObject versionObj = versionElement.getAsJsonObject();
            if (minecraftVersion.equals(versionObj.get("id").getAsString())) {
                versionUrl = versionObj.get("url").getAsString();
                break;
            }
        }

        if (versionUrl == null) {
            throw new InvalidUserCodeException("Could not find Minecraft version " + minecraftVersion + " in version manifest");
        }

        // Download the specific version JSON
        HttpRequest versionRequest = HttpRequest.newBuilder()
                .uri(URI.create(versionUrl))
                .GET()
                .build();

        String versionResponse;
        try {
            versionResponse = client.send(versionRequest, HttpResponse.BodyHandlers.ofString()).body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading version data", e);
        }

        JsonObject versionData = gson.fromJson(versionResponse, JsonObject.class);
        getLogger().info("Downloaded Minecraft {} version data", minecraftVersion);

        var clientUrl = versionData.getAsJsonObject("downloads").getAsJsonObject("client").getAsJsonPrimitive("url").getAsString();
        var clientMappingsUrl = versionData.getAsJsonObject("downloads").getAsJsonObject("client_mappings").getAsJsonPrimitive("url").getAsString();
        var serverUrl = versionData.getAsJsonObject("downloads").getAsJsonObject("server").getAsJsonPrimitive("url").getAsString();

        var clientFile = new File(getTemporaryDir(), "client.jar");
        var clientMappingsFile = new File(getTemporaryDir(), "client_mappings.txt");
        var serverFile = new File(getTemporaryDir(), "server.jar");

        CompletableFuture.allOf(
                client.sendAsync(HttpRequest.newBuilder(URI.create(clientUrl)).build(), HttpResponse.BodyHandlers.ofFile(clientFile.toPath())),
                client.sendAsync(HttpRequest.newBuilder(URI.create(clientMappingsUrl)).build(), HttpResponse.BodyHandlers.ofFile(clientMappingsFile.toPath())),
                client.sendAsync(HttpRequest.newBuilder(URI.create(serverUrl)).build(), HttpResponse.BodyHandlers.ofFile(serverFile.toPath()))
        ).join();

        var args = new ArrayList<String>();
        args.add("--task");
        args.add("PROCESS_MINECRAFT_JAR");

        args.add("--input");
        args.add(clientFile.getAbsolutePath());
        args.add("--input");
        args.add(serverFile.getAbsolutePath());
        args.add("--input-mappings");
        args.add(clientMappingsFile.getAbsolutePath());

        args.add("--output");
        args.add(getMinecraftJar().getAsFile().get().getAbsolutePath());

        // If an AT path is added twice, the validated variant takes precedence
        var accessTransformersAdded = new HashSet<File>();
        for (var accessTransformer : getAccessTransformers().getFiles()) {
            if (accessTransformersAdded.add(accessTransformer)) {
                args.add("--access-transformer");
                args.add(accessTransformer.getAbsolutePath());
            }
        }

        for (var interfaceInjectionFile : getInterfaceInjectionData().getFiles()) {
            args.add("--interface-injection-data");
            args.add(interfaceInjectionFile.getAbsolutePath());
        }

        args.add("--neoform-data");
        args.add(neoFormConfig.getAbsolutePath());

        if (!getNeoForgeUserDevConfig().isEmpty()) {
            try (var zip = new ZipFile(getNeoForgeUserDevConfig().getSingleFile())) {
                var configEntry = zip.getEntry("config.json");
                if (configEntry == null) {
                    throw new InvalidUserCodeException("NeoForge UserDev config file does not contain a config.json entry.");
                }
                try (var inputStream = zip.getInputStream(configEntry)) {
                    var userDevConfig = UserDevConfig.from(inputStream);
                    if (userDevConfig.binpatches() != null) {
                        var binpatches = new File(getTemporaryDir(), "neoforge_binpatches.lzma");
                        try (var binpatchesIn = zip.getInputStream(zip.getEntry(userDevConfig.binpatches()))) {
                            getLogger().info("Extracting NeoForge UserDev binpatches to {}", binpatches.getAbsolutePath());
                            Files.copy(binpatchesIn, binpatches.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        }
                        args.add("--apply-patches");
                        args.add(binpatches.getAbsolutePath());
                    }
                }
            }
        }

        getExecOperations().javaexec(spec -> {
            spec.args(args);
            spec.setClasspath(getInstallerTools());
        });
    }
}
