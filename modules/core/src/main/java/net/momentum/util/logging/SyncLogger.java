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
 * A {@link Logger} that writes to its outputs synchronously on the calling thread.
 *
 * <p>Simplest semantics — a log call returns once every output has seen the record — at the cost of doing the I/O
 * inline. Useful for tests, tools, and low-volume paths; use {@link AsyncLogger} for hot paths.
 */
public final class SyncLogger extends AbstractLogger {
  SyncLogger(String name) {
    super(name);
  }

  @Override
  protected void dispatch(LogRecord record) {
    writeToOutputs(record);
  }

  @Override
  public void flush() {
    flushOutputs();
  }

  @Override
  public void close() {
    closeOutputs();
  }
}
