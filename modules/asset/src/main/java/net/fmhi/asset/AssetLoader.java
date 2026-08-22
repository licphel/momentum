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

package net.fmhi.asset;

import net.fmhi.util.Identifier;
import net.fmhi.util.Namespace;
import net.fmhi.util.logging.Log;
import net.fmhi.util.logging.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Per-mod, frame-by-frame asset loading pipeline.
 *
 * <p>An {@code AssetLoader} collects file paths and processes them one at a
 * time via {@link #next()}. Callers drive the pipeline each frame and can
 * poll {@link #done()} and {@link #progress()} to display loading progress.
 *
 * <p>Subordinate loaders can be enqueued via
 * {@link #enqueue(AssetLoader)}. The root loader drains its children
 * automatically.
 */
public final class AssetLoader {
  private static final Logger LOGGER = Log.getLogger();

  private final Namespace namespace;
  private final Path basePath;
  private final List<Processor> processors = new ArrayList<>();
  private final Deque<Runnable> taskQueue = new ArrayDeque<>();
  private final List<AssetLoader> subordinates = new ArrayList<>();
  private int totalTasks;
  private int completedTasks;
  private boolean started;

  /**
   * Creates an asset loader for the given mod namespace.
   *
   * @param namespace the mod namespace, used to build {@link Identifier}s
   * @param basePath  the root directory to scan, relative to the working directory
   */
  public AssetLoader(Namespace namespace, Path basePath) {
    this.namespace = namespace;
    this.basePath = basePath;
  }

  /**
   * Registers a processor for files matching the given predicate.
   *
   * <p>The processor receives the resolved {@link Identifier} and the
   * absolute file path. Processors should store loaded assets via
   * {@link Assets#set(Identifier, Object)}.
   *
   * @param filter    decides which files this processor handles
   * @param processor the loading callback
   */
  public void addProcessor(Predicate<String> filter,
                           BiConsumer<Identifier, Path> processor) {
    processors.add(new Processor(filter, processor));
  }

  /**
   * Enqueues a single file for processing.
   *
   * @param relativePath the file path relative to the base directory
   */
  public void enqueue(String relativePath) {
    Path fullPath = basePath.resolve(relativePath);
    taskQueue.add(() -> {
      String idPath = relativePath.replace('\\', '/');
      Identifier id = namespace.resolve(idPath);
      boolean handled = false;
      for (Processor p : processors) {
        if (p.filter.test(idPath)) {
          p.processor.accept(id, fullPath);
          handled = true;
        }
      }
      if (!handled) {
        LOGGER.debug("No processor for: {}", id);
      }
      completedTasks++;
    });
    totalTasks++;
  }

  /**
   * Enqueues a subordinate loader.
   *
   * <p>Subordinate loaders are drained after the main queue is empty.
   *
   * @param sub the subordinate loader
   */
  public void enqueue(AssetLoader sub) {
    subordinates.add(sub);
    totalTasks += sub.totalTasks;
  }

  /**
   * Enqueues an arbitrary task for execution.
   *
   * @param task the task to enqueue
   */
  public void enqueue(Runnable task) {
    taskQueue.add(task);
    totalTasks++;
  }

  /**
   * Recursively scans a directory relative to the base path and enqueues
   * every file found.
   *
   * @param relativeDir the directory to scan, relative to the base path
   * @throws RuntimeException if an I/O error occurs during scanning
   */
  public void scan(String relativeDir) {
    Path dir = basePath.resolve(relativeDir);
    if (!Files.isDirectory(dir)) {
      LOGGER.warn("Scan directory not found: {}", dir);
      return;
    }
    try {
      Files.walkFileTree(dir, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              String relative = basePath.relativize(file).toString();
              enqueue(relative);
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      throw new RuntimeException("Failed to scan directory: " + dir, e);
    }
  }

  /**
   * Executes one task from the queue.
   *
   * <p>Call this repeatedly (e.g. once per frame) until {@link #done()}
   * returns {@code true}. Subordinate loaders are drained automatically
   * once the main queue is empty.
   */
  public void next() {
    if (!started) {
      started = true;
    }
    Runnable task = taskQueue.pollFirst();
    if (task != null) {
      task.run();
      return;
    }
    // Drain subordinates
    for (AssetLoader sub : subordinates) {
      if (!sub.done()) {
        sub.next();
        completedTasks = countCompleted();
        return;
      }
    }
  }

  /**
   * Returns whether all tasks and subordinate loaders have completed.
   *
   * @return {@code true} when loading is finished
   */
  public boolean done() {
    if (!started) {
      return false;
    }
    if (!taskQueue.isEmpty()) {
      return false;
    }
    for (AssetLoader sub : subordinates) {
      if (!sub.done()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns the loading progress as a fraction between {@code 0} and {@code 1}.
   *
   * @return a value in {@code [0, 1]}, or {@code 1} if there are no tasks
   */
  public float progress() {
    if (totalTasks == 0) {
      return 1;
    }
    int completed = countCompleted();
    return (float) completed / totalTasks;
  }

  private int countCompleted() {
    int c = completedTasks;
    for (AssetLoader sub : subordinates) {
      c += sub.completedTasks;
    }
    return c;
  }

  private record Processor(Predicate<String> filter,
                           BiConsumer<Identifier, Path> processor) {
  }
}
