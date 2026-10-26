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

package io.viki.momentum.math;

/**
 * Immutable 2D double-precision vector.
 *
 * @param x x coordinate position
 * @param y y coordinate position
 * @see Vector2
 * @see Vector3d
 */
public record Vector2d(double x, double y) {
  public static final Vector2d ZERO = new Vector2d(0.0, 0.0);
  public static final Vector2d ONE = new Vector2d(1.0, 1.0);
  public static final Vector2d UNIT_X = new Vector2d(1.0, 0.0);
  public static final Vector2d UNIT_Y = new Vector2d(0.0, 1.0);

  /**
   * Promotes a float vector to double precision.
   *
   * @param v the float vector
   */
  public Vector2d(Vector2 v) {
    this(v.x(), v.y());
  }

  /**
   * Creates a vector from polar coordinates.
   *
   * @param radius the radius
   * @param angle  the angle
   * @return a vector of the given (r, alpha)
   */
  public static Vector2d polar(double radius, double angle) {
    return new Vector2d(radius * Math.cos(angle), radius * Math.sin(angle));
  }

  /**
   * Returns the Euclidean distance between two vectors.
   *
   * @param a first vector
   * @param b second vector
   * @return the distance
   */
  public static double distance(Vector2d a, Vector2d b) {
    return Math.hypot(a.x - b.x, a.y - b.y);
  }

  /**
   * Returns the squared Euclidean distance between two vectors.
   *
   * @param a first vector
   * @param b second vector
   * @return the squared distance
   */
  public static double distanceSquared(Vector2d a, Vector2d b) {
    double dx = a.x - b.x;
    double dy = a.y - b.y;
    return dx * dx + dy * dy;
  }

  /**
   * Returns the Manhattan (L1) distance between two vectors.
   *
   * @param a first vector
   * @param b second vector
   * @return the Manhattan distance
   */
  public static double manhattanDistance(Vector2d a, Vector2d b) {
    return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
  }

  /**
   * Returns a vector with component-wise minimum values.
   *
   * @param a first vector
   * @param b second vector
   * @return component-wise minimum
   */
  public static Vector2d min(Vector2d a, Vector2d b) {
    return new Vector2d(Math.min(a.x, b.x), Math.min(a.y, b.y));
  }

  /**
   * Returns a vector with component-wise maximum values.
   *
   * @param a first vector
   * @param b second vector
   * @return component-wise maximum
   */
  public static Vector2d max(Vector2d a, Vector2d b) {
    return new Vector2d(Math.max(a.x, b.x), Math.max(a.y, b.y));
  }

  /**
   * Clamps each component of {@code v} between {@code min} and {@code max}.
   *
   * @param v   the vector to clamp
   * @param min lower bound
   * @param max upper bound
   * @return clamped vector
   */
  public static Vector2d clamp(Vector2d v, Vector2d min, Vector2d max) {
    return new Vector2d(Math.clamp(v.x, min.x, max.x), Math.clamp(v.y, min.y, max.y));
  }

  /**
   * Linearly interpolates between two vectors.
   *
   * @param a start vector
   * @param b end vector
   * @param t interpolation factor (0 = {@code a}, 1 = {@code b})
   * @return interpolated vector
   */
  public static Vector2d lerp(Vector2d a, Vector2d b, double t) {
    double inverseT = 1.0 - t;
    return new Vector2d(a.x * inverseT + b.x * t, a.y * inverseT + b.y * t);
  }

  /**
   * Creates a unit vector from an angle.
   *
   * @param radians angle in radians
   * @return unit vector pointing in the given direction
   */
  public static Vector2d createDirectional(double radians) {
    return new Vector2d(Math.cos(radians), Math.sin(radians));
  }

  /**
   * Returns the sum of this vector and another.
   *
   * @param o the other vector
   * @return this + o
   */
  public Vector2d add(Vector2d o) {
    return new Vector2d(x + o.x, y + o.y);
  }

  /**
   * Returns the difference of this vector and another.
   *
   * @param o the other vector
   * @return this - o
   */
  public Vector2d subtract(Vector2d o) {
    return new Vector2d(x - o.x, y - o.y);
  }

  /**
   * Returns the negation of this vector.
   *
   * @return -this
   */
  public Vector2d negate() {
    return new Vector2d(-x, -y);
  }

  /**
   * Returns this vector scaled by a scalar.
   *
   * @param s the scaling factor
   * @return this * s
   */
  public Vector2d multiply(double s) {
    return new Vector2d(x * s, y * s);
  }

  /**
   * Returns this vector divided by a scalar.
   *
   * @param s the divisor (must be non-zero)
   * @return this / s
   */
  public Vector2d divide(double s) {
    return new Vector2d(x / s, y / s);
  }

  /**
   * Returns the squared length (magnitude) of this vector.
   *
   * @return x^2 + y^2
   */
  public double lengthSquared() {
    return x * x + y * y;
  }

  /**
   * Returns the length (magnitude) of this vector.
   *
   * @return sqrt(x^2 + y^2)
   */
  public double length() {
    return Math.hypot(x, y);
  }

  /**
   * Returns a normalized copy of this vector. If the vector length is zero, returns {@link #ZERO}.
   *
   * @return unit vector in the same direction
   */
  public Vector2d normalize() {
    double length = length();
    return length == 0.0 ? ZERO : divide(length);
  }

  /**
   * Returns the 2D cross product (z-component of 3D cross product).
   *
   * @param o the other vector
   * @return x * o.y - y * o.x
   */
  public double cross(Vector2d o) {
    return x * o.y - y * o.x;
  }

  /**
   * Returns the dot product of this vector and another.
   *
   * @param o the other vector
   * @return x * o.x + y * o.y
   */
  public double dot(Vector2d o) {
    return x * o.x + y * o.y;
  }

  /**
   * Returns the reflection of this vector off a surface with the given normal. Assumes the normal is normalized.
   *
   * @param normal the surface normal
   * @return reflected vector
   */
  public Vector2d reflect(Vector2d normal) {
    double scale = 2.0 * dot(normal);
    return new Vector2d(x - scale * normal.x, y - scale * normal.y);
  }

  /**
   * Returns the projection of this vector onto another.
   *
   * @param onto the vector to project onto
   * @return projection of this onto {@code onto}
   */
  public Vector2d project(Vector2d onto) {
    double denominator = onto.dot(onto);
    return denominator == 0.0 ? ZERO : onto.multiply(dot(onto) / denominator);
  }

  /**
   * Returns the rejection of this vector from another (this - projection).
   *
   * @param onto the vector to reject from
   * @return rejection component
   */
  public Vector2d reject(Vector2d onto) {
    return subtract(project(onto));
  }

  /**
   * Rotates this vector around the origin by the given angle.
   *
   * @param radians rotation angle in radians
   * @return rotated vector
   */
  public Vector2d rotate(double radians) {
    double cos = Math.cos(radians);
    double sin = Math.sin(radians);
    return new Vector2d(x * cos - y * sin, x * sin + y * cos);
  }

  /**
   * Rotates this vector around a center point by the given angle.
   *
   * @param center  rotation center
   * @param radians rotation angle in radians
   * @return rotated vector
   */
  public Vector2d rotate(Vector2d center, double radians) {
    return subtract(center).rotate(radians).add(center);
  }

  /**
   * Returns the angle of this vector in radians.
   *
   * @return arctan2(y, x)
   */
  public double angle() {
    return Math.atan2(y, x);
  }

  /**
   * Narrows this vector to single precision.
   *
   * @return this vector as a {@link Vector2}
   */
  public Vector2 toVector2() {
    return new Vector2((float) x, (float) y);
  }
}
