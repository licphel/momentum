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

package net.fmhi.math.dim2;

import net.fmhi.math.Vector2;

/**
 * A cubic Bézier curve defined by four control points.
 *
 * <p>The curve starts at {@code p0} (when {@code t = 0}), ends at {@code p3}
 * (when {@code t = 1}), and is shaped by the two intermediate control points
 * {@code p1} and {@code p2} which generally do not lie on the curve.
 *
 * @param p0 the start point
 * @param p1 the first control point
 * @param p2 the second control point
 * @param p3 the end point
 */
public record BezierCurve(Vector2 p0, Vector2 p1, Vector2 p2, Vector2 p3) implements Curve2D {
  /**
   * Returns a quadratic Bézier curve as a cubic with an adjusted middle
   * control point.
   *
   * @param p0 the start point
   * @param p1 the control point
   * @param p2 the end point
   * @return an equivalent cubic Bézier curve
   */
  public static BezierCurve quadratic(Vector2 p0, Vector2 p1, Vector2 p2) {
    // Elevate: cubic P0=p0, P1=(p0+2*p1)/3, P2=(2*p1+p2)/3, P3=p2
    float p1x = (p0.x() + 2f * p1.x()) / 3f;
    float p1y = (p0.y() + 2f * p1.y()) / 3f;
    float p2x = (2f * p1.x() + p2.x()) / 3f;
    float p2y = (2f * p1.y() + p2.y()) / 3f;
    return new BezierCurve(p0, new Vector2(p1x, p1y), new Vector2(p2x, p2y), p2);
  }

  /**
   * Evaluates the cubic Bézier at parameter {@code t} using
   * de Casteljau / Bernstein polynomial evaluation.
   *
   * @param t the curve parameter in {@code [0, 1]}
   * @return the point on the curve
   */
  @Override
  public Vector2 evaluate(float t) {
    float u = 1f - t;
    float uu = u * u;
    float tt = t * t;
    // Bernstein basis: B0=u^3, B1=3u^2*t, B2=3u*t^2, B3=t^3
    float b0 = uu * u;
    float b1 = 3f * uu * t;
    float b2 = 3f * u * tt;
    float b3 = tt * t;
    float x = b0 * p0.x() + b1 * p1.x() + b2 * p2.x() + b3 * p3.x();
    float y = b0 * p0.y() + b1 * p1.y() + b2 * p2.y() + b3 * p3.y();
    return new Vector2(x, y);
  }
}
