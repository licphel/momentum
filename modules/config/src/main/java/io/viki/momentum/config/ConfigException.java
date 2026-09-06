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

package io.viki.momentum.config;

import java.io.Serial;

/**
 * Thrown when a configuration operation fails.
 *
 * <p>This exception covers file I/O errors, malformed JSON, and other
 * configuration-related failures.
 */
public class ConfigException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 2026072500L;

  /**
   * Creates a new exception with the given message.
   *
   * @param message the error description
   */
  public ConfigException(String message) {
    super(message);
  }

  /**
   * Creates a new exception with the given message and cause.
   *
   * @param message the error description
   * @param cause   the underlying exception
   */
  public ConfigException(String message, Throwable cause) {
    super(message, cause);
  }
}
