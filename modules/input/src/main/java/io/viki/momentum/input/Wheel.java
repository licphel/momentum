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

package io.viki.momentum.input;

/**
 * A two-dimensional relative or persistent input source. Thread safety belongs to the implementation.
 * Deltas are changes accumulated since the frame boundary, never velocities.
 */
public interface Wheel extends Input {
  /**
   * Returns the accumulated horizontal change for this frame.
   *
   * @return horizontal delta
   */
  double deltaX();

  /**
   * Returns the accumulated vertical change for this frame.
   *
   * @return vertical delta
   */
  double deltaY();

  /**
   * Returns the retained horizontal position, or zero for a relative-only source.
   *
   * @return horizontal position
   */
  double x();

  /**
   * Returns the retained vertical position, or zero for a relative-only source.
   *
   * @return vertical position
   */
  double y();

  /**
   * Reports whether positions persist across frames;
   * consumers integrate these with elapsed time when scrolling.
   *
   * @return whether positions persist
   */
  boolean persistent();
}
