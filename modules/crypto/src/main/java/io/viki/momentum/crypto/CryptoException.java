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

package io.viki.momentum.crypto;

import java.io.Serial;

/** Reports a failure while protecting, verifying, or decrypting package data. */
public final class CryptoException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 2026103100L;

  /**
   * Creates an exception with a description of the cryptographic failure.
   *
   * @param message description of the failure
   */
  public CryptoException(String message) {
    super(message);
  }

  /**
   * Creates an exception with a description and the underlying cause.
   *
   * @param message description of the failure
   * @param cause underlying cryptographic or I/O failure
   */
  public CryptoException(String message, Throwable cause) {
    super(message, cause);
  }
}
