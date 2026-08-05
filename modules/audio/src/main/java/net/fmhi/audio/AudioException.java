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

package net.fmhi.audio;

import java.io.Serial;

/**
 * Thrown to indicate an audio operation has failed at runtime.
 *
 * <p>Typical causes include failure to allocate native audio resources,
 * device loss, or other backend-level errors.
 *
 * @see Clip
 */
public class AudioException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 2026053100L;

  /**
   * Creates an {@code AudioException} with the given detail message.
   *
   * @param message description of the failure, including any relevant context
   */
  public AudioException(String message) {
    super(message);
  }

  /**
   * Creates an {@code AudioException} with the given message and cause.
   *
   * @param message description of the failure, including any relevant context
   * @param cause   the underlying cause
   */
  public AudioException(String message, Throwable cause) {
    super(message, cause);
  }
}
