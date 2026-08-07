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

package net.fmhi.mod;

import java.io.Serial;

/**
 * Thrown when a mod-related operation fails — loading, dependency resolution, validation, or lifecycle errors.
 *
 * <p>This is an unchecked exception. Callers that need to handle mod failures
 * explicitly should catch it at the appropriate boundary.
 */
public class ModException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 2026060100L;

  /**
   * Creates an exception with the given message.
   *
   * @param message the detail message
   */
  public ModException(String message) {
    super(message);
  }

  /**
   * Creates an exception with the given message and cause.
   *
   * @param message the detail message
   * @param cause   the underlying cause
   */
  public ModException(String message, Throwable cause) {
    super(message, cause);
  }
}
