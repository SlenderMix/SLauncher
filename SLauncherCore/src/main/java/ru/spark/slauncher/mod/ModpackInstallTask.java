package ru.spark.slauncher.mod;

import ru.spark.slauncher.task.Task;
import ru.spark.slauncher.util.DigestUtils;
import ru.spark.slauncher.util.Hex;
import ru.spark.slauncher.util.io.FileUtils;
import ru.spark.slauncher.util.io.Unzipper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Predicate;

public class ModpackInstallTask<T> extends Task<Void> {

    private final File modpackFile;
    private final File dest;
    private final Charset charset;
    private final String subDirectory;
    private final List<ModpackConfiguration.FileInformation> overrides;
    private final Predicate<String> callback;

    /**
     * Constructor
     *
     * @param modpackFile      a zip file
     * @param dest             destination to store unpacked files
     * @param charset          charset of the zip file
     * @param subDirectory     the subdirectory of zip file to unpack
     * @param callback         test whether the file (given full path) in zip file should be unpacked or not
     * @param oldConfiguration old modpack information if upgrade
     */
    public ModpackInstallTask(File modpackFile, File dest, Charset charset, String subDirectory, Predicate<String> callback, ModpackConfiguration<T> oldConfiguration) {
        this.modpackFile = modpackFile;
        this.dest = dest;
        this.charset = charset;
        this.subDirectory = subDirectory;
        this.callback = callback;

        if (oldConfiguration == null)
            overrides = Collections.emptyList();
        else
            overrides = oldConfiguration.getOverrides();
    }

    @Override
    public void execute() throws Exception {
        Set<String> entries = new HashSet<>();
        if (!FileUtils.makeDirectory(dest))
            throw new IOException("Unable to make directory " + dest);

        HashMap<String, ModpackConfiguration.FileInformation> files = new HashMap<>();
        for (ModpackConfiguration.FileInformation file : overrides)
            files.put(file.getPath(), file);

        new Unzipper(modpackFile, dest)
                .setSubDirectory(subDirectory)
                .setTerminateIfSubDirectoryNotExists()
                .setReplaceExistentFile(true)
                .setEncoding(charset)
                .setFilter((destPath, isDirectory, zipEntry, entryPath) -> {
                    if (isDirectory) return true;
                    if (!callback.test(entryPath)) return false;
                    entries.add(entryPath);

                    if (!files.containsKey(entryPath)) {
                        // If old modpack does not have this entry, add this entry or override the file that user added.
                        return true;
                    } else if (!Files.exists(destPath)) {
                        // If both old and new modpacks have this entry, but the file is deleted by user, leave it missing.
                        return false;
                    } else {
                        // If both old and new modpacks have this entry, and user has modified this file,
                        // we will not replace it since this modified file is what user expects.
                        String fileHash = Hex.encodeHex(DigestUtils.digest("SHA-1", destPath));
                        String oldHash = files.get(entryPath).getHash();
                        return Objects.equals(oldHash, fileHash);
                    }
                }).unzip();

        // If old modpack have this entry, and new modpack deleted it. Delete this file.
        for (ModpackConfiguration.FileInformation file : overrides) {
            File original = new File(dest, file.getPath());
            if (original.exists() && !entries.contains(file.getPath()))
                original.delete();
        }
    }
}
