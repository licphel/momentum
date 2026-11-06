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

package io.viki.momentum.input.binding;

/**
 * Physical mouse buttons in platform index order.
 */
public enum MouseButton {
  /** Primary mouse button. */
  LEFT,
  /** Secondary mouse button. */
  RIGHT,
  /** Middle mouse button. */
  MIDDLE,
  /** Fourth mouse button. */
  BUTTON_4,
  /** Fifth mouse button. */
  BUTTON_5,
  /** Sixth mouse button. */
  BUTTON_6,
  /** Seventh mouse button. */
  BUTTON_7,
  /** Eighth mouse button. */
  BUTTON_8;

  private static final MouseButton[] VALUES = values();

  /**
   * Resolves a platform button index.
   *
   * @param index zero-based platform button index
   * @return corresponding mouse button
   * @throws IllegalArgumentException if the index is outside the supported range
   */
  public static MouseButton fromIndex(int index) {
    if (index < 0 || index >= VALUES.length) {
      throw new IllegalArgumentException("Mouse button index out of range: " + index);
    }
    return VALUES[index];
  }
}
