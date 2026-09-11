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
 * A catenary curve, with z interpolated along the segment between its endpoints.
 *
 * @param vertex the lowest point of the catenary in the x/y plane
 * @param a the positive catenary constant
 * @param start the endpoint at the lower x bound
 * @param end the endpoint at the upper x bound
 */
public record CatenaryCurve(Vector3 vertex, float a, Vector3 start, Vector3 end) implements Curve {
  /**
   * Validates the catenary parameters.
   *
   * @param vertex the lowest point of the catenary in the x/y plane
   * @param a the positive catenary constant
   * @param start the endpoint at the lower x bound
   * @param end the endpoint at the upper x bound
   */
  public CatenaryCurve {
    if (a <= 0) {
      throw new IllegalArgumentException("Catenary constant a must be positive, got: " + a);
    }
    if (start.x() > end.x()) {
      throw new IllegalArgumentException("Catenary start x must not exceed end x");
    }
  }

  /**
   * Creates a symmetric catenary through two points at equal height,
   * with the vertex sagging by {@code sag} below their midpoint.
   *
   * @param p1 first endpoint
   * @param p2 second endpoint
   * @param sag vertical drop from the line midpoint to the vertex; positive
   * @return a new catenary curve
   */
  public static CatenaryCurve create(Vector3 p1, Vector3 p2, float sag) {
    if (sag <= 0) {
      throw new IllegalArgumentException("Sag must be positive, got: " + sag);
    }
    Vector3 start = p1.x() <= p2.x() ? p1 : p2;
    Vector3 end = p1.x() <= p2.x() ? p2 : p1;
    float midX = (p1.x() + p2.x()) * 0.5F;
    float midY = (p1.y() + p2.y()) * 0.5F;
    float midZ = (p1.z() + p2.z()) * 0.5F;
    float halfSpan = Math.abs(p2.x() - p1.x()) * 0.5F;
    Vector3 vertex = new Vector3(midX, midY + sag, midZ);
    if (halfSpan < 1E-6F) {
      return new CatenaryCurve(vertex, sag, start, end);
    }
    return new CatenaryCurve(vertex, solveA(halfSpan, sag), start, end);
  }

  /**
   * Numerically approximates the catenary constant for a symmetric segment.
   */
  private static float solveA(float halfSpan, float sag) {
    float a = (halfSpan * halfSpan) / (2F * sag);
    if (a < 1E-6F) {
      a = 1E-6F;
    }
    for (int i = 0; i < 10; i++) {
      float expTerm = (float) Math.exp(halfSpan / a);
      float coshTerm = (expTerm + 1.0F / expTerm) * 0.5F;
      float sinhTerm = (expTerm - 1.0F / expTerm) * 0.5F;
      float f = a * coshTerm - a - sag;
      float df = coshTerm - (halfSpan / a) * sinhTerm - 1.0F;
      if (Math.abs(df) < 1E-10F) {
        break;
      }
      float delta = f / df;
      a -= delta;
      if (Math.abs(delta) < 1E-6F) {
        break;
      }
    }
    return a;
  }

  /**
   * Evaluates the catenary at parameter {@code t}.
   *
   * @param t the curve parameter in {@code [0, 1]}
   * @return the point on the catenary
   */
  @Override
  public Vector3 evaluate(float t) {
    float x = start.x() + (end.x() - start.x()) * t;
    float relX = (x - vertex.x()) / a;
    float y = a * (float) Math.cosh(relX) + vertex.y() - a;
    float z = start.z() + (end.z() - start.z()) * t;
    return new Vector3(x, y, z);
  }
}
