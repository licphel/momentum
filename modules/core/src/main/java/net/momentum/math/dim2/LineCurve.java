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

package net.momentum.math.dim2;

import net.momentum.math.Vector2;

/**
 * A straight-line segment between two points.
 *
 * <p>At {@code t = 0} the curve is at {@code start}; at {@code t = 1}
 * it is at {@code end}. Intermediate values are linearly interpolated.
 *
 * @param start the starting point
 * @param end   the ending point
 */
public record LineCurve(Vector2 start, Vector2 end) implements Curve2D {
  /**
   * Evaluates the line at parameter {@code t}.
   *
   * @param t the curve parameter in {@code [0, 1]}
   * @return the interpolated point
   */
  @Override
  public Vector2 evaluate(float t) {
    return Vector2.lerp(start, end, t);
  }
}
