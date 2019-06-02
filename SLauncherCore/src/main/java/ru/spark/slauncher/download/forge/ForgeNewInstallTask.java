package ru.spark.slauncher.download.forge;

import ru.spark.slauncher.download.DefaultDependencyManager;
import ru.spark.slauncher.download.game.GameLibrariesTask;
import ru.spark.slauncher.download.optifine.OptiFineInstallTask;
import ru.spark.slauncher.game.*;
import ru.spark.slauncher.task.Task;
import ru.spark.slauncher.task.TaskResult;
import ru.spark.slauncher.util.DigestUtils;
import ru.spark.slauncher.util.Hex;
import ru.spark.slauncher.util.Logging;
import ru.spark.slauncher.util.StringUtils;
import ru.spark.slauncher.util.function.ExceptionalFunction;
import ru.spark.slauncher.util.gson.JsonUtils;
import ru.spark.slauncher.util.io.ChecksumMismatchException;
import ru.spark.slauncher.util.io.CompressingUtils;
import ru.spark.slauncher.util.io.FileUtils;
import ru.spark.slauncher.util.platform.CommandBuilder;
import ru.spark.slauncher.util.platform.JavaVersion;
import ru.spark.slauncher.util.platform.OperatingSystem;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class ForgeNewInstallTask extends TaskResult<Version> {

    private final DefaultDependencyManager dependencyManager;
    private final DefaultGameRepository gameRepository;
    private final Version version;
    private final Path installer;
    private final List<Task> dependents = new LinkedList<>();
    private final List<Task> dependencies = new LinkedList<>();

    private ForgeNewInstallProfile profile;
    private Version forgeVersion;

    public ForgeNewInstallTask(DefaultDependencyManager dependencyManager, Version version, Path installer) {
        this.dependencyManager = dependencyManager;
        this.gameRepository = dependencyManager.getGameRepository();
        this.version = version;
        this.installer = installer;

        setSignificance(TaskSignificance.MINOR);
    }

    private <E extends Exception> String parseLiteral(String literal, Map<String, String> var, ExceptionalFunction<String, String, E> plainConverter) throws E {
        if (StringUtils.isSurrounded(literal, "{", "}"))
            return var.get(StringUtils.removeSurrounding(literal, "{", "}"));
        else if (StringUtils.isSurrounded(literal, "'", "'"))
            return StringUtils.removeSurrounding(literal, "'");
        else if (StringUtils.isSurrounded(literal, "[", "]"))
            return gameRepository.getArtifactFile(version, new Artifact(StringUtils.removeSurrounding(literal, "[", "]"))).toString();
        else
            return plainConverter.apply(literal);
    }

    @Override
    public Collection<Task> getDependents() {
        return dependents;
    }

    @Override
    public List<Task> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() throws Exception {
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(installer)) {
            profile = JsonUtils.fromNonNullJson(FileUtils.readText(fs.getPath("install_profile.json")), ForgeNewInstallProfile.class);
            forgeVersion = JsonUtils.fromNonNullJson(FileUtils.readText(fs.getPath(profile.getJson())), Version.class);

            for (Library library : profile.getLibraries()) {
                Path file = fs.getPath("maven").resolve(library.getPath());
                if (Files.exists(file)) {
                    Path dest = gameRepository.getLibraryFile(version, library).toPath();
                    FileUtils.copyFile(file, dest);
                }
            }

            {
                Path mainJar = profile.getPath().getPath(fs.getPath("maven"));
                if (Files.exists(mainJar)) {
                    Path dest = gameRepository.getArtifactFile(version, profile.getPath());
                    FileUtils.copyFile(mainJar, dest);
                }
            }
        }

        dependents.add(new GameLibrariesTask(dependencyManager, version, profile.getLibraries()));
    }

    @Override
    public void execute() throws Exception {
        if ("net.minecraft.launchwrapper.Launch".equals(version.getMainClass()))
            throw new OptiFineInstallTask.UnsupportedOptiFineInstallationException();

        Path temp = Files.createTempDirectory("forge_installer");
        int finished = 0;
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(installer)) {
            List<ForgeNewInstallProfile.Processor> processors = profile.getProcessors();
            Map<String, String> data = profile.getData();

            updateProgress(0, processors.size());

            for (Map.Entry<String, String> entry : data.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                data.put(key, parseLiteral(value,
                        Collections.emptyMap(),
                        str -> {
                            Path dest = temp.resolve(str);
                            FileUtils.copyFile(fs.getPath(str), dest);
                            return dest.toString();
                        }));
            }

            data.put("SIDE", "client");
            data.put("MINECRAFT_JAR", gameRepository.getVersionJar(version).getAbsolutePath());

            for (ForgeNewInstallProfile.Processor processor : processors) {
                Map<String, String> outputs = new HashMap<>();
                boolean miss = false;

                for (Map.Entry<String, String> entry : processor.getOutputs().entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();

                    key = parseLiteral(key, data, ExceptionalFunction.identity());
                    value = parseLiteral(value, data, ExceptionalFunction.identity());

                    if (key == null || value == null) {
                        throw new Exception("Invalid forge installation configuration");
                    }

                    outputs.put(key, value);

                    Path artifact = Paths.get(key);
                    if (Files.exists(artifact)) {
                        String code;
                        try (InputStream stream = Files.newInputStream(artifact)) {
                            code = Hex.encodeHex(DigestUtils.digest("SHA-1", stream));
                        }

                        if (!Objects.equals(code, value)) {
                            Files.delete(artifact);
                            Logging.LOG.info("Found existing file is not valid: " + artifact);

                            miss = true;
                        }
                    } else {
                        miss = true;
                    }
                }

                if (!processor.getOutputs().isEmpty() && !miss) {
                    continue;
                }

                Path jar = gameRepository.getArtifactFile(version, processor.getJar());
                if (!Files.isRegularFile(jar))
                    throw new FileNotFoundException("Game processor file not found, should be downloaded in preprocess");

                String mainClass;
                try (JarFile jarFile = new JarFile(jar.toFile())) {
                    mainClass = jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
                }

                if (StringUtils.isBlank(mainClass))
                    throw new Exception("Game processor jar does not have main class " + jar);

                List<String> command = new ArrayList<>();
                command.add(JavaVersion.fromCurrentEnvironment().getBinary().toString());
                command.add("-cp");

                List<String> classpath = new ArrayList<>(processor.getClasspath().size() + 1);
                for (Artifact artifact : processor.getClasspath()) {
                    Path file = gameRepository.getArtifactFile(version, artifact);
                    if (!Files.isRegularFile(file))
                        throw new Exception("Game processor dependency missing");
                    classpath.add(file.toString());
                }
                classpath.add(jar.toString());
                command.add(String.join(OperatingSystem.PATH_SEPARATOR, classpath));

                command.add(mainClass);

                List<String> args = processor.getArgs().stream().map(arg -> {
                    String parsed = parseLiteral(arg, data, ExceptionalFunction.identity());
                    if (parsed == null)
                        throw new IllegalStateException("Invalid forge installation configuration");
                    return parsed;
                }).collect(Collectors.toList());

                command.addAll(args);

                Logging.LOG.info("Executing external processor " + processor.getJar().toString() + ", command line: " + new CommandBuilder().addAll(command).toString());
                Process process = new ProcessBuilder(command).start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    for (String line; (line = reader.readLine()) != null; ) {
                        System.out.println(line);
                    }
                }
                int exitCode = process.waitFor();
                if (exitCode != 0)
                    throw new IllegalStateException("Game processor exited abnormally");

                for (Map.Entry<String, String> entry : outputs.entrySet()) {
                    Path artifact = Paths.get(entry.getKey());
                    if (!Files.isRegularFile(artifact))
                        throw new FileNotFoundException("File missing: " + artifact);

                    String code;
                    try (InputStream stream = Files.newInputStream(artifact)) {
                        code = Hex.encodeHex(DigestUtils.digest("SHA-1", stream));
                    }

                    if (!Objects.equals(code, entry.getValue())) {
                        Files.delete(artifact);
                        throw new ChecksumMismatchException("SHA-1", entry.getValue(), code);
                    }
                }

                updateProgress(++finished, processors.size());
            }
        }

        // resolve the version
        SimpleVersionProvider provider = new SimpleVersionProvider();
        provider.addVersion(version);

        setResult(forgeVersion
                .setInheritsFrom(version.getId())
                .resolve(provider).setJar(null)
                .setId(version.getId()).setLogging(Collections.emptyMap()));

        dependencies.add(dependencyManager.checkLibraryCompletionAsync(forgeVersion));

        FileUtils.deleteDirectory(temp.toFile());
    }
}
