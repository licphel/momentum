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

package net.fmhi.math;

/**
 * Immutable 2D float vector.
 *
 * @param x x coordinate position
 * @param y y coordinate position
 * @see Vector3
 * @see Vector4
 */
public record Vector2(float x, float y) {
  public static final Vector2 ZERO = new Vector2(0.0F, 0.0F);
  public static final Vector2 ONE = new Vector2(1.0F, 1.0F);
  public static final Vector2 UNIT_X = new Vector2(1.0F, 0.0F);
  public static final Vector2 UNIT_Y = new Vector2(0.0F, 1.0F);

  /**
   * Creates a vector from polar coordinates.
   *
   * @param radius the radius
   * @param angle  the angle
   * @return a vector of the given (r, alpha)
   */
  public static Vector2 polar(float radius, float angle) {
    float[] sc = FastTrigonometric.sincos(angle);
    return new Vector2(radius * sc[1], radius * sc[0]);
  }

  /**
   * Returns the Euclidean distance between two vectors.
   *
   * @param a first vector
   * @param b second vector
   * @return the distance
   */
  public static float distance(Vector2 a, Vector2 b) {
    return a.subtract(b).length();
  }

  /**
   * Returns the squared Euclidean distance between two vectors.
   *
   * @param a first vector
   * @param b second vector
   * @return the squared distance
   */
  public static float distanceSquared(Vector2 a, Vector2 b) {
    return a.subtract(b).lengthSquared();
  }

  /**
   * Returns the Manhattan (L1) distance between two vectors.
   *
   * @param a first vector
   * @param b second vector
   * @return the Manhattan distance
   */
  public static float manhattanDistance(Vector2 a, Vector2 b) {
    return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
  }

  /**
   * Returns a vector with component-wise minimum values.
   *
   * @param a first vector
   * @param b second vector
   * @return component-wise minimum
   */
  public static Vector2 min(Vector2 a, Vector2 b) {
    return new Vector2(Math.min(a.x, b.x), Math.min(a.y, b.y));
  }

  /**
   * Returns a vector with component-wise maximum values.
   *
   * @param a first vector
   * @param b second vector
   * @return component-wise maximum
   */
  public static Vector2 max(Vector2 a, Vector2 b) {
    return new Vector2(Math.max(a.x, b.x), Math.max(a.y, b.y));
  }

  /**
   * Clamps each component of {@code v} between {@code min} and {@code max}.
   *
   * @param v   the vector to clamp
   * @param min lower bound
   * @param max upper bound
   * @return clamped vector
   */
  public static Vector2 clamp(Vector2 v, Vector2 min, Vector2 max) {
    return new Vector2(Math.clamp(v.x, min.x, max.x), Math.clamp(v.y, min.y, max.y));
  }

  /**
   * Linearly interpolates between two vectors.
   *
   * @param a start vector
   * @param b end vector
   * @param t interpolation factor (0 = {@code a}, 1 = {@code b})
   * @return interpolated vector
   */
  public static Vector2 lerp(Vector2 a, Vector2 b, float t) {
    float it = 1.0F - t;
    return new Vector2(a.x * it + b.x * t, a.y * it + b.y * t);
  }

  /**
   * Creates a unit vector from an angle.
   *
   * @param radians angle in radians
   * @return unit vector pointing in the given direction
   */
  public static Vector2 createDirectional(float radians) {
    float[] sc = FastTrigonometric.sincos(radians);
    return new Vector2(sc[1], sc[0]);
  }

  /**
   * Returns the sum of this vector and another.
   *
   * @param o the other vector
   * @return this + o
   */
  public Vector2 add(Vector2 o) {
    return new Vector2(x + o.x, y + o.y);
  }

  /**
   * Returns the difference of this vector and another.
   *
   * @param o the other vector
   * @return this - o
   */
  public Vector2 subtract(Vector2 o) {
    return new Vector2(x - o.x, y - o.y);
  }

  /**
   * Returns the negation of this vector.
   *
   * @return -this
   */
  public Vector2 negate() {
    return new Vector2(-x, -y);
  }

  /**
   * Returns this vector scaled by a scalar.
   *
   * @param s the scaling factor
   * @return this * s
   */
  public Vector2 multiply(float s) {
    return new Vector2(x * s, y * s);
  }

  /**
   * Returns this vector divided by a scalar.
   *
   * @param s the divisor (must be non-zero)
   * @return this / s
   */
  public Vector2 divide(float s) {
    return new Vector2(x / s, y / s);
  }

  /**
   * Returns the squared length (magnitude) of this vector.
   *
   * @return x^2 + y^2
   */
  public float lengthSquared() {
    return x * x + y * y;
  }

  /**
   * Returns the length (magnitude) of this vector.
   *
   * @return sqrt(x^2 + y^2)
   */
  public float length() {
    return (float) Math.sqrt(lengthSquared());
  }

  /**
   * Returns a normalized copy of this vector. If the vector length is near zero, returns {@link #ZERO}.
   *
   * @return unit vector in the same direction
   */
  public Vector2 normalize() {
    float len = length();
    return len < 1E-10F ? ZERO : divide(len);
  }

  /**
   * Returns the 2D cross product (z-component of 3D cross product).
   *
   * @param o the other vector
   * @return x * o.y - y * o.x
   */
  public float cross(Vector2 o) {
    return x * o.y - y * o.x;
  }

  /**
   * Returns the dot product of this vector and another.
   *
   * @param o the other vector
   * @return x * o.x + y * o.y
   */
  public float dot(Vector2 o) {
    return x * o.x + y * o.y;
  }

  /**
   * Returns the reflection of this vector off a surface with the given normal. Assumes the normal is normalized.
   *
   * @param normal the surface normal
   * @return reflected vector
   */
  public Vector2 reflect(Vector2 normal) {
    float d = 2.0F * dot(normal);
    return new Vector2(x - d * normal.x, y - d * normal.y);
  }

  /**
   * Returns the projection of this vector onto another.
   *
   * @param onto the vector to project onto
   * @return projection of this onto {@code onto}
   */
  public Vector2 project(Vector2 onto) {
    float d = onto.dot(onto);
    if (d < 1E-10F) {
      return ZERO;
    }
    float s = dot(onto) / d;
    return onto.multiply(s);
  }

  /**
   * Returns the rejection of this vector from another (this - projection).
   *
   * @param onto the vector to reject from
   * @return rejection component
   */
  public Vector2 reject(Vector2 onto) {
    return subtract(project(onto));
  }

  /**
   * Rotates this vector around the origin by the given angle.
   *
   * @param radians rotation angle in radians
   * @return rotated vector
   */
  public Vector2 rotate(float radians) {
    float[] sc = FastTrigonometric.sincos(radians);
    float cos = sc[1];
    float sin = sc[0];
    return new Vector2(x * cos - y * sin, x * sin + y * cos);
  }

  /**
   * Rotates this vector around a center point by the given angle.
   *
   * @param center  rotation center
   * @param radians rotation angle in radians
   * @return rotated vector
   */
  public Vector2 rotate(Vector2 center, float radians) {
    return subtract(center).rotate(radians).add(center);
  }

  /**
   * Returns the angle of this vector in radians.
   *
   * @return arctan2(y, x)
   */
  public float angle() {
    return (float) Math.atan2(y, x);
  }
}