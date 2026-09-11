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
 * A cubic Bézier curve defined by four control points.
 *
 * @param p0 the start point
 * @param p1 the first control point
 * @param p2 the second control point
 * @param p3 the end point
 */
public record BezierCurve(Vector3 p0, Vector3 p1, Vector3 p2, Vector3 p3) implements Curve {
  /**
   * Returns a quadratic Bézier curve as a cubic with an adjusted middle control point.
   *
   * @param p0 the start point
   * @param p1 the control point
   * @param p2 the end point
   * @return an equivalent cubic Bézier curve
   */
  public static BezierCurve quadratic(Vector3 p0, Vector3 p1, Vector3 p2) {
    // Elevate: P0=p0, P1=(p0+2*p1)/3, P2=(2*p1+p2)/3, P3=p2.
    Vector3 c1 = new Vector3(
        (p0.x() + 2F * p1.x()) / 3F,
        (p0.y() + 2F * p1.y()) / 3F,
        (p0.z() + 2f * p1.z()) / 3f);
    Vector3 c2 = new Vector3(
        (2f * p1.x() + p2.x()) / 3f,
        (2f * p1.y() + p2.y()) / 3f,
        (2f * p1.z() + p2.z()) / 3f);
    return new BezierCurve(p0, c1, c2, p2);
  }

  /**
   * Evaluates the cubic Bézier at parameter {@code t} using Bernstein polynomials.
   *
   * @param t the curve parameter in {@code [0, 1]}
   * @return the point on the curve
   */
  @Override
  public Vector3 evaluate(float t) {
    float u = 1f - t;
    float uu = u * u;
    float tt = t * t;
    float b0 = uu * u;
    float b1 = 3f * uu * t;
    float b2 = 3f * u * tt;
    float b3 = tt * t;
    return new Vector3(
        b0 * p0.x() + b1 * p1.x() + b2 * p2.x() + b3 * p3.x(),
        b0 * p0.y() + b1 * p1.y() + b2 * p2.y() + b3 * p3.y(),
        b0 * p0.z() + b1 * p1.z() + b2 * p2.z() + b3 * p3.z());
  }
}
