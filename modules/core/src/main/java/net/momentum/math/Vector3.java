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

package net.momentum.math;

/**
 * Immutable 3D float vector.
 *
 * @param x x coordinate position
 * @param y y coordinate position
 * @param z z coordinate position
 * @see Vector2
 * @see Vector4
 */
public record Vector3(float x, float y, float z) {
  public static final Vector3 ZERO = new Vector3(0.0F, 0.0F, 0.0F);
  public static final Vector3 ONE = new Vector3(1.0F, 1.0F, 1.0F);
  public static final Vector3 UNIT_X = new Vector3(1.0F, 0.0F, 0.0F);
  public static final Vector3 UNIT_Y = new Vector3(0.0F, 1.0F, 0.0F);
  public static final Vector3 UNIT_Z = new Vector3(0.0F, 0.0F, 1.0F);

  /**
   * Promotes a Vector2 (z = 0).
   *
   * @param v the 2D vector
   */
  public Vector3(Vector2 v) {
    this(v.x(), v.y(), 0.0F);
  }

  /**
   * Promotes a Vector2 with explicit z.
   *
   * @param v the 2D vector
   * @param z the z coordinate
   */
  public Vector3(Vector2 v, float z) {
    this(v.x(), v.y(), z);
  }

  /**
   * Returns the Euclidean distance between two vectors.
   *
   * @param a first vector
   * @param b second vector
   * @return the distance
   */
  public static float distance(Vector3 a, Vector3 b) {
    return a.subtract(b).length();
  }

  /**
   * Returns the squared Euclidean distance between two vectors. Use this when only comparing distances, as it avoids an
   * expensive square root.
   *
   * @param a first vector
   * @param b second vector
   * @return the squared distance
   */
  public static float distanceSquared(Vector3 a, Vector3 b) {
    return a.subtract(b).lengthSquared();
  }

  /**
   * Returns the Manhattan (L1) distance between two vectors.
   *
   * @param a first vector
   * @param b second vector
   * @return the Manhattan distance
   */
  public static float manhattanDistance(Vector3 a, Vector3 b) {
    return Math.abs(a.x - b.x) + Math.abs(a.y - b.y) + Math.abs(a.z - b.z);
  }

  /**
   * Returns a vector with component-wise minimum values.
   *
   * @param a first vector
   * @param b second vector
   * @return component-wise minimum
   */
  public static Vector3 min(Vector3 a, Vector3 b) {
    return new Vector3(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z));
  }

  /**
   * Returns a vector with component-wise maximum values.
   *
   * @param a first vector
   * @param b second vector
   * @return component-wise maximum
   */
  public static Vector3 max(Vector3 a, Vector3 b) {
    return new Vector3(Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z));
  }

  /**
   * Clamps each component of {@code v} between {@code min} and {@code max}.
   *
   * @param v   the vector to clamp
   * @param min lower bound
   * @param max upper bound
   * @return clamped vector
   */
  public static Vector3 clamp(Vector3 v, Vector3 min, Vector3 max) {
    return new Vector3(Math.clamp(v.x, min.x, max.x), Math.clamp(v.y, min.y, max.y), Math.clamp(v.z, min.z, max.z));
  }

  /**
   * Linearly interpolates between two vectors.
   *
   * @param a start vector
   * @param b end vector
   * @param t interpolation factor (0 = {@code a}, 1 = {@code b})
   * @return interpolated vector
   */
  public static Vector3 lerp(Vector3 a, Vector3 b, float t) {
    float it = 1.0F - t;
    return new Vector3(a.x * it + b.x * t, a.y * it + b.y * t, a.z * it + b.z * t);
  }

  /**
   * Creates a direction vector from spherical coordinates (yaw/pitch) and radius.
   *
   * @param yaw    yaw angle in radians
   * @param pitch  pitch angle in radians
   * @param radius radial distance
   * @return direction vector scaled to the given radius
   */
  public static Vector3 createDirectional(float yaw, float pitch, float radius) {
    float cp = FastTrigonometric.cos(pitch);
    return new Vector3(
        FastTrigonometric.cos(yaw) * cp * radius,
        FastTrigonometric.sin(pitch) * radius,
        FastTrigonometric.sin(yaw) * cp * radius
    );
  }

  /**
   * Creates a direction vector from spherical coordinates (yaw/pitch).
   *
   * @param yaw   yaw angle in radians
   * @param pitch pitch angle in radians
   * @return a normalized direction vector
   */
  public static Vector3 createDirectional(float yaw, float pitch) {
    return createDirectional(yaw, pitch, 1.0F);
  }

  /**
   * Returns the sum of this vector and another.
   *
   * @param o the other vector
   * @return this + o
   */
  public Vector3 add(Vector3 o) {
    return new Vector3(x + o.x, y + o.y, z + o.z);
  }

  /**
   * Returns the difference of this vector and another.
   *
   * @param o the other vector
   * @return this - o
   */
  public Vector3 subtract(Vector3 o) {
    return new Vector3(x - o.x, y - o.y, z - o.z);
  }

  /**
   * Returns the negation of this vector.
   *
   * @return -this
   */
  public Vector3 negate() {
    return new Vector3(-x, -y, -z);
  }

  /**
   * Returns this vector scaled by a scalar.
   *
   * @param s the scaling factor
   * @return this * s
   */
  public Vector3 multiply(float s) {
    return new Vector3(x * s, y * s, z * s);
  }

  /**
   * Returns this vector divided by a scalar.
   *
   * @param s the divisor (must be non-zero)
   * @return this / s
   */
  public Vector3 divide(float s) {
    return new Vector3(x / s, y / s, z / s);
  }

  /**
   * Returns the squared length (magnitude) of this vector.
   *
   * @return x^2 + y^2 + z^2
   */
  public float lengthSquared() {
    return x * x + y * y + z * z;
  }

  /**
   * Returns the length (magnitude) of this vector.
   *
   * @return sqrt(x^2 + y^2 + z^2)
   */
  public float length() {
    return (float) Math.sqrt(lengthSquared());
  }

  /**
   * Returns a normalized copy of this vector. If the vector length is near zero, returns {@link #ZERO}.
   *
   * @return unit vector in the same direction
   */
  public Vector3 normalize() {
    float len = length();
    return len < 1E-10F ? ZERO : divide(len);
  }

  /**
   * Returns the dot product of this vector and another.
   *
   * @param o the other vector
   * @return x * o.x + y * o.y + z * o.z
   */
  public float dot(Vector3 o) {
    return x * o.x + y * o.y + z * o.z;
  }

  /**
   * Returns the cross product of this vector and another.
   *
   * @param o the other vector
   * @return this × o
   */
  public Vector3 cross(Vector3 o) {
    return new Vector3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
  }

  /**
   * Returns the reflection of this vector off a surface with the given normal. Assumes the normal is normalized.
   *
   * @param normal the surface normal
   * @return reflected vector
   */
  public Vector3 reflect(Vector3 normal) {
    float d = 2.0F * dot(normal);
    return new Vector3(x - d * normal.x, y - d * normal.y, z - d * normal.z);
  }

  /**
   * Returns the projection of this vector onto another.
   *
   * @param onto the vector to project onto
   * @return projection of this onto {@code onto}
   */
  public Vector3 project(Vector3 onto) {
    float d = onto.dot(onto);
    if (d < 1E-10F) {
      return ZERO;
    }
    return onto.multiply(dot(onto) / d);
  }

  /**
   * Returns the rejection of this vector from another (this - projection).
   *
   * @param onto the vector to reject from
   * @return rejection component
   */
  public Vector3 reject(Vector3 onto) {
    return subtract(project(onto));
  }

  /**
   * Returns the projection of this vector onto a plane defined by its normal.
   *
   * @param planeNormal the plane normal (assumed normalized)
   * @return projection onto the plane
   */
  public Vector3 projectOnPlane(Vector3 planeNormal) {
    return subtract(planeNormal.multiply(dot(planeNormal)));
  }

  /**
   * Rotates this vector by a quaternion.
   *
   * @param q the rotation quaternion
   * @return rotated vector
   */
  public Vector3 rotate(Quaternion q) {
    return q.rotate(this);
  }

  /**
   * Rotates this vector around an axis by the given angle.
   *
   * @param axis    rotation axis (assumed normalized)
   * @param radians rotation angle in radians
   * @return rotated vector
   */
  public Vector3 rotate(Vector3 axis, float radians) {
    return rotate(Quaternion.createFromAxisAngle(axis, radians));
  }

  /**
   * Rotates this vector around a center point by a quaternion.
   *
   * @param center rotation center
   * @param q      the rotation quaternion
   * @return rotated vector
   */
  public Vector3 rotate(Vector3 center, Quaternion q) {
    return subtract(center).rotate(q).add(center);
  }

  /**
   * Rotates this vector around a center point and axis by the given angle.
   *
   * @param center  rotation center
   * @param axis    rotation axis (assumed normalized)
   * @param radians rotation angle in radians
   * @return rotated vector
   */
  public Vector3 rotate(Vector3 center, Vector3 axis, float radians) {
    return rotate(center, Quaternion.createFromAxisAngle(axis, radians));
  }

  /**
   * Drops the Z component.
   *
   * @return (x, y) as a 2D vector
   */
  public Vector2 xy() {
    return new Vector2(x, y);
  }

  /**
   * Reinterprets the vector as euler.
   *
   * @return the euler
   */
  public Euler toEuler() {
    return new Euler(x, y, z);
  }
}