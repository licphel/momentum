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

package net.fmhi.gfx;

import java.io.Serial;

/**
 * Thrown when a graphics operation fails at runtime.
 *
 * <p>Typical causes include:
 * <ul>
 *   <li>Shader compilation or linking failures
 *   <li>Framebuffer object incompleteness
 *   <li>GPU out-of-memory conditions
 *   <li>Unsupported texture format or sample count
 * </ul>
 *
 * <p>This is an unchecked exception — graphics errors are generally
 * unrecoverable at the application level and should be treated as fatal.
 */
public class GraphicsException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 2026060100L;

  /**
   * Creates a new {@code GraphicsException} with the given detail message.
   *
   * @param message description of the failure, including any relevant context (e.g. shader type, FBO status code,
   *                format name)
   */
  public GraphicsException(String message) {
    super(message);
  }

  /**
   * Creates a new {@code GraphicsException} with the given detail message and cause
   *
   * @param message description of the failure, including any relevant context (e.g. shader type, FBO status code,
   *                format name)
   * @param cause   the originating error
   */
  public GraphicsException(String message, Throwable cause) {
    super(message, cause);
  }
}
