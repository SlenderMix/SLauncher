package ru.spark.slauncher.task;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import ru.spark.slauncher.event.EventManager;
import ru.spark.slauncher.util.InvocationDispatcher;
import ru.spark.slauncher.util.Logging;
import ru.spark.slauncher.util.ReflectionHelper;
import ru.spark.slauncher.util.function.ExceptionalConsumer;
import ru.spark.slauncher.util.function.ExceptionalRunnable;
import ru.spark.slauncher.util.function.ExceptionalSupplier;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;

/**
 * Disposable task.
 *
 * @author Spark1337
 */
public abstract class Task {

    private final EventManager<TaskEvent> onDone = new EventManager<>();
    private final ReadOnlyDoubleWrapper progress = new ReadOnlyDoubleWrapper(this, "progress", -1);
    private final InvocationDispatcher<Double> progressUpdate = InvocationDispatcher.runOn(Platform::runLater, progress::set);
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", null);
    private final InvocationDispatcher<String> messageUpdate = InvocationDispatcher.runOn(Platform::runLater, message::set);
    private TaskSignificance significance = TaskSignificance.MAJOR;
    private TaskState state = TaskState.READY;
    private Exception lastException;
    private boolean dependentsSucceeded = false;
    private boolean dependenciesSucceeded = false;
    private String name = getClass().getName();
    private long lastTime = Long.MIN_VALUE;

    public static Task of(ExceptionalRunnable<?> closure) {
        return of(Schedulers.defaultScheduler(), closure);
    }

    public static Task of(String name, ExceptionalRunnable<?> closure) {
        return of(name, Schedulers.defaultScheduler(), closure);
    }

    public static Task of(Scheduler scheduler, ExceptionalRunnable<?> closure) {
        return of(getCaller(), scheduler, closure);
    }

    public static Task of(String name, Scheduler scheduler, ExceptionalRunnable<?> closure) {
        return new SimpleTask(name, closure, scheduler);
    }

    public static Task ofThen(ExceptionalSupplier<Task, ?> b) {
        return new CoupleTask(null, b, true);
    }

    public static <V> TaskResult<V> ofResult(Callable<V> callable) {
        return ofResult(getCaller(), callable);
    }

    public static <V> TaskResult<V> ofResult(String name, Callable<V> callable) {
        return new SimpleTaskResult<>(callable).setName(name);
    }

    private static ExceptionalSupplier<Task, ?> convert(Task t) {
        return new ExceptionalSupplier<Task, Exception>() {
            @Override
            public Task get() {
                return t;
            }

            @Override
            public String toString() {
                return t.getName();
            }
        };
    }

    static String getCaller() {
        return ReflectionHelper.getCaller(packageName -> !"ru.spark.slauncher.task".equals(packageName)).toString();
    }

    /**
     * True if not logging when executing this task.
     */
    public final TaskSignificance getSignificance() {
        return significance;
    }

    public void setSignificance(TaskSignificance significance) {
        this.significance = significance;
    }

    public TaskState getState() {
        return state;
    }

    void setState(TaskState state) {
        this.state = state;
    }

    public Exception getLastException() {
        return lastException;
    }

    void setLastException(Exception e) {
        lastException = e;
    }

    /**
     * The scheduler that decides how this task runs.
     */
    public Scheduler getScheduler() {
        return Schedulers.defaultScheduler();
    }

    public boolean isDependentsSucceeded() {
        return dependentsSucceeded;
    }

    void setDependentsSucceeded() {
        dependentsSucceeded = true;
    }

    public boolean isDependenciesSucceeded() {
        return dependenciesSucceeded;
    }

    void setDependenciesSucceeded() {
        dependenciesSucceeded = true;
    }

    /**
     * True if requires all {@link #getDependents} finishing successfully.
     * <p>
     * **Note** if this field is set false, you are not supposed to invoke [run]
     */
    public boolean isRelyingOnDependents() {
        return true;
    }

    /**
     * True if requires all {@link #getDependencies} finishing successfully.
     * <p>
     * **Note** if this field is set false, you are not supposed to invoke [run]
     */
    public boolean isRelyingOnDependencies() {
        return true;
    }

    public String getName() {
        return name;
    }

    public Task setName(String name) {
        this.name = name;
        return this;
    }

    public boolean doPreExecute() {
        return false;
    }

    /**
     * @throws InterruptedException if current thread is interrupted
     * @see Thread#isInterrupted
     */
    public void preExecute() throws Exception {
    }

    /**
     * @throws InterruptedException if current thread is interrupted
     * @see Thread#isInterrupted
     */
    public abstract void execute() throws Exception;

    public boolean doPostExecute() {
        return false;
    }

    /**
     * This method will be called after dependency tasks terminated all together.
     * <p>
     * You can check whether dependencies succeed in this method by calling
     * {@link Task#isDependenciesSucceeded()} no matter when
     * {@link Task#isRelyingOnDependencies()} returns true or false.
     *
     * @throws InterruptedException if current thread is interrupted
     * @see Thread#isInterrupted
     * @see Task#isDependenciesSucceeded()
     */
    public void postExecute() throws Exception {
    }

    /**
     * The collection of sub-tasks that should execute **before** this task running.
     */
    public Collection<? extends Task> getDependents() {
        return Collections.emptySet();
    }

    /**
     * The collection of sub-tasks that should execute **after** this task running.
     * Will not be executed if execution fails.
     */
    public Collection<? extends Task> getDependencies() {
        return Collections.emptySet();
    }

    public EventManager<TaskEvent> onDone() {
        return onDone;
    }

    protected long getProgressInterval() {
        return 1000L;
    }

    public ReadOnlyDoubleProperty progressProperty() {
        return progress.getReadOnlyProperty();
    }

    protected void updateProgress(int progress, int total) {
        updateProgress(1.0 * progress / total);
    }

    protected void updateProgress(double progress) {
        if (progress < 0 || progress > 1.0)
            throw new IllegalArgumentException("Progress is must between 0 and 1.");
        long now = System.currentTimeMillis();
        if (lastTime == Long.MIN_VALUE || now - lastTime >= getProgressInterval()) {
            updateProgressImmediately(progress);
            lastTime = now;
        }
    }

    protected void updateProgressImmediately(double progress) {
        progressUpdate.accept(progress);
    }

    public final ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }

    protected final void updateMessage(String newMessage) {
        messageUpdate.accept(newMessage);
    }

    public final void run() throws Exception {
        if (getSignificance().shouldLog())
            Logging.LOG.log(Level.FINE, "Executing task: " + getName());

        for (Task task : getDependents())
            doSubTask(task);
        execute();
        for (Task task : getDependencies())
            doSubTask(task);
        onDone.fireEvent(new TaskEvent(this, this, false));
    }

    private void doSubTask(Task task) throws Exception {
        message.bind(task.message);
        progress.bind(task.progress);
        task.run();
        message.unbind();
        progress.unbind();
    }

    public final TaskExecutor executor() {
        return new TaskExecutor(this);
    }

    public final TaskExecutor executor(boolean start) {
        TaskExecutor executor = new TaskExecutor(this);
        if (start)
            executor.start();
        return executor;
    }

    public final TaskExecutor executor(TaskListener taskListener) {
        TaskExecutor executor = new TaskExecutor(this);
        executor.addTaskListener(taskListener);
        return executor;
    }

    public final void start() {
        executor().start();
    }

    public final boolean test() {
        return executor().test();
    }

    public final Task then(Task b) {
        return then(convert(b));
    }

    public final Task then(ExceptionalSupplier<Task, ?> b) {
        return new CoupleTask(this, b, true);
    }

    /**
     * Returns a new TaskResult that, when this task completes
     * normally, is executed using the default Scheduler.
     *
     * @param fn  the function to use to compute the value of the returned TaskResult
     * @param <U> the function's return type
     * @return the new TaskResult
     */
    public final <U> TaskResult<U> thenSupply(Callable<U> fn) {
        return thenCompose(() -> Task.ofResult(fn));
    }

    /**
     * Returns a new TaskResult that, when this task completes
     * normally, is executed.
     *
     * @param fn  the function returning a new TaskResult
     * @param <U> the type of the returned TaskResult's result
     * @return the TaskResult
     */
    public final <U> TaskResult<U> thenCompose(ExceptionalSupplier<TaskResult<U>, ?> fn) {
        return new TaskResult<U>() {
            TaskResult<U> then;

            @Override
            public Collection<? extends Task> getDependents() {
                return Collections.singleton(Task.this);
            }

            @Override
            public void execute() throws Exception {
                then = fn.get().storeTo(this::setResult);
            }

            @Override
            public Collection<? extends Task> getDependencies() {
                return then == null ? Collections.emptyList() : Collections.singleton(then);
            }
        };
    }

    public final Task with(Task b) {
        return with(convert(b));
    }

    public final <E extends Exception> Task with(ExceptionalSupplier<Task, E> b) {
        return new CoupleTask(this, b, false);
    }

    /**
     * Returns a new Task with the same exception as this task, that executes
     * the given action when this task completes.
     *
     * <p>When this task is complete, the given action is invoked, a boolean
     * value represents the execution status of this task, and the exception
     * (or {@code null} if none) of this task as arguments.  The returned task
     * is completed when the action returns.  If the supplied action itself
     * encounters an exception, then the returned task exceptionally completes
     * with this exception unless this task also completed exceptionally.
     *
     * @param action the action to perform
     * @return the new Task
     */
    public final Task whenComplete(FinalizedCallback action) {
        return whenComplete(Schedulers.defaultScheduler(), action);
    }

    /**
     * Returns a new Task with the same exception as this task, that executes
     * the given action when this task completes.
     *
     * <p>When this task is complete, the given action is invoked, a boolean
     * value represents the execution status of this task, and the exception
     * (or {@code null} if none, which means when isDependentSucceeded is false,
     * exception may be null) of this task as arguments.  The returned task
     * is completed when the action returns.  If the supplied action itself
     * encounters an exception, then the returned task exceptionally completes
     * with this exception unless this task also completed exceptionally.
     *
     * @param action    the action to perform
     * @param scheduler the executor to use for asynchronous execution
     * @return the new Task
     */
    public final Task whenComplete(Scheduler scheduler, FinalizedCallback action) {
        return new Task() {
            {
                setSignificance(TaskSignificance.MODERATE);
            }

            @Override
            public Scheduler getScheduler() {
                return scheduler;
            }

            @Override
            public void execute() throws Exception {
                action.execute(isDependentsSucceeded(), Task.this.getLastException());

                if (!isDependentsSucceeded()) {
                    setSignificance(TaskSignificance.MINOR);
                    if (Task.this.getLastException() == null)
                        throw new CancellationException();
                    else
                        throw Task.this.getLastException();
                }
            }

            @Override
            public Collection<Task> getDependents() {
                return Collections.singleton(Task.this);
            }

            @Override
            public boolean isRelyingOnDependents() {
                return false;
            }
        }.setName(getCaller());
    }

    /**
     * Returns a new Task with the same exception as this task, that executes
     * the given actions when this task completes.
     *
     * <p>When this task is complete, the given success action is invoked, the
     * given failure action is invoked with the exception of this task.  The
     * returned task is completed when the action returns.  If the supplied
     * action itself encounters an exception, then the returned task exceptionally
     * completes with this exception unless this task also
     * completed exceptionally.
     *
     * @param success the action to perform when this task successfully completed
     * @param failure the action to perform when this task exceptionally returned
     * @return the new Task
     */
    public final <E1 extends Exception, E2 extends Exception> Task whenComplete(Scheduler scheduler, ExceptionalRunnable<E1> success, ExceptionalConsumer<Exception, E2> failure) {
        return whenComplete(scheduler, (isDependentSucceeded, exception) -> {
            if (isDependentSucceeded) {
                if (success != null)
                    try {
                        success.run();
                    } catch (Exception e) {
                        Logging.LOG.log(Level.WARNING, "Failed to execute " + success, e);
                        if (failure != null)
                            failure.accept(e);
                    }
            } else {
                if (failure != null)
                    failure.accept(exception);
            }
        });
    }

    public enum TaskSignificance {
        MAJOR,
        MODERATE,
        MINOR;

        public boolean shouldLog() {
            return this != MINOR;
        }

        public boolean shouldShow() {
            return this == MAJOR;
        }
    }

    public enum TaskState {
        READY,
        RUNNING,
        EXECUTED,
        SUCCEEDED,
        FAILED
    }

    public interface FinalizedCallback {
        void execute(boolean isDependentSucceeded, Exception exception) throws Exception;
    }
}
