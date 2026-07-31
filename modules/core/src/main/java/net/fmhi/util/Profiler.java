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

package net.fmhi.util;

import org.jspecify.annotations.NullMarked;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A lightweight, global, thread-safe CPU profiler for ad-hoc performance debugging.
 *
 * <p>Use {@link #start(String)} and {@link #end(String)} to bracket sections of code, or
 * {@link #scope(String)} with try-with-resources for automatic cleanup. Call {@link #dump()}
 * at any time to print accumulated statistics to {@code System.out}.
 *
 *
 * <p>Sections nest per thread. Each call to {@link #start} must have a matching
 * call to {@link #end} on the same thread, in LIFO order.
 *
 * <p><b>Thread safety:</b> all public methods may be called concurrently.
 */
@NullMarked
public final class Profiler {
  private static final double NS_TO_MS = 1.0 / 1_000_000.0;

  private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();
  private static final ThreadLocal<Deque<Frame>> STACK =
      ThreadLocal.withInitial(ArrayDeque::new);

  private Profiler() {
  }

  /**
   * Begins timing a named section.
   *
   * <p>Must be paired with a matching {@link #end(String)} on the same thread,
   * in LIFO order. Use {@link #scope(String)} with try-with-resources for automatic cleanup.
   *
   * @param name the section name, used as a key when accumulating and reporting
   */
  public static void start(String name) {
    STACK.get().push(new Frame(name, System.nanoTime()));
  }

  /**
   * Ends timing for the most recently started section with the given name.
   *
   * <p>If the name does not match the top of the stack, the call is silently ignored.
   *
   * @param name the section name, must match the most recent {@link #start(String)}
   */
  public static void end(String name) {
    Deque<Frame> stack = STACK.get();
    if (stack.isEmpty()) {
      return;
    }
    Frame top = stack.pop();
    if (!top.name.equals(name)) {
      return;
    }
    long elapsed = System.nanoTime() - top.start;
    ENTRIES.compute(name, (k, e) -> (e == null ? new Entry() : e).record(elapsed));
  }

  /**
   * Returns an {@link AutoCloseable} scope that begins timing immediately and
   * calls {@link #end(String)} when closed.
   *
   * @param name the section name
   * @return a scope whose {@link Scope#close()} ends the timing
   */
  public static Scope scope(String name) {
    start(name);
    return new Scope(name);
  }

  /** Prints all accumulated profile data to {@code System.out}. */
  public static void dump() {
    var list = new ArrayList<>(ENTRIES.entrySet());
    if (list.isEmpty()) {
      System.out.println("[Profiler] no data");
      return;
    }
    list.sort(Comparator.<Map.Entry<String, Entry>>comparingLong(e -> e.getValue().totalNs).reversed());

    System.out.println("[Profiler]");
    System.out.printf("  %-40s %8s %8s %8s %8s %10s%n",
        "section", "calls", "min ms", "max ms", "avg ms", "total ms");
    System.out.println("  " + "-".repeat(90));
    for (var e : list) {
      Entry entry = e.getValue();
      System.out.printf("  %-40s %8d %8.3f %8.3f %8.3f %10.3f%n",
          e.getKey(), entry.count,
          entry.minNs * NS_TO_MS, entry.maxNs * NS_TO_MS,
          entry.totalNs * NS_TO_MS / entry.count, entry.totalNs * NS_TO_MS);
    }
  }

  /** Clears all accumulated data. */
  public static void reset() {
    ENTRIES.clear();
  }

  /**
   * AutoCloseable scope for try-with-resources usage.
   *
   * @param name scope name
   */
  public record Scope(String name) implements AutoCloseable {
    @Override
    public void close() {
      end(name);
    }
  }

  /** Accumulated statistics for one section. */
  private static final class Entry {
    long count;
    long totalNs;
    long minNs = Long.MAX_VALUE;
    long maxNs = Long.MIN_VALUE;

    synchronized Entry record(long ns) {
      count++;
      totalNs += ns;
      if (ns < minNs) minNs = ns;
      if (ns > maxNs) maxNs = ns;
      return this;
    }
  }

  private record Frame(String name, long start) {
  }
}
