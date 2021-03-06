package ru.spark.slauncher.mod.server;

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import ru.spark.slauncher.download.DefaultDependencyManager;
import ru.spark.slauncher.game.DefaultGameRepository;
import ru.spark.slauncher.mod.ModpackConfiguration;
import ru.spark.slauncher.task.FileDownloadTask;
import ru.spark.slauncher.task.GetTask;
import ru.spark.slauncher.task.Task;
import ru.spark.slauncher.util.DigestUtils;
import ru.spark.slauncher.util.Hex;
import ru.spark.slauncher.util.Logging;
import ru.spark.slauncher.util.StringUtils;
import ru.spark.slauncher.util.gson.JsonUtils;
import ru.spark.slauncher.util.io.FileUtils;
import ru.spark.slauncher.util.io.NetworkUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class ServerModpackCompletionTask extends Task<Void> {

    private final DefaultGameRepository repository;
    private final String version;
    private ModpackConfiguration<ServerModpackManifest> manifest;
    private GetTask dependent;
    private ServerModpackManifest remoteManifest;
    private final List<Task<?>> dependencies = new LinkedList<>();

    public ServerModpackCompletionTask(DefaultDependencyManager dependencyManager, String version) {
        this(dependencyManager, version, null);
    }

    public ServerModpackCompletionTask(DefaultDependencyManager dependencyManager, String version, ModpackConfiguration<ServerModpackManifest> manifest) {
        this.repository = dependencyManager.getGameRepository();
        this.version = version;

        if (manifest == null) {
            try {
                File manifestFile = repository.getModpackConfiguration(version);
                if (manifestFile.exists()) {
                    this.manifest = JsonUtils.GSON.fromJson(FileUtils.readText(manifestFile), new TypeToken<ModpackConfiguration<ServerModpackManifest>>() {
                    }.getType());
                }
            } catch (Exception e) {
                Logging.LOG.log(Level.WARNING, "Unable to read CurseForge modpack manifest.json", e);
            }
        } else {
            this.manifest = manifest;
        }
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() throws Exception {
        if (manifest == null || StringUtils.isBlank(manifest.getManifest().getFileApi())) return;
        dependent = new GetTask(new URL(manifest.getManifest().getFileApi() + "/server-manifest.json"));
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return dependent == null ? Collections.emptySet() : Collections.singleton(dependent);
    }

    @Override
    public void execute() throws Exception {
        if (manifest == null || StringUtils.isBlank(manifest.getManifest().getFileApi())) return;

        try {
            remoteManifest = JsonUtils.fromNonNullJson(dependent.getResult(), ServerModpackManifest.class);
        } catch (JsonParseException e) {
            throw new IOException(e);
        }

        Path rootPath = repository.getVersionRoot(version).toPath();
        Map<String, ModpackConfiguration.FileInformation> files = manifest.getManifest().getFiles().stream()
                .collect(Collectors.toMap(ModpackConfiguration.FileInformation::getPath,
                        Function.identity()));

        Set<String> remoteFiles = remoteManifest.getFiles().stream().map(ModpackConfiguration.FileInformation::getPath)
                .collect(Collectors.toSet());

        // for files in new modpack
        for (ModpackConfiguration.FileInformation file : remoteManifest.getFiles()) {
            Path actualPath = rootPath.resolve(file.getPath());
            boolean download;
            if (!files.containsKey(file.getPath())) {
                // If old modpack does not have this entry, download it
                download = true;
            } else if (!Files.exists(actualPath)) {
                // If both old and new modpacks have this entry, but the file is missing...
                // Re-download it since network problem may cause file missing
                download = true;
            } else {
                // If user modified this entry file, we will not replace this file since this modified file is that user expects.
                String fileHash = Hex.encodeHex(DigestUtils.digest("SHA-1", actualPath));
                String oldHash = files.get(file.getPath()).getHash();
                download = !Objects.equals(oldHash, file.getHash()) && Objects.equals(oldHash, fileHash);
            }

            if (download) {
                dependencies.add(new FileDownloadTask(
                        new URL(remoteManifest.getFileApi() + "/overrides/" + NetworkUtils.encodeLocation(file.getPath())),
                        actualPath.toFile(),
                        new FileDownloadTask.IntegrityCheck("SHA-1", file.getHash())));
            }
        }

        // If old modpack have this entry, and new modpack deleted it. Delete this file.
        for (ModpackConfiguration.FileInformation file : manifest.getManifest().getFiles()) {
            Path actualPath = rootPath.resolve(file.getPath());
            if (Files.exists(actualPath) && !remoteFiles.contains(file.getPath()))
                Files.deleteIfExists(actualPath);
        }
    }

    @Override
    public boolean doPostExecute() {
        return true;
    }

    @Override
    public void postExecute() throws Exception {
        if (manifest == null || StringUtils.isBlank(manifest.getManifest().getFileApi())) return;
        File manifestFile = repository.getModpackConfiguration(version);
        FileUtils.writeText(manifestFile, JsonUtils.GSON.toJson(new ModpackConfiguration<>(remoteManifest, this.manifest.getType(), remoteManifest.getFiles())));
    }
}
