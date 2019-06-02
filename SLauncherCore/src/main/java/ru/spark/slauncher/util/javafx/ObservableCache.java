package ru.spark.slauncher.util.javafx;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import ru.spark.slauncher.util.function.ExceptionalFunction;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

/**
 * @author Spark1337
 */
public class ObservableCache<K, V, E extends Exception> {

    private final ExceptionalFunction<K, V, E> source;
    private final BiConsumer<K, Throwable> exceptionHandler;
    private final V fallbackValue;
    private final Executor executor;
    private final ObservableHelper observable = new ObservableHelper();
    private final Map<K, V> cache = new HashMap<>();
    private final Map<K, CompletableFuture<V>> pendings = new HashMap<>();
    private final Map<K, Boolean> invalidated = new HashMap<>();

    public ObservableCache(ExceptionalFunction<K, V, E> source, BiConsumer<K, Throwable> exceptionHandler, V fallbackValue, Executor executor) {
        this.source = source;
        this.exceptionHandler = exceptionHandler;
        this.fallbackValue = fallbackValue;
        this.executor = executor;
    }

    public Optional<V> getImmediately(K key) {
        synchronized (this) {
            return Optional.ofNullable(cache.get(key));
        }
    }

    public void put(K key, V value) {
        synchronized (this) {
            cache.put(key, value);
            invalidated.remove(key);
        }
        Platform.runLater(observable::invalidate);
    }

    private CompletableFuture<V> query(K key, Executor executor) {
        CompletableFuture<V> future;
        synchronized (this) {
            CompletableFuture<V> prev = pendings.get(key);
            if (prev != null) {
                return prev;
            } else {
                future = new CompletableFuture<>();
                pendings.put(key, future);
            }
        }

        executor.execute(() -> {
            V result;
            try {
                result = source.apply(key);
            } catch (Throwable ex) {
                synchronized (this) {
                    pendings.remove(key);
                }
                exceptionHandler.accept(key, ex);
                future.completeExceptionally(ex);
                return;
            }

            synchronized (this) {
                cache.put(key, result);
                invalidated.remove(key);
                pendings.remove(key, future);
            }
            future.complete(result);
            Platform.runLater(observable::invalidate);
        });

        return future;
    }

    public V get(K key) {
        V cached;
        synchronized (this) {
            cached = cache.get(key);
            if (cached != null && !invalidated.containsKey(key)) {
                return cached;
            }
        }

        try {
            return query(key, Runnable::run).join();
        } catch (CompletionException | CancellationException ignored) {
        }

        if (cached == null) {
            return fallbackValue;
        } else {
            return cached;
        }
    }

    public V getDirectly(K key) throws E {
        V result = source.apply(key);
        put(key, result);
        return result;
    }

    public ObjectBinding<V> binding(K key) {
        return Bindings.createObjectBinding(() -> {
            V result;
            boolean refresh;
            synchronized (this) {
                result = cache.get(key);
                if (result == null) {
                    result = fallbackValue;
                    refresh = true;
                } else {
                    refresh = invalidated.containsKey(key);
                }
            }
            if (refresh) {
                query(key, executor);
            }
            return result;
        }, observable);
    }

    public void invalidate(K key) {
        synchronized (this) {
            if (cache.containsKey(key)) {
                invalidated.put(key, Boolean.TRUE);
            }
        }
        Platform.runLater(observable::invalidate);
    }
}
