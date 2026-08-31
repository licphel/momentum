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

/**
 * A destination for formatted log records: console, file, in-memory ring for tests, …
 *
 * <p>Implementations are attached to a logger via {@link Logger#addOutput(Output)} and invoked by it — synchronously
 * in a direct logger, or on the flusher thread in an {@link AsyncLogger}. In the async case implementations must be
 * safe for exclusive use by that single thread; outputs shared across loggers each get their own serialization.
 *
 * <p>Implementations should be cheap and non-throwing: a throwing {@link #write(LogRecord)} degrades logging for the
 * whole pipeline, so failures are reported to stderr and the record is skipped.
 */
public interface Output {
  /**
   * Writes one record. Called by the logger infrastructure; not meant for application code.
   *
   * @param record the fully formatted record
   */
  void write(LogRecord record);

  /**
   * Flushes any buffered bytes to the underlying sink, if applicable.
   */
  default void flush() {
  }

  /**
   * Closes the output and releases its resources. Loggers close their outputs on {@code close()}.
   */
  default void close() {
  }
}
