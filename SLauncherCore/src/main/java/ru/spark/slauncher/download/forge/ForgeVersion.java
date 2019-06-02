package ru.spark.slauncher.download.forge;

import com.google.gson.JsonParseException;
import ru.spark.slauncher.util.Immutable;
import ru.spark.slauncher.util.gson.Validation;

/**
 * @author Spark1337
 */
@Immutable
public final class ForgeVersion implements Validation {

    private final String branch;
    private final String mcversion;
    private final String jobver;
    private final String version;
    private final int build;
    private final double modified;
    private final String[][] files;

    /**
     * No-arg constructor for Gson.
     */
    @SuppressWarnings("unused")
    public ForgeVersion() {
        this(null, null, null, null, 0, 0, null);
    }

    public ForgeVersion(String branch, String mcversion, String jobver, String version, int build, double modified, String[][] files) {
        this.branch = branch;
        this.mcversion = mcversion;
        this.jobver = jobver;
        this.version = version;
        this.build = build;
        this.modified = modified;
        this.files = files;
    }

    public String getBranch() {
        return branch;
    }

    public String getGameVersion() {
        return mcversion;
    }

    public String getJobver() {
        return jobver;
    }

    public String getVersion() {
        return version;
    }

    public int getBuild() {
        return build;
    }

    public double getModified() {
        return modified;
    }

    public String[][] getFiles() {
        return files;
    }

    @Override
    public void validate() throws JsonParseException {
        if (files == null)
            throw new JsonParseException("ForgeVersion files cannot be null");
        if (version == null)
            throw new JsonParseException("ForgeVersion version cannot be null");
        if (mcversion == null)
            throw new JsonParseException("ForgeVersion mcversion cannot be null");
    }

}
