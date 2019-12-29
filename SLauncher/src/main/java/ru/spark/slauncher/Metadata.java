package ru.spark.slauncher;

import ru.spark.slauncher.util.platform.OperatingSystem;

import java.nio.file.Path;

/**
 * Stores metadata about this application.
 */
public final class Metadata {
    public static final String VERSION = "3.2.4";
    public static final String NAME = "SLauncher";
    public static final String TITLE = NAME + " " + VERSION;
    public static final String UPDATE_URL = System.getProperty("slauncher.update_source.override", "http://update.slauncher.ru/update_link");
    public static final String CONTACT_URL = "https://vk.com/slauncher";
    public static final String HELP_URL = "https://vk.me/slauncher";
    public static final String CHANGELOG_URL = "https://slauncher.ru/changelog";
    public static final String PUBLISH_URL = "https://slauncher.ru/";
    public static final Path MINECRAFT_DIRECTORY = OperatingSystem.getWorkingDirectory("minecraft");
    public static final Path SLauncher_DIRECTORY = OperatingSystem.getWorkingDirectory("SLauncher");

    private Metadata() {
    }
}
