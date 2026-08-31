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

package net.momentum.util.logging;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entry point of the logging API: creates and caches loggers named after the calling class.
 *
 * <p>{@link #getLogger()} auto-detects the caller via {@code StackWalker} and names the logger after its simple class
 * name; {@link #getLogger(String)} takes an explicit name. Instances are cached per name, so repeated calls from the
 * same class return the same instance with its outputs intact.
 *
 * <p>By default, an {@link AsyncLogger} is created. Callers that need synchronous semantics request
 * {@code Loggers.logger(name, false)}.
 */
public final class Log {
  private static final ConcurrentHashMap<String, Logger> CACHE = new ConcurrentHashMap<>();
  private static final List<Output> DEFAULT_OUTPUTS = new ArrayList<>();

  private Log() {
  }

  /**
   * Adds a default output to future loggers, as well as to existing loggers.
   *
   * @param output the output
   */
  public static void addOutput(Output output) {
    for (Logger logger : CACHE.values()) {
      logger.addOutput(output); // Add for existing loggers
    }

    DEFAULT_OUTPUTS.add(output);
  }

  /**
   * Returns the (cached) async logger named after the calling class.
   *
   * @return a logger whose name is the caller's simple class name
   */
  public static Logger getLogger() {
    return getLogger(callerName(), true);
  }

  /**
   * Returns the (cached) sync logger named after the calling class.
   *
   * @return a synchronous logger whose name is the caller's simple class name
   */
  public static Logger getSynchedLogger() {
    return getLogger(callerName(), false);
  }

  /**
   * Resolves the first stack frame outside this package and returns its simple class name.
   *
   * @return the calling class's simple name, or {@code "unknown"} if it cannot be determined
   */
  private static String callerName() {
    return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> frames
            .filter(f -> !f.getDeclaringClass().getName().startsWith("net.momentum.logging."))
            .findFirst())
        .map(f -> f.getDeclaringClass().getSimpleName())
        .orElse("unknown");
  }

  /**
   * Returns the (cached) logger with an explicit name, async by default.
   *
   * @param name the logger name
   * @return a logger for the given name
   */
  public static Logger getLogger(String name) {
    return getLogger(name, true);
  }

  /**
   * Returns the (cached) logger with an explicit name and chosen mode.
   *
   * @param name  the logger name
   * @param async {@code true} for an {@link AsyncLogger}, {@code false} for a {@link SyncLogger}
   * @return a logger for the given name
   */
  public static Logger getLogger(String name, boolean async) {
    Logger logger = CACHE.computeIfAbsent(name, n -> async ? new AsyncLogger(n) : new SyncLogger(n));

    for (Output output : DEFAULT_OUTPUTS) {
      logger.addOutput(output);
    }

    return logger;
  }

  /**
   * Returns all loggers created so far.
   *
   * @return an unmodifiable collection
   */
  public static Collection<Logger> all() {
    return CACHE.values();
  }
}
