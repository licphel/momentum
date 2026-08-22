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

/**
 * Severity levels ordered from most verbose to most severe: {@code DEBUG < INFO < WARN < FATAL}.
 *
 * <p>Records below an output's threshold are discarded before formatting, so a {@code DEBUG} flood costs one enum
 * comparison per output regardless of message cost.
 */
public enum Level {
  /** Fine-grained diagnostic tracing. */
  DEBUG,
  /** Routine operational messages. */
  INFO,
  /** Something unexpected but recoverable. */
  WARN,
  /** Unrecoverable; the process is likely about to die. */
  FATAL;

  /**
   * Returns whether this level is at least as severe as {@code other}.
   *
   * @param other the threshold to compare against
   * @return {@code true} if this level would be logged at the given threshold
   */
  public boolean covers(Level other) {
    return ordinal() <= other.ordinal();
  }
}
