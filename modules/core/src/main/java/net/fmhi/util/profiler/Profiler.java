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

package net.fmhi.util.profiler;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * A global, thread-safe CPU profiler for measuring the time spent in sections of code.
 *
 * <p>Bracket a section with {@link #scope(String)} (in a try-with-resources block) or
 * {@link #start(String)}/{@link #end(String)}; a section must be started and ended on the
 * same thread, in LIFO order. Profiling is switched on or off from code via
 * {@link #setEnabled(boolean)} and tuned with {@link #setOutputPath(Path)} and
 * {@link #setWarmupSeconds(int)}.
 *
 * <p>While enabled, {@link #get()} returns a profiler that records per-section aggregate
 * statistics and a bounded time series; {@link #dump()} prints a summary table and writes
 * one duration-curve PNG chart per section into the output directory. While disabled,
 * {@link #get()} returns a no-op profiler that costs nothing on the hot path.
 *
 * <p>All methods may be called concurrently.
 *
 * @see #get()
 * @see #scope(String)
 */
public interface Profiler {
  /**
   * Enables or disables profiling.
   *
   * <p>While disabled, {@link #get()} returns a no-op profiler and recording costs nothing.
   *
   * @param on {@code true} to enable profiling, {@code false} to disable it
   */
  static void setEnabled(boolean on) {
    ProfilerConfig.enabled = on;
  }

  /**
   * Returns whether profiling is currently enabled.
   *
   * @return {@code true} if profiling is enabled
   */
  static boolean enabled() {
    return ProfilerConfig.enabled;
  }

  /**
   * Sets the directory to which {@link #dump()} writes the per-section PNG charts.
   *
   * @param path the target directory; created on demand
   */
  static void setOutputPath(Path path) {
    ProfilerConfig.outputPath = path;
  }

  /**
   * Returns the directory to which {@link #dump()} writes the per-section PNG charts.
   *
   * @return the output directory, defaulting to {@code perf}
   */
  static Path outputPath() {
    return ProfilerConfig.outputPath;
  }

  /**
   * Sets how long after the profiler starts that recording is skipped.
   *
   * <p>The startup seconds are dominated by JIT, shader, and resource loading and would
   * skew the reported statistics, so recording only begins once the warm-up period has
   * elapsed.
   *
   * @param seconds the warm-up period in seconds; {@code 0} to record immediately
   * @throws IllegalArgumentException if {@code seconds} is negative
   */
  static void setWarmupSeconds(int seconds) {
    if (seconds < 0) {
      throw new IllegalArgumentException("seconds must be >= 0: " + seconds);
    }
    ProfilerConfig.warmupNanos = TimeUnit.SECONDS.toNanos(seconds);
  }

  /**
   * Returns the shared profiler.
   *
   * @return the active chart profiler when profiling is enabled, otherwise a no-op
   *     profiler that records nothing
   */
  static Profiler get() {
    return ProfilerConfig.enabled ? ChartProfiler.INSTANCE : NoopProfiler.INSTANCE;
  }

  /**
   * Begins timing a named section immediately.
   *
   * <p>The section ends when the returned scope is closed, so this pairs naturally with a
   * try-with-resources block.
   *
   * @param name the section name, used as the key when accumulating and reporting
   * @return a scope whose close ends timing for the section
   */
  default Scope scope(String name) {
    start(name);
    return new Scope(this, name);
  }

  /**
   * Begins timing a named section.
   *
   * <p>The section must be ended on the same thread with {@link #end(String)}, in LIFO order.
   *
   * @param name the section name
   */
  void start(String name);

  /**
   * Ends timing for the section most recently started on this thread.
   *
   * <p>A call whose name does not match that section is ignored.
   *
   * @param name the name of the section to end
   */
  void end(String name);

  /**
   * Prints a summary table of the recorded sections.
   *
   * <p>When profiling is enabled, also writes one duration-curve PNG chart per section
   * into the output directory.
   */
  void dump();

  /** Discards all recorded data. */
  void reset();

  /**
   * An auto-closeable handle that ends timing for a section when closed.
   *
   * <p>Obtain one via {@link Profiler#scope(String)} and use it in a try-with-resources
   * block. For a disabled profiler the scope is a shared instance whose profiler component
   * is {@code null}; closing it then does nothing.
   *
   * @param profiler the profiler that started the section, or {@code null} for the no-op
   *     scope of a disabled profiler
   * @param name the name of the timed section
   */
  record Scope(@Nullable Profiler profiler, String name) implements AutoCloseable {
    /**
     * Ends timing for the section.
     *
     * <p>Does nothing when the profiler is {@code null}, as is the case for a disabled
     * profiler's shared no-op scope.
     */
    @Override
    public void close() {
      if (profiler != null) {
        profiler.end(name);
      }
    }
  }
}
