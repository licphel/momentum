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

package io.viki.momentum.util.multithread;

import io.viki.momentum.util.Platform;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Process-wide background task budget. All entry points are thread-safe.
 *
 * <p>This class provides two shared executors to limit and schedule background tasks across the
 * entire application lifecycle, preventing resource exhaustion caused by creating separate thread
 * pools for each component.
 *
 * <h2>Resource Limiting Strategy</h2>
 * <ul>
 *   <li><b>CPU work ({@link #cpu()})</b>: Uses at most two platform workers (one on devices with
 *       two or fewer logical processors).</li>
 *   <li><b>Blocking I/O ({@link #io()})</b>: Uses one virtual thread per task, with four tasks
 *       admitted at a time.</li>
 * </ul>
 *
 * <h2>Usage Constraints</h2>
 * <ul>
 *   <li>Decode/compression work belongs on {@link #cpu()}, <b>not</b> inside an I/O task.</li>
 *   <li>Neither executor owns render, world-tick, or native-context threads.</li>
 *   <li>Tasks must not wait for dependent work submitted to the same bounded CPU executor, as
 *       this may cause deadlock.</li>
 * </ul>
 *
 * <h2>Lifecycle and Exposure</h2>
 * <p>Executors are application-owned daemon services and are intentionally exposed only as
 * {@link Executor} to prevent external components from erroneously shutting down shared services.
 * Components should cancel their own tasks instead of shutting down shared services.
 * CPU admission is bounded; saturation throws {@link RejectedExecutionException} and <b>never</b>
 * executes work on the caller thread.
 *
 * @since 1.0
 */
public final class Parallel {
  /**
   * I/O concurrency limit. At most {@code IO_CONCURRENCY} I/O tasks may hold execution permits
   * simultaneously.
   */
  public static final int IO_CONCURRENCY = 4;
  /**
   * CPU concurrency limit. Set to 1 on devices with two or fewer logical processors, otherwise 2.
   */
  public static final int CPU_CONCURRENCY = Runtime.getRuntime().availableProcessors() <= 2 ? 1 : 2;
  /** Maximum queue capacity for the CPU executor. */
  private static final int CPU_QUEUE_CAPACITY = 256;
  private static final System.Logger LOGGER = System.getLogger(Parallel.class.getName());
  private static final ThreadPoolExecutor CPU;
  private static final Semaphore IO_PERMITS = new Semaphore(IO_CONCURRENCY);
  private static final Executor IO;
  private static final Executor IO_THREADS = Executors.newVirtualThreadPerTaskExecutor();
  private static final ScheduledThreadPoolExecutor TIMER = new ScheduledThreadPoolExecutor(1,
      Thread.ofPlatform().daemon().name("Parallel-Timer").factory());

  static {
    CPU = new ThreadPoolExecutor(
        CPU_CONCURRENCY, CPU_CONCURRENCY, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(CPU_QUEUE_CAPACITY),
        Thread.ofPlatform().daemon().name("Parallel-CPU-", 0).factory());
    CPU.allowCoreThreadTimeOut(true);
    TIMER.setRemoveOnCancelPolicy(true);
    IO = task -> IO_THREADS.execute(() -> {
      IO_PERMITS.acquireUninterruptibly();
      try {
        task.run();
      } finally {
        IO_PERMITS.release();
      }
    });
  }

  private Parallel() {
  }

  /**
   * Returns the shared executor for computation.
   *
   * <p>This executor has an O(1) bounded admission policy. When the queue is full, submitting a
   * task immediately throws {@link RejectedExecutionException} rather than executing the task
   * inline on the caller thread.
   *
   * @return the shared CPU executor, never {@code null}
   */
  public static Executor cpu() {
    return CPU;
  }

  /**
   * Returns the shared executor for blocking I/O.
   *
   * <p>Because virtual threads are used, waiting virtual threads do not occupy CPU worker slots.
   * At most {@link #IO_CONCURRENCY} tasks are admitted for execution at a time; excess tasks
   * block within their virtual threads.
   *
   * @return the shared I/O executor, never {@code null}
   */
  public static Executor io() {
    return IO;
  }

  /**
   * Submits a computation task and returns its completion, including task failure.
   *
   * @param task the computation task to execute, must not be {@code null}
   * @return a {@link CompletableFuture} representing task completion
   * @throws RejectedExecutionException if the CPU executor's queue is full
   */
  public static CompletableFuture<Void> cpu(Runnable task) {
    return CompletableFuture.runAsync(task, CPU);
  }

  /**
   * Submits a blocking I/O task and returns its completion, including task failure.
   *
   * @param task the I/O task to execute, must not be {@code null}
   * @return a {@link CompletableFuture} representing task completion
   */
  public static CompletableFuture<Void> io(Runnable task) {
    return CompletableFuture.runAsync(task, IO);
  }

  /**
   * Registers non-overlapping recurring work on the shared CPU executor.
   *
   * <p>The first attempt occurs after the interval. A tick is skipped while the previous invocation
   * remains queued/running or CPU admission is full: recurring tasks never build up a backlog.
   * The timer only dispatches work. Exceptions are reported and do not stop subsequent invocations.
   * Cancel the returned future to unregister; an already admitted invocation may still finish.
   *
   * @param interval positive spacing between dispatch attempts
   * @param task     recurring computation, not a blocking I/O operation
   * @return a cancellation handle for future dispatches
   * @throws IllegalArgumentException if {@code interval} is not positive
   */
  public static ScheduledFuture<?> registerScheduledTask(Duration interval, Runnable task) {
    long nanos = interval.toNanos();
    if (nanos <= 0) {
      throw new IllegalArgumentException("Scheduled interval must be positive: " + interval);
    }
    AtomicBoolean pending = new AtomicBoolean();
    Runnable invocation = () -> {
      try {
        task.run();
      } catch (Exception exception) {
        LOGGER.log(System.Logger.Level.WARNING, "Scheduled background task failed", exception);
      } finally {
        pending.set(false);
      }
    };
    return TIMER.scheduleWithFixedDelay(() -> {
      if (!pending.compareAndSet(false, true)) {
        return;
      }
      try {
        CPU.execute(invocation);
      } catch (RejectedExecutionException exception) {
        pending.set(false);
      }
    }, nanos, nanos, TimeUnit.NANOSECONDS);
  }

  /**
   * Registers recurring work using a positive interval in milliseconds.
   *
   * @param intervalMillis positive spacing between dispatch attempts, in milliseconds
   * @param task           recurring computation, not a blocking I/O operation
   * @return a cancellation handle for future dispatches
   * @throws IllegalArgumentException if {@code intervalMillis} is not positive
   * @see #registerScheduledTask(Duration, Runnable)
   */
  public static ScheduledFuture<?> registerScheduledTask(long intervalMillis, Runnable task) {
    return registerScheduledTask(Duration.ofMillis(intervalMillis), task);
  }

  /**
   * Determines whether the current platform is CPU-resource-limited.
   *
   * @return {@code true} if the platform is resource-limited, otherwise {@code false}
   */
  public static boolean isCpuResourceLimited() {
    Platform platform = Platform.currentOS();
    return !(platform == Platform.WINDOWS || platform == Platform.LINUX || platform == Platform.MACOS);
  }
}