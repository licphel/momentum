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

package io.viki.momentum.math.dim2;

import io.viki.momentum.math.Vector2;

/**
 * A Catmull-Rom spline passing through a sequence of control points.
 *
 * <p>The curve passes through every control point. Between each adjacent pair
 * of points, a cubic Catmull-Rom segment is interpolated using the four
 * surrounding points. At the ends, the boundary point is repeated to handle
 * the missing neighbors.
 *
 * <p>At least two control points are required; with exactly two points the
 * spline degenerates to a straight line.
 *
 * @param controlPoints the points the curve passes through
 */
public record SplineCurve(Vector2[] controlPoints) implements Curve2D {
  /**
   * Creates a Catmull-Rom spline through the given points.
   *
   * @param controlPoints the points the curve passes through; at least 2 required.
   *                      The array is defensively copied.
   */
  public SplineCurve {
    if (controlPoints.length < 2) {
      throw new IllegalArgumentException("Spline requires at least 2 control points, got " + controlPoints.length);
    }
    controlPoints = controlPoints.clone();
  }

  /**
   * Evaluates a single Catmull-Rom segment given four surrounding control
   * points and a local parameter.
   *
   * @param p0 the point before the segment start
   * @param p1 the segment start point
   * @param p2 the segment end point
   * @param p3 the point after the segment end
   * @param s  the local parameter in {@code [0, 1]}
   * @return the interpolated point
   */
  public static Vector2 catmullRom(Vector2 p0, Vector2 p1, Vector2 p2, Vector2 p3, float s) {
    float s2 = s * s;
    float s3 = s2 * s;
    // Catmull-Rom basis with tension 0.5 (standard)
    float b0 = -0.5F * s3 + s2 - 0.5F * s;
    float b1 = 1.5F * s3 - 2.5F * s2 + 1;
    float b2 = -1.5F * s3 + 2 * s2 + 0.5F * s;
    float b3 = 0.5F * s3 - 0.5F * s2;
    float x = b0 * p0.x() + b1 * p1.x() + b2 * p2.x() + b3 * p3.x();
    float y = b0 * p0.y() + b1 * p1.y() + b2 * p2.y() + b3 * p3.y();
    return new Vector2(x, y);
  }

  /**
   * Evaluates the spline at parameter {@code t}.
   *
   * <p>The parameter space {@code [0, 1]} is mapped uniformly across all
   * segments. The segment index is determined by {@code floor(t * (n − 1))}
   * and clamped for safety.
   *
   * @param t the curve parameter in {@code [0, 1]}
   * @return the point on the spline
   */
  @Override
  public Vector2 evaluate(float t) {
    int n = controlPoints.length;
    // Clamp t to [0, 1]
    if (t <= 0f) {
      return controlPoints[0];
    }
    if (t >= 1f) {
      return controlPoints[n - 1];
    }

    // Map t to segment index and local parameter s
    float mapped = t * (n - 1);
    int i = (int) mapped;
    float s = mapped - i;

    // Clamp segment index
    if (i < 0) {
      i = 0;
      s = 0;
    }
    if (i >= n - 1) {
      i = n - 2;
      s = 1;
    }

    // Surrounding points (with boundary repetition)
    Vector2 p0 = controlPoints[i > 0 ? i - 1 : 0];
    Vector2 p1 = controlPoints[i];
    Vector2 p2 = controlPoints[i + 1];
    Vector2 p3 = controlPoints[i < n - 2 ? i + 2 : n - 1];

    return catmullRom(p0, p1, p2, p3, s);
  }
}
