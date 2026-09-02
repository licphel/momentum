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

package net.momentum.asset;

import net.momentum.util.Identifier;
import net.momentum.util.Namespace;
import net.momentum.util.logging.Log;
import net.momentum.util.logging.Logger;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Per-namespace, phased, frame-by-frame asset loading pipeline.
 *
 * <p>An {@code AssetLoader} collects resource paths and processes them one at
 * a time via {@link #next()}. Callers drive the pipeline each frame and can
 * poll {@link #done()} and {@link #progress()} to display loading progress.
 *
 * <p>Resources are located through an {@link AssetFinder}, which maps the
 * loader's namespace to a base {@link Path}: a directory in development, or
 * a JAR file whose entries are exposed as a directory tree of an opened
 * {@code jar:} {@link java.nio.file.FileSystem}. Both are scanned and
 * streamed through the same code path — {@link Files#walkFileTree} over a
 * path that is either a real directory or an archive root — so a single
 * loader serves development and packaged runs without branching on where
 * the content physically lives.
 *
 * <p><b>Finder lifecycle</b>: JAR file systems are opened and owned by the
 * finder, so paths handed to processors stay valid only while the finder is
 * open. A loader constructed with a plain base path wraps it in a private
 * single-namespace finder and closes it in {@link #close()}; a loader
 * constructed with a shared finder leaves closing to the caller.
 *
 * <p><b>Phases</b>: processors registered via {@link #addProcessor(int, Predicate, BiConsumer)}
 * belong to a phase. All files of an earlier phase are processed before any
 * file of a later phase, so a phase-N asset may safely depend on phase-N-1
 * assets (e.g. texture atlases after individual textures).
 *
 * <p><b>Priority</b>: the loader's priority (constructor) is metadata used by
 * the assembling party (e.g. a mod loader) to order loaders — higher-priority
 * loaders run last so their values win when multiple loaders provide the same
 * asset. The loader itself does not act on the value.
 *
 * <p><b>Failures</b>: exceptions thrown by processors are collected in
 * {@link #failures()} and do not abort the remaining queue.
 *
 * <p><b>Hot reload</b>: {@link #reload(Identifier)} re-processes the file a
 * given asset was loaded from, which updates the store and notifies {@link Ref}
 * listeners.
 *
 * <p>Subordinate loaders can be enqueued via
 * {@link #enqueue(AssetLoader)}. The root loader drains its children
 * automatically.
 */
public final class AssetLoader implements AutoCloseable {
  private static final Logger LOGGER = Log.getLogger();

  private final Namespace namespace;
  private final AssetFinder finder;
  private final int priority;
  private final boolean ownsFinder;
  private final List<Processor> processors = new ArrayList<>();
  private final List<Deque<Runnable>> phaseQueues = new ArrayList<>();
  private final List<AssetLoader> subordinates = new ArrayList<>();
  private final List<Throwable> failures = new ArrayList<>();
  private final Map<Identifier, String> loadedFiles = new HashMap<>();
  private int totalTasks;
  private int completedTasks;
  private boolean started;
  private int currentPhase;

  /**
   * Creates an asset loader for the given namespace at the default priority
   * of {@code 0}.
   *
   * <p>The base — a directory or a JAR file — is wrapped in a private
   * single-namespace {@link NamespacedAssetFinder}; the loader owns and
   * closes it.
   *
   * @param namespace the resource namespace, used to build {@link Identifier}s
   * @param base      the root directory or JAR file to scan
   */
  public AssetLoader(Namespace namespace, Path base) {
    this(namespace, base, 0);
  }

  /**
   * Creates an asset loader for the given namespace at the given priority.
   *
   * <p>The priority is metadata for the assembling party: run loaders in
   * ascending priority order so that higher-priority loaders execute last
   * and their values win on conflicts.
   *
   * @param namespace the resource namespace, used to build {@link Identifier}s
   * @param base      the root directory or JAR file to scan
   * @param priority  the loader priority; higher wins on conflicts
   */
  public AssetLoader(Namespace namespace, Path base, int priority) {
    this(namespace, new NamespacedAssetFinder(Map.of(namespace.name(), base)), priority, true);
  }

  /**
   * Creates an asset loader for the given namespace at the default priority
   * of {@code 0}, locating resources through the given finder.
   *
   * <p>The loader references the finder for the namespace's base root. The
   * caller owns the finder's lifecycle: {@link #close()} on this loader is a
   * no-op and the finder must be closed once no loader or consumer needs it.
   *
   * @param namespace the resource namespace, used to build {@link Identifier}s
   * @param finder    the finder locating the namespace's resources
   */
  public AssetLoader(Namespace namespace, AssetFinder finder) {
    this(namespace, finder, 0);
  }

  /**
   * Creates an asset loader for the given namespace at the given priority,
   * locating resources through the given finder.
   *
   * <p>See {@link #AssetLoader(Namespace, AssetFinder)} for ownership.
   *
   * @param namespace the resource namespace, used to build {@link Identifier}s
   * @param finder    the finder locating the namespace's resources
   * @param priority  the loader priority; higher wins on conflicts
   */
  public AssetLoader(Namespace namespace, AssetFinder finder, int priority) {
    this(namespace, finder, priority, false);
  }

  private AssetLoader(Namespace namespace, AssetFinder finder, int priority, boolean ownsFinder) {
    this.namespace = namespace;
    this.finder = finder;
    this.priority = priority;
    this.ownsFinder = ownsFinder;
  }

  /**
   * Returns the loader priority.
   *
   * @return the priority assigned at construction
   */
  public int priority() {
    return priority;
  }

  /**
   * Registers a processor for files matching the given predicate.
   *
   * <p>The processor receives the resolved {@link Identifier} and the
   * resource {@link Path} — on the default file system for directory bases,
   * inside the finder's {@code jar:} file system for archive bases.
   * Processors open what they need and should store loaded assets via
   * {@link Assets#set(Identifier, Object)}.
   *
   * @param filter    decides which files this processor handles
   * @param processor the loading callback
   */
  public void addProcessor(Predicate<String> filter, BiConsumer<Identifier, Path> processor) {
    addProcessor(0, filter, processor);
  }

  /**
   * Registers a phase-N processor for files matching the given predicate.
   *
   * <p>All files whose lowest matching phase is N are processed after every
   * file of phases {@code 0..N-1}, so phase-N loaders may consume assets
   * produced by earlier phases.
   *
   * @param phase     the phase index; lower phases complete first
   * @param filter    decides which files this processor handles
   * @param processor the loading callback
   */
  public void addProcessor(int phase, Predicate<String> filter, BiConsumer<Identifier, Path> processor) {
    if (phase < 0) {
      throw new IllegalArgumentException("Phase must be non-negative, got " + phase);
    }
    processors.add(new Processor(phase, filter, processor));
  }

  /**
   * Enqueues a single file for processing.
   *
   * <p>The file is placed in the queue of the lowest phase whose processor
   * matches it; files matching no processor fall into phase {@code 0}.
   *
   * @param relativePath the file path relative to the namespace's base root
   */
  public void enqueue(String relativePath) {
    String idPath = AssetFinder.standardizePath(relativePath);
    Identifier id = namespace.resolve(idPath);
    Path fullPath = base().resolve(idPath);

    int phase = lowestMatchingPhase(idPath);
    queueAt(Math.max(phase, currentPhase)).add(() -> {
      runProcessors(id, idPath, fullPath);
      completedTasks++;
    });
    loadedFiles.put(id, idPath);
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
  }

  /**
   * Enqueues an arbitrary task for execution.
   *
   * <p>The task is placed in the phase-0 queue.
   *
   * @param task the task to enqueue
   */
  public void enqueue(Runnable task) {
    queueAt(Math.max(0, currentPhase)).add(task);
    totalTasks++;
  }

  /**
   * Recursively scans a directory relative to the namespace's base root and
   * enqueues every file found.
   *
   * <p>Directory bases are walked on the default file system; archive bases
   * are walked inside their {@code jar:} file system — the same code path.
   *
   * @param relativeDir the directory to scan, relative to the base root
   * @throws AssetException if the archive cannot be opened or scanning fails
   */
  public void scan(String relativeDir) {
    Path base = base();
    Path dir = relativeDir.isEmpty() ? base : base.resolve(relativeDir);
    if (!Files.isDirectory(dir)) {
      LOGGER.warn("Scan directory not found: {}", dir);
      return;
    }
    try {
      Files.walkFileTree(dir, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              enqueue(base.relativize(file).toString());
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      throw new AssetException("Failed to scan directory: " + dir, e);
    }
  }

  /**
   * Re-processes the file an asset was loaded from, then returns whether
   * the asset was known to this loader.
   *
   * <p>Processing runs synchronously; matching processors re-run and
   * {@link Assets#set(Identifier, Object)} updates the store, notifying all
   * {@link Ref} listeners.
   *
   * @param id the identifier of the asset to reload
   * @return {@code true} if the asset was loaded by this loader
   */
  public boolean reload(Identifier id) {
    String idPath = loadedFiles.get(id);
    if (idPath == null) {
      return false;
    }
    runProcessors(id, idPath, base().resolve(idPath));
    return true;
  }

  /**
   * Executes one task from the queue.
   *
   * <p>Call this repeatedly (e.g. once per frame) until {@link #done()}
   * returns {@code true}. Tasks run in phase order; subordinate loaders are
   * drained automatically once the main queue is empty.
   */
  public void next() {
    if (!started) {
      started = true;
    }
    while (currentPhase < phaseQueues.size()) {
      Deque<Runnable> queue = phaseQueues.get(currentPhase);
      Runnable task = queue.pollFirst();
      if (task != null) {
        task.run();
        return;
      }
      currentPhase++;
    }
    // Drain subordinates
    for (AssetLoader sub : subordinates) {
      if (!sub.done()) {
        sub.next();
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
    for (int i = currentPhase; i < phaseQueues.size(); i++) {
      if (!phaseQueues.get(i).isEmpty()) {
        return false;
      }
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
   * <p>Counts are summed live across subordinates, so progress never exceeds
   * {@code 1} even when subordinates enqueue more work while running.
   *
   * @return a value in {@code [0, 1]}, or {@code 1} if there are no tasks
   */
  public float progress() {
    int total = totalTasks;
    int completed = completedTasks;
    for (AssetLoader sub : subordinates) {
      total += sub.totalTasks;
      completed += sub.completedTasks;
    }
    if (total == 0) {
      return 1;
    }
    return (float) completed / total;
  }

  /**
   * Returns the failures collected while processing files, in occurrence
   * order.
   *
   * @return an unmodifiable view of the collected failures
   */
  public List<Throwable> failures() {
    return Collections.unmodifiableList(failures);
  }

  /**
   * Closes the private finder created from a base path, if any. Paths and
   * streams obtained from this loader become invalid.
   *
   * <p>Loaders constructed with an {@link AssetFinder} do not close it; the
   * caller owns that finder's lifecycle.
   */
  @Override
  public void close() {
    if (ownsFinder) {
      finder.close();
    }
  }

  /**
   * Returns the namespace's base root as located by the finder.
   */
  private Path base() {
    return finder.base(namespace);
  }

  private int lowestMatchingPhase(String idPath) {
    int phase = 0;
    boolean matched = false;
    for (Processor p : processors) {
      if (p.filter.test(idPath)) {
        phase = matched ? Math.min(phase, p.phase) : p.phase;
        matched = true;
      }
    }
    return phase;
  }

  private Deque<Runnable> queueAt(int phase) {
    while (phaseQueues.size() <= phase) {
      phaseQueues.add(new ArrayDeque<>());
    }
    return phaseQueues.get(phase);
  }

  private void runProcessors(Identifier id, String idPath, Path fullPath) {
    boolean handled = false;
    for (Processor p : processors) {
      if (p.filter.test(idPath)) {
        handled = true;
        try {
          p.processor.accept(id, fullPath);
        } catch (RuntimeException e) {
          failures.add(e);
          LOGGER.warn("Failed to load asset '{}': {}", id, e);
        }
      }
    }
    if (!handled) {
      LOGGER.debug("No processor for: {}", id);
    }
  }

  private record Processor(int phase,
                           Predicate<String> filter,
                           BiConsumer<Identifier, Path> processor) {
  }
}
