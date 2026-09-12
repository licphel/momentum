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

package io.viki.momentum.math.shape;

import io.viki.momentum.math.Vector2;

/**
 * Defines common geometric operations and axis-aligned bounds for a two-dimensional shape.
 *
 * @param <T> concrete shape type returned by transformations
 */
public interface Shape<T extends Shape<T>> {
  /**
   * Returns the area of this shape.
   *
   * @return area
   */
  float area();

  /**
   * Returns the x coordinate of this shape's center.
   *
   * @return center x coordinate
   */
  float centralX();

  /**
   * Returns the y coordinate of this shape's center.
   *
   * @return center y coordinate
   */
  float centralY();

  /**
   * Returns the center point of this shape.
   *
   * @return center vector
   */
  Vector2 center();

  /**
   * Returns whether this shape contains the given point.
   *
   * @param x x coordinate
   * @param y y coordinate
   * @return true if point is inside
   */
  boolean contains(float x, float y);

  /**
   * Returns whether this shape contains the given point.
   *
   * @param v the point
   * @return true if point is inside
   */
  default boolean contains(Vector2 v) {
    return contains(v.x(), v.y());
  }

  /**
   * Returns a new shape scaled by the given factors.
   *
   * @param sx x scale factor
   * @param sy y scale factor
   * @return scaled shape
   */
  T scale(float sx, float sy);

  /**
   * Returns a new shape scaled by the given factors.
   *
   * @param v scaling vector
   * @return scaled shape
   */
  default T scale(Vector2 v) {
    return scale(v.x(), v.y());
  }

  /**
   * Returns a new shape translated by the given amounts.
   *
   * @param tx x translation
   * @param ty y translation
   * @return translated shape
   */
  T translate(float tx, float ty);

  /**
   * Returns a new shape translated by the given vector.
   *
   * @param v translation vector
   * @return translated shape
   */
  default T translate(Vector2 v) {
    return translate(v.x(), v.y());
  }

  /**
   * Returns the minimum x coordinate of this shape's axis-aligned bounds.
   *
   * @return minimum x coordinate
   */
  float minX();

  /**
   * Returns the minimum y coordinate of this shape's axis-aligned bounds.
   *
   * @return minimum y coordinate
   */
  float minY();

  /**
   * Returns the maximum x coordinate of this shape's axis-aligned bounds.
   *
   * @return maximum x coordinate
   */
  float maxX();

  /**
   * Returns the maximum y coordinate of this shape's axis-aligned bounds.
   *
   * @return maximum y coordinate
   */
  float maxY();
}
