/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.viki.momentum.asset;

import org.jspecify.annotations.Nullable;
import io.viki.momentum.util.multithread.Parallel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Runs priority-ordered asset tasks with bounded background preparation.
 *
 * <p>Queue operations and publication callbacks belong to the owner thread that drives
 * {@link #next()}. A {@link Plan} separates ordered owner-thread work from independent preparation
 * that may run on workers. Each batch commits tasks in ascending priority, then enqueue order; tasks
 * added while a batch is running join the next batch. Workers never mutate queue bookkeeping.
 *
 * <p>The preparation window includes completed but unpublished values, bounding both concurrency
 * and look-ahead memory. Preparations must be independent of publications, own and close their
 * input streams, and return heap-managed data that does not require owner-thread cleanup if loading
 * is canceled.
 *
 * <p>This class is not thread-safe.
 */
public final class TaskQueue implements AutoCloseable {
  private static final Comparator<Task> ORDER
      = Comparator.comparingInt((Task task) -> task.priority).thenComparingLong(task -> task.sequence);
  private final PriorityQueue<Task> pending = new PriorityQueue<>(ORDER);
  private final PriorityQueue<Task> active = new PriorityQueue<>(ORDER);
  private final PriorityQueue<Preparation> preparations
      = new PriorityQueue<>(Comparator.comparing(Preparation::task, ORDER).thenComparingInt(Preparation::index));
  private final int parallelism;
  private long nextSequence;
  private int outstanding;
  private boolean started;
  private boolean closed;

  /** Creates a queue with a preparation window twice the available CPU count. */
  public TaskQueue() {
    this(Parallel.CPU_CONCURRENCY * 2);
  }

  /**
   * Creates an owner-thread queue with a fixed preparation window.
   *
   * @param parallelism maximum number of prepared but unpublished values, which must be positive
   * @throws IllegalArgumentException if {@code parallelism} is less than one
   */
  public TaskQueue(int parallelism) {
    if (parallelism < 1) {
      throw new IllegalArgumentException("Preparation parallelism must be positive");
    }
    this.parallelism = parallelism;
  }

  /**
   * Returns the maximum number of prepared but unpublished values.
   *
   * @return the preparation window size
   */
  public int parallelism() {
    return parallelism;
  }

  /**
   * Enqueues an action to run on the owner thread as a single-stage task.
   *
   * <p>If the action fails, its exception is rethrown by {@link #next()} when the task completes.
   *
   * @param priority task priority; lower values run first
   * @param task     the action to run
   * @throws IllegalStateException if this queue is closed
   */
  public void enqueue(int priority, Runnable task) {
    enqueue(priority, plan -> plan.run(task), failures -> {
      if (!failures.isEmpty()) {
        Throwable error = failures.getFirst();
        if (error instanceof RuntimeException runtime) {
          throw runtime;
        }
        throw new CompletionException(error);
      }
    });
  }

  /**
   * Defines and enqueues one ordered asset task.
   *
   * <p>The definition callback runs immediately on the owner thread and should only register stages.
   * The completion callback receives all stage failures, including preparation failures. Failed
   * stages are skipped and later stages still run. The definition callback must not retain its plan.
   * Exceptions from the definition propagate from this method; exceptions from the completion
   * callback propagate from {@link #next()}.
   *
   * @param priority   task priority; lower values run first, with enqueue order breaking ties
   * @param definition callback that registers the task's ordered stages
   * @param completed  callback that receives failures after all stages have been processed
   * @throws IllegalStateException if this queue is closed
   * @throws RuntimeException      if the definition callback fails
   */
  public void enqueue(int priority, Consumer<Plan> definition, Consumer<List<Throwable>> completed) {
    if (closed) {
      throw new IllegalStateException("Asset task queue is closed");
    }
    Plan plan = new Plan();
    definition.accept(plan);
    pending.add(new Task(priority, nextSequence++, List.copyOf(plan.steps), completed));
  }

  /**
   * Advances one ready stage without waiting for workers.
   *
   * @return {@code true} if a stage or empty task completed; {@code false} if the queue is closed,
   * has no active task, or its next preparation is not ready
   * @throws RuntimeException if a task completion callback fails
   */
  public boolean next() {
    if (closed) {
      return false;
    }
    started = true;
    if (active.isEmpty()) {
      active.addAll(pending);
      pending.clear();
      for (Task task : active) {
        for (int i = 0; i < task.steps.size(); i++) {
          if (task.steps.get(i).parallel) preparations.add(new Preparation(task, i));
        }
      }
    }
    startPreparations();
    Task task = active.peek();
    if (task == null) {
      return false;
    }
    if (task.cursor < task.steps.size()) {
      Step<?> step = task.steps.get(task.cursor);
      if (step.parallel && (step.future == null || !step.future.isDone())) {
        return false;
      }
      try {
        step.publish();
      } catch (Exception error) {
        task.failures.add(error instanceof CompletionException && error.getCause() != null
            ? error.getCause() : error);
      } finally {
        if (step.parallel) {
          outstanding--;
          step.future = null;
        }
        task.cursor++;
      }
    }
    if (task.cursor == task.steps.size()) {
      active.remove();
      task.completed.accept(List.copyOf(task.failures));
    }
    startPreparations();
    return true;
  }

  /**
   * Reports whether driving has begun and no pending or active tasks remain.
   *
   * @return {@code true} after {@link #next()} has been called and the queue has no pending work
   */
  public boolean done() {
    return closed || started && size() == 0;
  }

  /**
   * Returns the number of queued tasks that have not completed.
   *
   * @return the number of pending and active tasks, including those waiting for preparation or publication
   */
  public int size() {
    return pending.size() + active.size();
  }

  /** Starts queued preparations until the configured window is full or none remain. */
  private void startPreparations() {
    while (outstanding < parallelism && !preparations.isEmpty()) {
      Preparation preparation = preparations.remove();
      try {
        preparation.task.steps.get(preparation.index).start(Parallel.cpu());
      } catch (RejectedExecutionException exception) {
        preparations.add(preparation);
        break;
      }
      outstanding++;
    }
  }

  /**
   * Cancels queued preparations and stops the worker executor without waiting for workers to exit.
   *
   * <p>Pending tasks are discarded, and later calls to {@link #next()} do not publish work. This
   * operation is idempotent and must run on the owner thread.
   */
  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    for (Task task : active) {
      for (Step<?> step : task.steps) {
        if (step.future != null) {
          step.future.cancel(true);
        }
      }
    }
    pending.clear();
    active.clear();
    preparations.clear();
  }

  /**
   * Describes the ordered stages that make up one asset task.
   *
   * <p>Register stages from the definition callback passed to {@link #enqueue(int, Consumer,
   * Consumer)}. Preparations may run on workers, while every publication runs on the queue's owner
   * thread in stage order. A plan is not thread-safe and must not be retained after its definition
   * callback returns.
   */
  public static final class Plan {
    private final List<Step<?>> steps = new ArrayList<>();

    private Plan() {
    }

    /**
     * Schedules an action to run on the queue's owner thread in stage order.
     *
     * @param action the action to run when this stage is published
     */
    public void run(Runnable action) {
      steps.add(new Step<>(() -> Boolean.TRUE, ignored -> action.run(), false));
    }

    /**
     * Schedules independent preparation and ordered owner-thread publication.
     *
     * <p>The prepared value must be heap-managed. If preparation fails, publication is skipped and
     * the failure is delivered to the task's completion callback.
     *
     * @param <T>         the prepared value type
     * @param preparation work that produces a value independently of publication
     * @param publication owner-thread action that consumes the prepared value
     */
    public <T> void prepare(Callable<T> preparation, Consumer<? super T> publication) {
      steps.add(new Step<>(preparation, publication, true));
    }
  }

  private static final class Step<T> {
    final Callable<T> preparation;
    final Consumer<? super T> publication;
    final boolean parallel;
    @Nullable CompletableFuture<T> future;

    Step(Callable<T> preparation, Consumer<? super T> publication, boolean parallel) {
      this.preparation = preparation;
      this.publication = publication;
      this.parallel = parallel;
    }

    /**
     * Starts this preparation asynchronously.
     *
     * @param executor the worker executor that runs the preparation
     * @throws RejectedExecutionException if the executor rejects the work
     */
    void start(Executor executor) {
      future = CompletableFuture.supplyAsync(() -> {
        try {
          return preparation.call();
        } catch (Exception error) {
          throw new CompletionException(error);
        }
      }, executor);
    }

    /**
     * Publishes the prepared value, or runs this stage's synchronous action.
     *
     * @throws Exception if preparation or publication fails
     */
    void publish() throws Exception {
      assert !parallel || future != null;
      publication.accept(parallel ? future.join() : preparation.call());
    }
  }

  private static final class Task {
    final int priority;
    final long sequence;
    final List<Step<?>> steps;
    final Consumer<List<Throwable>> completed;
    final List<Throwable> failures = new ArrayList<>();
    int cursor;

    Task(int priority, long sequence, List<Step<?>> steps, Consumer<List<Throwable>> completed) {
      this.priority = priority;
      this.sequence = sequence;
      this.steps = steps;
      this.completed = completed;
    }
  }

  private record Preparation(Task task, int index) { }
}
