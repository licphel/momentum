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

import java.io.PrintStream;

/**
 * An {@link Output} writing to a {@link PrintStream}: {@link System#out} or {@link System#err} for the console.
 *
 * <p>Each write flushes, so console lines appear immediately even under heavy buffering. The stream is not closed on
 * {@link #close()} — closing {@code System.out} would kill the JVM's own output.
 */
public final class StdAccess implements Output {
  private final PrintStream out;
  private final boolean flushEach;

  /**
   * Creates an output writing to {@link System#out}.
   */
  public StdAccess() {
    this(System.out);
  }

  /**
   * Creates an output writing to the given stream.
   *
   * @param out the destination stream; typically {@code System.out} or {@code System.err}
   */
  public StdAccess(PrintStream out) {
    this.out = out;
    this.flushEach = out == System.out || out == System.err;
  }

  @Override
  public void write(LogRecord record) {
    out.print(record.line());
    if (flushEach) {
      out.flush();
    }
  }

  @Override
  public void flush() {
    out.flush();
  }
}
