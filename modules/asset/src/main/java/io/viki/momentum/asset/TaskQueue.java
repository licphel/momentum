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

import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * A single-threaded priority queue for asset work.
 *
 * <p>Lower numeric priorities run first. Higher priorities therefore run later
 * and can override values produced by lower-priority work. Tasks with equal
 * priority run in enqueue order.
 */
public final class TaskQueue {
  private final PriorityQueue<Task> tasks = new PriorityQueue<>(
      Comparator.comparingInt(Task::priority).thenComparingLong(Task::sequence)
  );
  private long nextSequence;
  private boolean started;

  /**
   * Adds a task to the queue.
   *
   * @param priority the task priority
   * @param task     the task to run
   */
  public void enqueue(int priority, Runnable task) {
    tasks.add(new Task(priority, nextSequence++, task));
  }

  /**
   * Executes the next task, if any.
   *
   * @return true if a task was executed
   */
  public boolean next() {
    started = true;
    Task task = tasks.poll();
    if (task == null) {
      return false;
    }
    task.action.run();
    return true;
  }

  /**
   * Returns whether the queue has been started and is empty.
   *
   * @return true when no queued work remains
   */
  public boolean done() {
    return started && tasks.isEmpty();
  }

  /**
   * Returns the number of queued tasks.
   *
   * @return the queue size
   */
  public int size() {
    return tasks.size();
  }

  private record Task(int priority, long sequence, Runnable action) {
  }
}
