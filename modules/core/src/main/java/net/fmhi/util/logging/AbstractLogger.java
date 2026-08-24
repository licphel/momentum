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

import org.jspecify.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Shared machinery for logger implementations: record formatting, output list management, and caller detection.
 *
 * <p>Format: {@code [LEVEL/YY-MM-DD-hh-mm-ss] Caller.method(Caller.java:42): message}, newline-terminated. When a
 * record carries a throwable its stack trace is appended after the message.
 *
 * <p>This class is thread-safe; subclasses decide how records reach the outputs (synchronously or via a queue).
 */
abstract class AbstractLogger implements Logger {
  /** Timestamp shape without year: {@code YY-MM-DD-hh-mm-ss}. */
  static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yy-MM-dd-HH-mm-ss", Locale.ROOT);

  private final String name;
  private final CopyOnWriteArrayList<Output> outputs;
  private volatile Level threshold;

  AbstractLogger(String name) {
    this.name = Objects.requireNonNull(name, "name");
    this.outputs = new CopyOnWriteArrayList<>();
    this.threshold = Level.INFO;
  }

  /**
   * Formats one log line. Exposed for tests and for callers that need the exact wire format.
   *
   * @param level   severity
   * @param time    capture time
   * @param caller  caller description
   * @param message message text
   * @param error   optional throwable appended as a stack trace, or {@code null}
   * @return the newline-terminated formatted line
   */
  static String format(Level level, LocalDateTime time, String caller, String message,
                       @Nullable Throwable error) {
    StringBuilder sb = new StringBuilder(64 + message.length());
    sb.append('[').append(level).append('/').append(TIME_FORMAT.format(time)).append("] ")
        .append(caller).append(": ").append(message).append('\n');
    if (error != null) {
      sb.append("  at ").append(caller).append('\n');
      StringWriter sw = new StringWriter();
      error.printStackTrace(new PrintWriter(sw));
      sb.append(sw);
    }
    return sb.toString();
  }

  @Override
  public final String name() {
    return name;
  }

  @Override
  public final Logger addOutput(Output output) {
    outputs.addIfAbsent(output);
    return this;
  }

  @Override
  public final Logger removeOutput(Output output) {
    outputs.remove(output);
    return this;
  }

  @Override
  public final List<Output> outputs() {
    return List.copyOf(outputs);
  }

  @Override
  public final void threshold(Level level) {
    this.threshold = Objects.requireNonNull(level, "level");
  }

  @Override
  public final Level threshold() {
    return threshold;
  }

  @Override
  public void log(Level level, String message, @Nullable Throwable error) {
    emit(level, () -> message, error);
  }

  /**
   * Subclass hook: dispatch one formatted record to the outputs.
   *
   * @param record the record to emit
   */
  protected abstract void dispatch(LogRecord record);

  /**
   * Formats a record for the given caller and hands it to {@link #dispatch(LogRecord)}.
   *
   * <p>The message supplier is only evaluated when the threshold admits the level.
   *
   * @param level   severity
   * @param message message text
   * @param error   optional throwable, or {@code null}
   */
  protected final void emit(Level level, Supplier<String> message, @Nullable Throwable error) {
    if (!wouldLog(level)) {
      return;
    }
    LocalDateTime time = LocalDateTime.now();
    String caller = CallerResolver.resolve();
    String text = message.get();
    String line = format(level, time, caller, text, error);
    dispatch(new LogRecord(level, time, caller, text, error, line));
  }

  /**
   * Writes one record to every output, swallowing and reporting failures to stderr.
   *
   * @param record the record to write
   */
  final void writeToOutputs(LogRecord record) {
    for (Output output : outputs) {
      try {
        output.write(record);
      } catch (Exception e) {
        System.err.println("[fmhi-logging] output " + output + " failed: " + e);
      }
    }
  }

  /**
   * Flushes and closes all outputs, detaching them.
   */
  final void closeOutputs() {
    for (Output output : outputs) {
      try {
        output.flush();
        output.close();
      } catch (Exception e) {
        System.err.println("[fmhi-logging] closing output " + output + " failed: " + e);
      }
    }
    outputs.clear();
  }

  final void flushOutputs() {
    for (Output output : outputs) {
      try {
        output.flush();
      } catch (Exception e) {
        System.err.println("[fmhi-logging] flushing output " + output + " failed: " + e);
      }
    }
  }

  /**
   * Locates the first stack frame outside this package, for caller display.
   */
  static final class CallerResolver {
    private CallerResolver() {
    }

    /**
     * Resolves the calling site as {@code Class.method(Class.java:line)}.
     *
     * @return the caller description
     */
    static String resolve() {
      StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
      return walker.walk(frames -> frames
              .filter(f -> !f.getDeclaringClass().getName().startsWith("net.fmhi.logging."))
              .findFirst())
          .map(f -> f.getDeclaringClass().getSimpleName() + "." + f.getMethodName()
              + "(" + f.getFileName() + ":" + f.getLineNumber() + ")")
          .orElse("unknown");
    }
  }
}
