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

import io.viki.momentum.util.Identifier;
import io.viki.momentum.util.Namespace;
import io.viki.momentum.resource.Resource;
import io.viki.momentum.logging.Log;
import io.viki.momentum.logging.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Per-namespace, frame-by-frame asset loading facade.
 *
 * <p>An {@code AssetLoader} resolves paths into identifiers and submits work
 * to an {@link TaskQueue}. The queue may be shared by multiple loaders so
 * priorities are applied across namespaces as well as within one namespace.
 * Processing rules are supplied by a reusable {@link LoadingDispatcher}.
 *
 * <p>The resource provider is owned by the caller. In particular, a mod's
 * provider is available from {@code mod.jarResource()} and is not closed by
 * this loader.
 */
public final class Loader {
  /** The priority used by overloads that do not specify one. */
  public static final int DEFAULT_PRIORITY = 0;

  private static final Logger LOGGER = Log.getLogger();

  private final Namespace namespace;
  private final Resource resources;
  private final LoadingDispatcher processors;
  private final TaskQueue taskQueue;
  private final List<Throwable> failures = new ArrayList<>();
  private final Map<Identifier, String> loadedFiles = new HashMap<>();
  private int totalTasks;
  private int completedTasks;
  private int pendingTasks;
  private boolean started;

  /**
   * Creates an asset loader with no processors and its own task queue.
   *
   * @param namespace the namespace used to resolve relative paths
   * @param resources the provider for resources in that namespace
   */
  public Loader(Namespace namespace, Resource resources) {
    this(namespace, resources, LoadingDispatcher.EMPTY);
  }

  /**
   * Creates an asset loader with a processor group and its own task queue.
   *
   * @param namespace the namespace used to resolve relative paths
   * @param resources the provider for resources in that namespace
   * @param processors the reusable processing rules
   */
  public Loader(Namespace namespace, Resource resources, LoadingDispatcher processors) {
    this(namespace, resources, processors, new TaskQueue());
  }

  /**
   * Creates an asset loader that submits work to a caller-owned task queue.
   *
   * <p>Sharing the queue makes priority ordering global across all loaders
   * passed the same queue. The coordinating thread should drive the queue
   * directly through {@link #taskQueue()} or drive one loader at a time.
   *
   * @param namespace the namespace used to resolve relative paths
   * @param resources the provider for resources in that namespace
   * @param processors the reusable processing rules
   * @param taskQueue the task queue receiving submitted work
   */
  public Loader(Namespace namespace, Resource resources, LoadingDispatcher processors,
                TaskQueue taskQueue) {
    this.namespace = namespace;
    this.resources = resources;
    this.processors = processors;
    this.taskQueue = taskQueue;
  }

  /**
   * Returns the task queue used by this loader.
   *
   * @return the task queue
   */
  public TaskQueue taskQueue() {
    return taskQueue;
  }

  /**
   * Enqueues a resource at {@link #DEFAULT_PRIORITY}.
   *
   * @param relativePath the provider-local resource path
   */
  public void enqueue(String relativePath) {
    enqueue(DEFAULT_PRIORITY, relativePath);
  }

  /**
   * Enqueues a resource at the given priority.
   *
   * <p>Higher numeric priorities run later, allowing their values to override
   * values produced by lower-priority tasks.
   *
   * @param priority     the task priority
   * @param relativePath the provider-local resource path
   */
  public void enqueue(int priority, String relativePath) {
    String path = Resource.normalizePath(relativePath);
    if (path.isEmpty()) {
      throw new IllegalArgumentException("Resource path must not be empty");
    }
    Identifier id = namespace.resolve(path);
    loadedFiles.put(id, path);
    submit(priority, () -> process(id));
  }

  /**
   * Enqueues an arbitrary task at {@link #DEFAULT_PRIORITY}.
   *
   * @param task the task to run
   */
  public void enqueue(Runnable task) {
    enqueue(DEFAULT_PRIORITY, task);
  }

  /**
   * Enqueues an arbitrary task at the given priority.
   *
   * @param priority the task priority
   * @param task     the task to run
   */
  public void enqueue(int priority, Runnable task) {
    submit(priority, task);
  }

  /**
   * Recursively scans a provider-local directory and enqueues every file at
   * {@link #DEFAULT_PRIORITY}.
   *
   * @param relativeDir the provider-local directory to scan
   * @throws AssetException if the provider cannot walk or the scan fails
   */
  public void scan(String relativeDir) {
    scan(DEFAULT_PRIORITY, relativeDir);
  }

  /**
   * Recursively scans a provider-local directory and enqueues every file at
   * the given priority.
   *
   * @param priority    the priority assigned to discovered files
   * @param relativeDir the provider-local directory to scan
   * @throws AssetException if the provider cannot walk or the scan fails
   */
  public void scan(int priority, String relativeDir) {
    String directory = Resource.normalizePath(relativeDir);
    try (Stream<String> paths = resources.walk(directory)) {
      paths.forEach(path -> enqueue(priority, path));
    } catch (UnsupportedOperationException e) {
      throw new AssetException("Resource provider cannot scan: " + directory, e);
    } catch (IOException e) {
      throw new AssetException("Failed to scan resource directory: " + directory, e);
    }
  }

  /**
   * Re-enqueues a known asset at {@link #DEFAULT_PRIORITY}.
   *
   * @param id the identifier of the asset to reload
   * @return true if the asset was previously enqueued by this loader
   */
  public boolean reload(Identifier id) {
    return reload(DEFAULT_PRIORITY, id);
  }

  /**
   * Re-enqueues a known asset at the given priority.
   *
   * <p>Reloading is asynchronous and follows the same queue and completion
   * rules as an initial enqueue.
   *
   * @param priority the priority assigned to the reload task
   * @param id       the identifier of the asset to reload
   * @return true if the asset was previously enqueued by this loader
   */
  public boolean reload(int priority, Identifier id) {
    String path = loadedFiles.get(id);
    if (path == null) {
      return false;
    }
    enqueue(priority, path);
    return true;
  }

  /**
   * Executes one task from the queue, if one is available.
   */
  public void next() {
    started = true;
    taskQueue.next();
  }

  /**
   * Returns whether this loader has started and all of its submitted tasks
   * have completed.
   *
   * @return true when this loader has no pending tasks
   */
  public boolean done() {
    return started && pendingTasks == 0;
  }

  /**
   * Returns this loader's completed-task fraction.
   *
   * @return a value in {@code [0, 1]}
   */
  public float progress() {
    if (totalTasks == 0) {
      return 1;
    }
    return (float) completedTasks / totalTasks;
  }

  /**
   * Returns failures raised by processors, in occurrence order.
   *
   * @return an unmodifiable view of processor failures
   */
  public List<Throwable> failures() {
    return Collections.unmodifiableList(failures);
  }

  private void submit(int priority, Runnable action) {
    totalTasks++;
    pendingTasks++;
    taskQueue.enqueue(priority, () -> {
      try {
        action.run();
      } finally {
        completedTasks++;
        pendingTasks--;
      }
    });
  }

  private void process(Identifier id) {
    if (!processors.matches(id.path())) {
      LOGGER.debug("No processor for: {}", id);
      return;
    }
    for (Throwable failure : processors.process(id, resources)) {
      failures.add(failure);
      LOGGER.warn("Failed to load asset '{}': {}", id, failure);
    }
  }
}
