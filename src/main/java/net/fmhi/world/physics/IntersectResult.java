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

package net.fmhi.world.physics;

import net.fmhi.math.Vector2;
import org.jspecify.annotations.NullMarked;

/**
 * Result of a SAT (Separating Axis Theorem) intersection test between two
 * convex polygons.
 *
 * <p>When {@link #intersects()} is {@code true}, {@link #overlap()} is the
 * minimum translation vector (MTV) that should be applied to the first
 * polygon to separate it from the second.
 *
 * @param intersects whether the two polygons overlap
 * @param overlap    the minimum translation vector to separate the first
 *                   polygon from the second; {@link Vector2#ZERO} when
 *                   there is no intersection
 * @see Polygon#satIntersection(Polygon)
 * @see Polygon#directionalSatIntersection(Polygon, Vector2, boolean)
 */
@NullMarked
public record IntersectResult(boolean intersects, Vector2 overlap) {

  /** Singleton for the non-intersecting case. */
  public static final IntersectResult NO_INTERSECT =
      new IntersectResult(false, Vector2.ZERO);
}
