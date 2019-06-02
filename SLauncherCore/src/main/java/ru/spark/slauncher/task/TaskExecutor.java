package ru.spark.slauncher.task;

import ru.spark.slauncher.util.Logging;
import ru.spark.slauncher.util.function.ExceptionalRunnable;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * @author Spark1337
 */
public final class TaskExecutor {

    private final Task firstTask;
    private final List<TaskListener> taskListeners = new LinkedList<>();
    private final AtomicInteger totTask = new AtomicInteger(0);
    private final ConcurrentLinkedQueue<Future<?>> workerQueue = new ConcurrentLinkedQueue<>();
    private boolean canceled = false;
    private Exception lastException;
    private Scheduler scheduler = Schedulers.newThread();

    public TaskExecutor(Task task) {
        this.firstTask = task;
    }

    public void addTaskListener(TaskListener taskListener) {
        taskListeners.add(taskListener);
    }

    public boolean isCanceled() {
        return canceled;
    }

    public Exception getLastException() {
        return lastException;
    }

    public void setScheduler(Scheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler);
    }

    public TaskExecutor start() {
        taskListeners.forEach(TaskListener::onStart);
        workerQueue.add(scheduler.schedule(() -> {
            boolean flag = executeTasks(Collections.singleton(firstTask));
            taskListeners.forEach(it -> it.onStop(flag, this));
        }));
        return this;
    }

    public boolean test() {
        taskListeners.forEach(TaskListener::onStart);
        AtomicBoolean flag = new AtomicBoolean(true);
        Future<?> future = scheduler.schedule(() -> {
            flag.set(executeTasks(Collections.singleton(firstTask)));
            taskListeners.forEach(it -> it.onStop(flag.get(), this));
        });
        workerQueue.add(future);
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | CancellationException e) {
        }
        return flag.get();
    }

    /**
     * Cancel the subscription ant interrupt all tasks.
     */
    public synchronized void cancel() {
        canceled = true;

        while (!workerQueue.isEmpty()) {
            Future<?> future = workerQueue.poll();
            if (future != null)
                future.cancel(true);
        }
    }

    private boolean executeTasks(Collection<? extends Task> tasks) throws InterruptedException {
        if (tasks.isEmpty())
            return true;

        totTask.addAndGet(tasks.size());
        AtomicBoolean success = new AtomicBoolean(true);
        CountDownLatch latch = new CountDownLatch(tasks.size());
        for (Task task : tasks) {
            if (canceled)
                return false;
            Invoker invoker = new Invoker(task, latch, success);
            try {
                Future<?> future = scheduler.schedule(invoker);
                if (future != null)
                    workerQueue.add(future);
            } catch (RejectedExecutionException e) {
                throw new InterruptedException();
            }
        }

        if (canceled)
            return false;

        try {
            latch.await();
        } catch (InterruptedException e) {
            return false;
        }
        return success.get() && !canceled;
    }

    private boolean executeTask(Task task) {
        if (canceled) {
            task.setState(Task.TaskState.FAILED);
            task.setLastException(new CancellationException());
            return false;
        }

        task.setState(Task.TaskState.READY);

        if (task.getSignificance().shouldLog())
            Logging.LOG.log(Level.FINE, "Executing task: " + task.getName());

        taskListeners.forEach(it -> it.onReady(task));

        boolean flag = false;

        try {
            if (task.doPreExecute()) {
                try {
                    task.getScheduler().schedule(task::preExecute).get();
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof Exception)
                        throw (Exception) e.getCause();
                    else
                        throw e;
                }
            }

            Collection<? extends Task> dependents = task.getDependents();
            boolean doDependentsSucceeded = executeTasks(dependents);
            Exception dependentsException = dependents.stream().map(Task::getLastException).filter(Objects::nonNull).findAny().orElse(null);
            if (!doDependentsSucceeded && task.isRelyingOnDependents() || canceled) {
                task.setLastException(dependentsException);
                throw new CancellationException();
            }

            if (doDependentsSucceeded)
                task.setDependentsSucceeded();

            try {
                task.getScheduler().schedule(() -> {
                    task.setState(Task.TaskState.RUNNING);
                    taskListeners.forEach(it -> it.onRunning(task));
                    task.execute();
                }).get();
            } catch (ExecutionException e) {
                if (e.getCause() instanceof Exception)
                    throw (Exception) e.getCause();
                else
                    throw e;
            } finally {
                task.setState(Task.TaskState.EXECUTED);
            }

            Collection<? extends Task> dependencies = task.getDependencies();
            boolean doDependenciesSucceeded = executeTasks(dependencies);
            Exception dependenciesException = dependencies.stream().map(Task::getLastException).filter(Objects::nonNull).findAny().orElse(null);

            if (doDependenciesSucceeded)
                task.setDependenciesSucceeded();

            if (task.doPostExecute()) {
                try {
                    task.getScheduler().schedule(task::postExecute).get();
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof Exception)
                        throw (Exception) e.getCause();
                    else
                        throw e;
                }
            }

            if (!doDependenciesSucceeded && task.isRelyingOnDependencies()) {
                Logging.LOG.severe("Subtasks failed for " + task.getName());
                task.setLastException(dependenciesException);
                throw new CancellationException();
            }

            flag = true;
            if (task.getSignificance().shouldLog()) {
                Logging.LOG.log(Level.FINER, "Task finished: " + task.getName());
            }

            task.onDone().fireEvent(new TaskEvent(this, task, false));
            taskListeners.forEach(it -> it.onFinished(task));
        } catch (InterruptedException e) {
            task.setLastException(e);
            if (task.getSignificance().shouldLog()) {
                Logging.LOG.log(Level.FINE, "Task aborted: " + task.getName());
            }
            task.onDone().fireEvent(new TaskEvent(this, task, true));
            taskListeners.forEach(it -> it.onFailed(task, e));
        } catch (CancellationException | RejectedExecutionException e) {
            if (task.getLastException() == null)
                task.setLastException(e);
        } catch (Exception e) {
            task.setLastException(e);
            lastException = e;
            if (task.getSignificance().shouldLog()) {
                Logging.LOG.log(Level.FINE, "Task failed: " + task.getName(), e);
            }
            task.onDone().fireEvent(new TaskEvent(this, task, true));
            taskListeners.forEach(it -> it.onFailed(task, e));
        }
        task.setState(flag ? Task.TaskState.SUCCEEDED : Task.TaskState.FAILED);
        return flag;
    }

    public int getRunningTasks() {
        return totTask.get();
    }

    private class Invoker implements ExceptionalRunnable<Exception> {

        private final Task task;
        private final CountDownLatch latch;
        private final AtomicBoolean success;

        public Invoker(Task task, CountDownLatch latch, AtomicBoolean success) {
            this.task = task;
            this.latch = latch;
            this.success = success;
        }

        @Override
        public void run() {
            try {
                Thread.currentThread().setName(task.getName());
                if (!executeTask(task))
                    success.set(false);
            } finally {
                latch.countDown();
            }
        }

    }
}
