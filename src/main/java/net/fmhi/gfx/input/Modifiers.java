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

package net.fmhi.gfx.input;

/**
 * Modifier key bitmask constants aligned with GLFW modifier flags.
 *
 * <p>Combine with bitwise OR: {@code Modifiers.SHIFT | Modifiers.CONTROL}.
 * Pass {@link #ANY} to {@code Key.transitioned(int)} to match regardless of modifier state.
 */
public final class Modifiers {
  /** Sentinel value that matches any modifier combination. */
  public static final int ANY = -1;
  public static final int NONE = 0;
  public static final int SHIFT = 0x1;
  public static final int CONTROL = 0x2;
  public static final int ALT = 0x4;
  public static final int SUPER = 0x8;
  public static final int CAPS_LOCK = 0x10;
  public static final int NUM_LOCK = 0x20;

  private Modifiers() {
  }
}
