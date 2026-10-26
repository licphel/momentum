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
 * Immutable 3D double-precision vector.
 *
 * @param x x coordinate position
 * @param y y coordinate position
 * @param z z coordinate position
 * @see Vector3
 * @see Vector2d
 */
public record Vector3d(double x, double y, double z) {
  public static final Vector3d ZERO = new Vector3d(0.0, 0.0, 0.0);
  public static final Vector3d ONE = new Vector3d(1.0, 1.0, 1.0);
  public static final Vector3d UNIT_X = new Vector3d(1.0, 0.0, 0.0);
  public static final Vector3d UNIT_Y = new Vector3d(0.0, 1.0, 0.0);
  public static final Vector3d UNIT_Z = new Vector3d(0.0, 0.0, 1.0);

  /**
   * Promotes a 2D double vector (z = 0).
   *
   * @param v the 2D vector
   */
  public Vector3d(Vector2d v) {
    this(v.x(), v.y(), 0.0);
  }

  /**
   * Promotes a 2D double vector with explicit z.
   *
   * @param v the 2D vector
   * @param z the z coordinate
   */
  public Vector3d(Vector2d v, double z) {
    this(v.x(), v.y(), z);
  }

  /**
   * Promotes a Vector2 (z = 0).
   *
   * @param v the 2D vector
   */
  public Vector3d(Vector2 v) {
    this(v.x(), v.y(), 0.0);
  }

  /**
   * Promotes a Vector2 with explicit z.
   *
   * @param v the 2D vector
   * @param z the z coordinate
   */
  public Vector3d(Vector2 v, double z) {
    this(v.x(), v.y(), z);
  }

  /**
   * Promotes a float vector to double precision.
   *
   * @param v the float vector
   */
  public Vector3d(Vector3 v) {
    this(v.x(), v.y(), v.z());
  }

  /**
   * Returns the Euclidean distance between two vectors.
   *
   * @param a first vector
   * @param b second vector
   * @return the distance
   */
  public static double distance(Vector3d a, Vector3d b) {
    return Math.hypot(Math.hypot(a.x - b.x, a.y - b.y), a.z - b.z);
  }

  /**
   * Returns the squared Euclidean distance between two vectors. Use this when only comparing distances, as it avoids an
   * expensive square root.
   *
   * @param a first vector
   * @param b second vector
   * @return the squared distance
   */
  public static double distanceSquared(Vector3d a, Vector3d b) {
    double dx = a.x - b.x;
    double dy = a.y - b.y;
    double dz = a.z - b.z;
    return dx * dx + dy * dy + dz * dz;
  }

  /**
   * Returns the Manhattan (L1) distance between two vectors.
   *
   * @param a first vector
   * @param b second vector
   * @return the Manhattan distance
   */
  public static double manhattanDistance(Vector3d a, Vector3d b) {
    return Math.abs(a.x - b.x) + Math.abs(a.y - b.y) + Math.abs(a.z - b.z);
  }

  /**
   * Returns a vector with component-wise minimum values.
   *
   * @param a first vector
   * @param b second vector
   * @return component-wise minimum
   */
  public static Vector3d min(Vector3d a, Vector3d b) {
    return new Vector3d(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z));
  }

  /**
   * Returns a vector with component-wise maximum values.
   *
   * @param a first vector
   * @param b second vector
   * @return component-wise maximum
   */
  public static Vector3d max(Vector3d a, Vector3d b) {
    return new Vector3d(Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z));
  }

  /**
   * Clamps each component of {@code v} between {@code min} and {@code max}.
   *
   * @param v   the vector to clamp
   * @param min lower bound
   * @param max upper bound
   * @return clamped vector
   */
  public static Vector3d clamp(Vector3d v, Vector3d min, Vector3d max) {
    return new Vector3d(Math.clamp(v.x, min.x, max.x), Math.clamp(v.y, min.y, max.y),
        Math.clamp(v.z, min.z, max.z));
  }

  /**
   * Linearly interpolates between two vectors.
   *
   * @param a start vector
   * @param b end vector
   * @param t interpolation factor (0 = {@code a}, 1 = {@code b})
   * @return interpolated vector
   */
  public static Vector3d lerp(Vector3d a, Vector3d b, double t) {
    double inverseT = 1.0 - t;
    return new Vector3d(a.x * inverseT + b.x * t, a.y * inverseT + b.y * t, a.z * inverseT + b.z * t);
  }

  /**
   * Creates a direction vector from spherical coordinates (yaw/pitch) and radius.
   *
   * @param yaw    yaw angle in radians
   * @param pitch  pitch angle in radians
   * @param radius radial distance
   * @return direction vector scaled to the given radius
   */
  public static Vector3d createDirectional(double yaw, double pitch, double radius) {
    double cosPitch = Math.cos(pitch);
    return new Vector3d(Math.cos(yaw) * cosPitch * radius, Math.sin(pitch) * radius,
        Math.sin(yaw) * cosPitch * radius);
  }

  /**
   * Creates a direction vector from spherical coordinates (yaw/pitch).
   *
   * @param yaw   yaw angle in radians
   * @param pitch pitch angle in radians
   * @return a normalized direction vector
   */
  public static Vector3d createDirectional(double yaw, double pitch) {
    return createDirectional(yaw, pitch, 1.0);
  }

  /**
   * Returns the sum of this vector and another.
   *
   * @param o the other vector
   * @return this + o
   */
  public Vector3d add(Vector3d o) {
    return new Vector3d(x + o.x, y + o.y, z + o.z);
  }

  /**
   * Returns the difference of this vector and another.
   *
   * @param o the other vector
   * @return this - o
   */
  public Vector3d subtract(Vector3d o) {
    return new Vector3d(x - o.x, y - o.y, z - o.z);
  }

  /**
   * Returns the negation of this vector.
   *
   * @return -this
   */
  public Vector3d negate() {
    return new Vector3d(-x, -y, -z);
  }

  /**
   * Returns this vector scaled by a scalar.
   *
   * @param s the scaling factor
   * @return this * s
   */
  public Vector3d multiply(double s) {
    return new Vector3d(x * s, y * s, z * s);
  }

  /**
   * Returns this vector divided by a scalar.
   *
   * @param s the divisor (must be non-zero)
   * @return this / s
   */
  public Vector3d divide(double s) {
    return new Vector3d(x / s, y / s, z / s);
  }

  /**
   * Returns the squared length (magnitude) of this vector.
   *
   * @return x^2 + y^2 + z^2
   */
  public double lengthSquared() {
    return x * x + y * y + z * z;
  }

  /**
   * Returns the length (magnitude) of this vector.
   *
   * @return sqrt(x^2 + y^2 + z^2)
   */
  public double length() {
    return Math.hypot(Math.hypot(x, y), z);
  }

  /**
   * Returns a normalized copy of this vector. If the vector length is zero, returns {@link #ZERO}.
   *
   * @return unit vector in the same direction
   */
  public Vector3d normalize() {
    double length = length();
    return length == 0.0 ? ZERO : divide(length);
  }

  /**
   * Returns the dot product of this vector and another.
   *
   * @param o the other vector
   * @return x * o.x + y * o.y + z * o.z
   */
  public double dot(Vector3d o) {
    return x * o.x + y * o.y + z * o.z;
  }

  /**
   * Returns the cross product of this vector and another.
   *
   * @param o the other vector
   * @return this × o
   */
  public Vector3d cross(Vector3d o) {
    return new Vector3d(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
  }

  /**
   * Returns the reflection of this vector off a surface with the given normal. Assumes the normal is normalized.
   *
   * @param normal the surface normal
   * @return reflected vector
   */
  public Vector3d reflect(Vector3d normal) {
    double scale = 2.0 * dot(normal);
    return new Vector3d(x - scale * normal.x, y - scale * normal.y, z - scale * normal.z);
  }

  /**
   * Returns the projection of this vector onto another.
   *
   * @param onto the vector to project onto
   * @return projection of this onto {@code onto}
   */
  public Vector3d project(Vector3d onto) {
    double denominator = onto.dot(onto);
    return denominator == 0.0 ? ZERO : onto.multiply(dot(onto) / denominator);
  }

  /**
   * Returns the rejection of this vector from another (this - projection).
   *
   * @param onto the vector to reject from
   * @return rejection component
   */
  public Vector3d reject(Vector3d onto) {
    return subtract(project(onto));
  }

  /**
   * Returns the projection of this vector onto a plane defined by its normal.
   *
   * @param planeNormal the plane normal (assumed normalized)
   * @return projection onto the plane
   */
  public Vector3d projectOnPlane(Vector3d planeNormal) {
    return subtract(planeNormal.multiply(dot(planeNormal)));
  }

  /**
   * Rotates this vector by a quaternion.
   *
   * @param q the rotation quaternion
   * @return rotated vector
   */
  public Vector3d rotate(Quaternion q) {
    double qx = q.x();
    double qy = q.y();
    double qz = q.z();
    double qw = q.w();
    double tx = 2.0 * (qy * z - qz * y);
    double ty = 2.0 * (qz * x - qx * z);
    double tz = 2.0 * (qx * y - qy * x);
    return new Vector3d(x + qw * tx + (qy * tz - qz * ty), y + qw * ty + (qz * tx - qx * tz),
        z + qw * tz + (qx * ty - qy * tx));
  }

  /**
   * Rotates this vector around an axis by the given angle.
   *
   * @param axis    rotation axis (assumed normalized)
   * @param radians rotation angle in radians
   * @return rotated vector
   */
  public Vector3d rotate(Vector3d axis, double radians) {
    Vector3d normalizedAxis = axis.normalize();
    if (normalizedAxis.equals(ZERO)) {
      return this;
    }
    double cos = Math.cos(radians);
    double sin = Math.sin(radians);
    return multiply(cos).add(normalizedAxis.cross(this).multiply(sin))
        .add(normalizedAxis.multiply(normalizedAxis.dot(this) * (1.0 - cos)));
  }

  /**
   * Rotates this vector around a center point by a quaternion.
   *
   * @param center rotation center
   * @param q      the rotation quaternion
   * @return rotated vector
   */
  public Vector3d rotate(Vector3d center, Quaternion q) {
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
  public Vector3d rotate(Vector3d center, Vector3d axis, double radians) {
    return subtract(center).rotate(axis, radians).add(center);
  }

  /**
   * Drops the Z component.
   *
   * @return (x, y) as a 2D vector
   */
  public Vector2d xy() {
    return new Vector2d(x, y);
  }

  /**
   * Reinterprets the vector as euler.
   *
   * @return the euler
   */
  public Euler toEuler() {
    return new Euler((float) x, (float) y, (float) z);
  }

  /**
   * Narrows this vector to single precision.
   *
   * @return this vector as a {@link Vector3}
   */
  public Vector3 toVector3() {
    return new Vector3((float) x, (float) y, (float) z);
  }
}
