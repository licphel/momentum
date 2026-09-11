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

package io.viki.momentum.math.curve;

import io.viki.momentum.math.Vector3;

/**
 * A Catmull-Rom spline passing through a sequence of control points.
 *
 * @param controlPoints the points the curve passes through
 */
public record SplineCurve(Vector3[] controlPoints) implements Curve {
  /**
   * Creates a Catmull-Rom spline through the given points.
   *
   * @param controlPoints the points the curve passes through; at least two required
   */
  public SplineCurve {
    if (controlPoints.length < 2) {
      throw new IllegalArgumentException("Spline requires at least 2 control points, got " + controlPoints.length);
    }
    controlPoints = controlPoints.clone();
  }

  /**
   * Evaluates one Catmull-Rom segment with standard tension 0.5.
   *
   * @param p0 the point before the segment start
   * @param p1 the segment start point
   * @param p2 the segment end point
   * @param p3 the point after the segment end
   * @param s the local parameter in {@code [0, 1]}
   * @return the interpolated point
   */
  public static Vector3 catmullRom(Vector3 p0, Vector3 p1, Vector3 p2, Vector3 p3, float s) {
    float s2 = s * s;
    float s3 = s2 * s;
    float b0 = -0.5F * s3 + s2 - 0.5F * s;
    float b1 = 1.5F * s3 - 2.5F * s2 + 1;
    float b2 = -1.5F * s3 + 2 * s2 + 0.5F * s;
    float b3 = 0.5F * s3 - 0.5F * s2;
    return new Vector3(
        b0 * p0.x() + b1 * p1.x() + b2 * p2.x() + b3 * p3.x(),
        b0 * p0.y() + b1 * p1.y() + b2 * p2.y() + b3 * p3.y(),
        b0 * p0.z() + b1 * p1.z() + b2 * p2.z() + b3 * p3.z());
  }

  /**
   * Evaluates the spline at parameter {@code t}.
   *
   * @param t the curve parameter in {@code [0, 1]}
   * @return the point on the spline
   */
  @Override
  public Vector3 evaluate(float t) {
    int n = controlPoints.length;
    if (t <= 0F) {
      return controlPoints[0];
    }
    if (t >= 1F) {
      return controlPoints[n - 1];
    }

    float mapped = t * (n - 1);
    int i = (int) mapped;
    float s = mapped - i;
    if (i < 0) {
      i = 0;
      s = 0;
    }
    if (i >= n - 1) {
      i = n - 2;
      s = 1;
    }

    Vector3 p0 = controlPoints[i > 0 ? i - 1 : 0];
    Vector3 p1 = controlPoints[i];
    Vector3 p2 = controlPoints[i + 1];
    Vector3 p3 = controlPoints[i < n - 2 ? i + 2 : n - 1];
    return catmullRom(p0, p1, p2, p3, s);
  }
}
