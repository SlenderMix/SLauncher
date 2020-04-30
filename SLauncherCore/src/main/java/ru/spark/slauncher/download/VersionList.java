package ru.spark.slauncher.download;

import ru.spark.slauncher.task.Task;
import ru.spark.slauncher.util.SimpleMultimap;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The remote version list.
 *
 * @param <T> The subclass of {@code RemoteVersion}, the type of RemoteVersion.
 * @author spark1337
 */
public abstract class VersionList<T extends RemoteVersion> {

    /**
     * the remote version list.
     * key: game version.
     * values: corresponding remote versions.
     */
    protected final SimpleMultimap<String, T> versions = new SimpleMultimap<String, T>(HashMap::new, TreeSet::new);

    /**
     * True if the version list has been loaded.
     */
    public boolean isLoaded() {
        return !versions.isEmpty();
    }

    /**
     * True if the version list that contains the remote versions which depends on the specific game version has been loaded.
     *
     * @param gameVersion the remote version depends on
     */
    public boolean isLoaded(String gameVersion) {
        return !versions.get(gameVersion).isEmpty();
    }

    public abstract boolean hasType();

    protected final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * @return the task to reload the remote version list.
     */
    public abstract Task<?> refreshAsync();

    /**
     * @param gameVersion the remote version depends on
     * @return the task to reload the remote version list.
     */
    public Task<?> refreshAsync(String gameVersion) {
        return refreshAsync();
    }

    public Task<?> loadAsync() {
        return Task.composeAsync(() -> {
            lock.readLock().lock();
            boolean loaded;

            try {
                loaded = isLoaded();
            } finally {
                lock.readLock().unlock();
            }
            return loaded ? null : refreshAsync();
        });
    }

    public Task<?> loadAsync(String gameVersion) {
        return Task.composeAsync(() -> {
            lock.readLock().lock();
            boolean loaded;

            try {
                loaded = isLoaded(gameVersion);
            } finally {
                lock.readLock().unlock();
            }
            return loaded ? null : refreshAsync(gameVersion);
        });
    }

    protected Collection<T> getVersionsImpl(String gameVersion) {
        return versions.get(gameVersion);
    }

    /**
     * Get the remote versions that specifics Minecraft version.
     *
     * @param gameVersion the Minecraft version that remote versions belong to
     * @return the collection of specific remote versions
     */
    public final Collection<T> getVersions(String gameVersion) {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableCollection(new ArrayList<>(getVersionsImpl(gameVersion)));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get the specific remote version.
     *
     * @param gameVersion   the Minecraft version that remote versions belong to
     * @param remoteVersion the version of the remote version.
     * @return the specific remote version, null if it is not found.
     */
    public Optional<T> getVersion(String gameVersion, String remoteVersion) {
        lock.readLock().lock();
        try {
            T result = null;
            for (T it : versions.get(gameVersion))
                if (remoteVersion.equals(it.getSelfVersion()))
                    result = it;
            return Optional.ofNullable(result);
        } finally {
            lock.readLock().unlock();
        }
    }
}
