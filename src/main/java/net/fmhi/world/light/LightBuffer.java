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

package net.fmhi.world.light;

/**
 * A mutable RGB light color, with components typically in {@code [0, 1]}.
 *
 * <p>Implementations represent a single color sample used throughout the
 * lighting system. Values are expected to be normalized to {@code [0.0, 1.0]},
 * though higher values may temporarily appear while light propagates.
 * Thread-safety is implementation-specific.
 *
 * @see SimpleLightBuffer
 * @see Channel
 * @see LightEngine
 */
public interface LightBuffer {
  /**
   * Returns the red component of this light color.
   *
   * @return the red value, typically in {@code [0.0, 1.0]}
   */
  float r();

  /**
   * Returns the green component of this light color.
   *
   * @return the green value, typically in {@code [0.0, 1.0]}
   */
  float g();

  /**
   * Returns the blue component of this light color.
   *
   * @return the blue value, typically in {@code [0.0, 1.0]}
   */
  float b();

  /**
   * Sets the red component of this light color.
   *
   * @param v the new red value, in {@code [0.0, 1.0]}
   */
  void r(float v);

  /**
   * Sets the green component of this light color.
   *
   * @param v the new green value, in {@code [0.0, 1.0]}
   */
  void g(float v);

  /**
   * Sets the blue component of this light color.
   *
   * @param v the new blue value, in {@code [0.0, 1.0]}
   */
  void b(float v);

  /**
   * Copies the color from the given buffer into this one.
   *
   * @param buf the buffer to copy from, must not be {@code null}
   * @throws NullPointerException if {@code buf} is {@code null}
   */
  default void copy(LightBuffer buf) {
    r(buf.r());
    g(buf.g());
    b(buf.b());
  }

  /**
   * Replaces each channel with the brighter of this color and the given one.
   *
   * @param buf the buffer to compare against, must not be {@code null}
   * @throws NullPointerException if {@code buf} is {@code null}
   */
  default void max(LightBuffer buf) {
    r(Math.max(buf.r(), r()));
    g(Math.max(buf.g(), g()));
    b(Math.max(buf.b(), b()));
  }
}