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
 * A catenary curve — the shape of a hanging chain under uniform gravity.
 *
 * <p>The curve follows {@code y = a · cosh((x − vertexX) / a) + vertexY − a}
 * for {@code x} in {@code [startX, endX]}. The parameter {@code a} controls the
 * curvature: larger values produce a flatter curve, smaller values a more
 * pronounced sag.
 *
 * <p>The vertex {@code (vertexX, vertexY)} is the lowest point of the curve
 * (assuming {@code a > 0}).
 *
 * @param vertexX the x-coordinate of the catenary vertex (lowest point)
 * @param vertexY the y-coordinate of the catenary vertex
 * @param a       the catenary constant
 * @param startX  the minimum x to evaluate
 * @param endX    the maximum x to evaluate
 */
public record CatenaryCurve(float vertexX, float vertexY, float a,
                            float startX, float endX) implements Curve2D {
  /**
   * Creates a catenary curve segment.
   *
   * @param vertexX the x-coordinate of the vertex (lowest point)
   * @param vertexY the y-coordinate of the vertex
   * @param a       the catenary constant; must be positive
   * @param startX  the minimum x to evaluate; must not exceed {@code endX}
   * @param endX    the maximum x to evaluate
   */
  public CatenaryCurve {
    if (startX > endX) {
      throw new IllegalArgumentException("startX must not exceed endX");
    }
  }

  /**
   * Creates a symmetrical catenary curve through two points at equal height,
   * with the vertex sagging by {@code sag} below their midpoint.
   *
   * @param p1  first endpoint
   * @param p2  second endpoint
   * @param sag the vertical drop from the line midpoint to the vertex; must be positive
   * @return a new catenary curve
   */
  public static CatenaryCurve create(Vector2 p1, Vector2 p2, float sag) {
    if (sag <= 0) {
      throw new IllegalArgumentException("Sag must be positive, got: " + sag);
    }
    float midX = (p1.x() + p2.x()) * 0.5F;
    float midY = (p1.y() + p2.y()) * 0.5F;
    float dx = Math.abs(p2.x() - p1.x()) * 0.5F;
    if (dx < 1E-6F) {
      // Nearly vertical — return a degenerate curve
      return new CatenaryCurve(midX, midY + sag, sag, p1.x(), p2.x());
    }
    // Numerically solve for 'a' such that the curve passes through (p2.x, p2.y)
    // relative to vertex at (midX, midY + sag)
    float a = solveA(dx, sag);
    return new CatenaryCurve(midX, midY + sag, a,
        Math.min(p1.x(), p2.x()), Math.max(p1.x(), p2.x()));
  }

  /**
   * Numerically approximates the catenary constant {@code a} for a symmetric
   * catenary where the vertex sags by {@code sag} at horizontal distance
   * {@code halfSpan} from the vertex.
   */
  private static float solveA(float halfSpan, float sag) {
    // Solve: a * cosh(halfSpan / a) - a = sag
    // Using Newton's method with initial guess a ≈ halfSpan^2 / (2*sag) (parabolic approx)
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
  public Vector2 evaluate(float t) {
    float x = startX + (endX - startX) * t;
    float relX = (x - vertexX) / a;
    float y = a * (float) Math.cosh(relX) + vertexY - a;
    return new Vector2(x, y);
  }
}
