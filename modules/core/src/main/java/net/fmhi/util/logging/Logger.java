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

package net.fmhi.util.logging;

import net.fmhi.util.QuickFmt;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Logs formatted records to a set of {@link Output}s.
 *
 * <p>Line format: {@code [LEVEL/YY-MM-DD-hh-mm-ss] Caller.method(Caller.java:42): message}. The caller is resolved
 * automatically from the stack trace at log time, skipping frames belonging to this package so the reported site is
 * the user code that invoked logging, not the framework itself.
 *
 * <p>Records are filtered by a global threshold ({@link #threshold()}) before any formatting work happens.
 *
 * <p>Obtain instances from {@link Log}, which auto-detects the caller's class name to name the logger.
 *
 * @see AsyncLogger
 * @see Log
 */
public interface Logger extends AutoCloseable {
  /**
   * Returns the logger's name, used for identification.
   *
   * @return the logger name
   */
  String name();

  /**
   * Attaches an output to this logger. Double-attaching the same output is a no-op.
   *
   * @param output the output to add
   * @return this logger, for chaining
   */
  Logger addOutput(Output output);

  /**
   * Detaches an output from this logger.
   *
   * @param output the output to remove
   * @return this logger, for chaining
   */
  Logger removeOutput(Output output);

  /**
   * Returns the outputs currently attached to this logger.
   *
   * @return an unmodifiable snapshot of the outputs
   */
  List<Output> outputs();

  /**
   * Sets the minimum level that will be logged; cheaper records are dropped before formatting.
   *
   * @param level the new threshold
   */
  void threshold(Level level);

  /**
   * Returns the current minimum level.
   *
   * @return the threshold
   */
  Level threshold();

  /**
   * Returns whether records at the given level would currently be logged.
   *
   * @param level the level to test
   * @return {@code true} if a record at this level would be emitted
   */
  default boolean wouldLog(Level level) {
    return level.covers(threshold());
  }

  /**
   * Logs at {@link Level#DEBUG}.
   *
   * @param message the message
   * @param args    message formatter args
   */
  default void debug(String message, Object... args) {
    log(Level.DEBUG, QuickFmt.format(message, args), null);
  }

  /**
   * Logs at {@link Level#INFO}.
   *
   * @param message the message
   * @param args    message formatter args
   */
  default void info(String message, Object... args) {
    log(Level.INFO, QuickFmt.format(message, args), null);
  }

  /**
   * Logs at {@link Level#WARN}.
   *
   * @param message the message
   * @param args    message formatter args
   */
  default void warn(String message, Object... args) {
    log(Level.WARN, QuickFmt.format(message, args), null);
  }

  /**
   * Logs at {@link Level#FATAL}.
   *
   * @param message the message
   * @param args    message formatter args
   */
  default void fatal(String message, Object... args) {
    log(Level.FATAL, QuickFmt.format(message, args), null);
  }

  /**
   * Logs at {@link Level#DEBUG} with an associated throwable.
   *
   * @param message the message
   * @param error   the throwable to attach
   */
  default void debugExc(String message, Throwable error) {
    log(Level.DEBUG, message, error);
  }

  /**
   * Logs at {@link Level#INFO} with an associated throwable.
   *
   * @param message the message
   * @param error   the throwable to attach
   */
  default void infoExc(String message, Throwable error) {
    log(Level.INFO, message, error);
  }

  /**
   * Logs at {@link Level#WARN} with an associated throwable.
   *
   * @param message the message
   * @param error   the throwable to attach
   */
  default void warnExc(String message, Throwable error) {
    log(Level.WARN, message, error);
  }

  /**
   * Logs at {@link Level#FATAL} with an associated throwable.
   *
   * @param message the message
   * @param error   the throwable to attach
   */
  default void fatalExc(String message, Throwable error) {
    log(Level.FATAL, message, error);
  }

  /**
   * Logs a record at the given level, resolving the caller automatically.
   *
   * @param level   severity
   * @param message the message text
   * @param error   optional associated throwable, or {@code null}
   */
  void log(Level level, String message, @Nullable Throwable error);

  /**
   * Flushes all attached outputs.
   */
  void flush();

  /**
   * Flushes and closes all attached outputs, detaching them.
   */
  @Override
  void close();
}
