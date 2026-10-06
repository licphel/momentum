package io.viki.momentum.util;

import io.viki.momentum.logging.Log;
import io.viki.momentum.logging.Logger;
import org.jspecify.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects keyed wall-clock timings for a single profiling session.
 *
 * <p>Profiling is disabled by default. Enable it with {@link #setEnabled(boolean)} before opening
 * a session; disabled {@link #start(String)} and {@link #end(String)} calls return immediately.
 * Session operations belong to one thread, while the enabled flag may be changed from any thread
 * when no timing interval is running. Nested keys must finish in reverse start order, and a key
 * cannot re-enter itself.
 */
public final class Analysis implements AutoCloseable {
  private static final Logger LOGGER = Log.getLogger();

  /** Primitive sample blocks avoid per-sample allocation. */
  private static final int BLOCK_SAMPLES = 4096;
  /** Number of long values stored for each recorded sample. */
  private static final int SAMPLE_FIELDS = 6;
  /** Bound sample storage to about 48 MiB, plus one partial block per key. */
  private static final int MAX_SAMPLES = 1_000_000;
  /** Whether timing collection is enabled; volatile so changes are visible across threads. */
  private static volatile boolean enabled;
  /** The currently open profiling session, if any. */
  private static @Nullable Analysis active;
  private final Path output;
  private final long origin = System.nanoTime();
  private final Map<String, Process> processes = new LinkedHashMap<>();
  private int samples;
  private long droppedSamples;
  private long nextSpan;
  private @Nullable Process current;

  private Analysis(Path output) {
    this.output = output;
  }

  /**
   * Returns whether timing collection is enabled.
   *
   * @return {@code true} when timing collection is enabled
   */
  public static boolean isEnabled() {
    return enabled;
  }

  /**
   * Enables or disables timing collection globally.
   *
   * <p>Change this flag only when no timing interval is running so every started interval can be
   * paired with its corresponding end call.
   *
   * @param enabled whether timing collection should be enabled
   */
  public static void setEnabled(boolean enabled) {
    Analysis.enabled = enabled;
    LOGGER.info("Performance analysis is {}", enabled ? "enabled" : "disabled");
  }

  /**
   * Opens the single profiling session that receives subsequent timing intervals.
   *
   * @param output the path to receive the session report when it contains timing data
   * @return the opened session, which must be closed to finish the session
   * @throws IllegalStateException if another session is already open
   */
  public static Analysis open(Path output) {
    if (active != null) {
      throw new IllegalStateException("An analysis session is already open");
    }
    Analysis session = new Analysis(output);
    active = session;
    return session;
  }

  /**
   * Begins a named timing interval when collection is enabled.
   *
   * @param key the interval name
   * @throws IllegalStateException if collection is enabled and the key is already running
   */
  public static void start(String key) {
    if (!enabled) {
      return;
    }
    Analysis session = active;
    if (session == null) {
      return;
    }
    Process process = session.processes.computeIfAbsent(key, ignored -> new Process());
    if (process.running) {
      throw new IllegalStateException("Analysis key is already running: " + key);
    }
    process.running = true;
    process.parent = session.current;
    process.span = ++session.nextSpan;
    process.childNanos = 0;
    session.current = process;
    process.start = System.nanoTime();
  }

  /**
   * Finishes a named timing interval when collection is enabled.
   *
   * @param key the name supplied to the matching {@link #start(String)} call
   * @throws IllegalStateException if collection is enabled and the key was not started or intervals are not ended in
   *                               nested order
   */
  public static void end(String key) {
    if (!enabled) {
      return;
    }
    Analysis session = active;
    if (session == null) {
      return;
    }
    long end = System.nanoTime();
    Process process = session.processes.get(key);
    if (process == null || !process.running) {
      throw new IllegalStateException("Analysis key was not started: " + key);
    }
    if (session.current != process) {
      throw new IllegalStateException("Analysis keys must end in nested order: " + key);
    }
    long duration = end - process.start;
    Process parent = process.parent;
    session.current = parent;
    if (parent != null) {
      parent.childNanos += duration;
    }
    process.running = false;
    long interval = process.hasPrevious ? process.start - process.previousStart : 0;
    process.hasPrevious = true;
    process.previousStart = process.start;
    if (session.samples == MAX_SAMPLES) {
      session.droppedSamples++;
      return;
    }
    process.record(process.start - session.origin, duration, interval);
    session.samples++;
  }

  /**
   * Creates a reusable action wrapper that records its duration when collection is enabled.
   *
   * <p>When collection is disabled, the action runs without starting a timing interval. Exceptions
   * from the action still finish an enabled interval before propagating.
   *
   * @param key    the interval name
   * @param action the action to run
   * @return a reusable wrapper around {@code action}
   */
  public static Runnable measure(String key, Runnable action) {
    return () -> {
      if (!enabled) {
        action.run();
        return;
      }
      start(key);
      try {
        action.run();
      } finally {
        end(key);
      }
    };
  }

  /**
   * Escapes a string for use as a JSON string value.
   *
   * @param value the string to escape
   * @return the quoted JSON string
   */
  private static String quoted(String value) {
    StringBuilder result = new StringBuilder("\"");
    for (int i = 0; i < value.length(); i++) {
      char character = value.charAt(i);
      if (character == '"' || character == '\\') {
        result.append('\\').append(character);
      } else if (character < 32) {
        result.append(String.format("\\u%04x", (int) character));
      } else {
        result.append(character);
      }
    }
    return result.append('"').toString();
  }

  /**
   * Closes this session and writes its report when at least one timing key was started.
   *
   * @throws UncheckedIOException if the report cannot be written
   */
  @Override
  public void close() {
    if (active != this) {
      return;
    }
    active = null;
    if (processes.isEmpty()) {
      return;
    }
    try (BufferedWriter writer = Files.newBufferedWriter(output)) {
      writer.write("{\"unit\":\"ms\",\"nested\":true,\"dropped_samples\":" + droppedSamples + ",\"processes\":[\n");
      boolean first = true;
      for (var entry : processes.entrySet()) {
        if (!first) {
          writer.write(",\n");
        }
        first = false;
        Process process = entry.getValue();
        writer.write("{\"name\":" + quoted(entry.getKey()) + ",\"unfinished\":" + process.running + ",\"samples\":[\n");
        for (int i = 0; i < process.samples; i++) {
          long[] block = process.blocks.get(i / BLOCK_SAMPLES);
          int offset = (i % BLOCK_SAMPLES) * SAMPLE_FIELDS;
          if (i > 0) {
            writer.write(",\n");
          }
          writer.write("{\"start_ms\":" + block[offset] / 1_000_000.0
              + ",\"duration_ms\":" + block[offset + 1] / 1_000_000.0
              + ",\"interval_ms\":" + block[offset + 2] / 1_000_000.0
              + ",\"exclusive_ms\":" + block[offset + 3] / 1_000_000.0
              + ",\"span\":" + block[offset + 4]
              + ",\"parent_span\":" + block[offset + 5] + "}");
        }
        writer.write("\n]}");
      }
      writer.write("\n]}\n");
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot write analysis to " + output, exception);
    }
  }

  private static final class Process {
    private final List<long[]> blocks = new ArrayList<>();
    private int samples;
    private boolean running;
    private boolean hasPrevious;
    private long start;
    private long previousStart;
    private long span;
    private long childNanos;
    private @Nullable Process parent;

    /**
     * Appends one completed interval to this process's sample storage.
     *
     * @param start    the interval start offset from the session origin, in nanoseconds
     * @param duration the total interval duration, in nanoseconds
     * @param interval the time since this process's previous interval start, in nanoseconds
     */
    private void record(long start, long duration, long interval) {
      int block = samples / BLOCK_SAMPLES;
      if (block == blocks.size()) {
        blocks.add(new long[BLOCK_SAMPLES * SAMPLE_FIELDS]);
      }
      long[] values = blocks.get(block);
      int offset = (samples++ % BLOCK_SAMPLES) * SAMPLE_FIELDS;
      values[offset] = start;
      values[offset + 1] = duration;
      values[offset + 2] = interval;
      values[offset + 3] = duration - childNanos;
      values[offset + 4] = span;
      values[offset + 5] = parent == null ? 0 : parent.span;
    }
  }
}
