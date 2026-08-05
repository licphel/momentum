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
 * Immutable 4D float vector.
 *
 * @param x x coordinate position
 * @param y y coordinate position
 * @param z z coordinate position
 * @param w w coordinate position (often used as homogeneous coordinate or alpha)
 * @see Vector2
 * @see Vector3
 */
public record Vector4(float x, float y, float z, float w) {
  public static final Vector4 ZERO = new Vector4(0.0F, 0.0F, 0.0F, 0.0F);
  public static final Vector4 ONE = new Vector4(1.0F, 1.0F, 1.0F, 1.0F);
  public static final Vector4 UNIT_X = new Vector4(1.0F, 0.0F, 0.0F, 0.0F);
  public static final Vector4 UNIT_Y = new Vector4(0.0F, 1.0F, 0.0F, 0.0F);
  public static final Vector4 UNIT_Z = new Vector4(0.0F, 0.0F, 1.0F, 0.0F);
  public static final Vector4 UNIT_W = new Vector4(0.0F, 0.0F, 0.0F, 1.0F);

  /**
   * Creates a Vector4 from a Vector3 and a w component.
   *
   * @param v the 3D vector
   * @param w the w component
   */
  public Vector4(Vector3 v, float w) {
    this(v.x(), v.y(), v.z(), w);
  }

  /**
   * Creates a Vector4 from a Vector2 and explicit z, w.
   *
   * @param v the 2D vector
   * @param z the z component
   * @param w the w component
   */
  public Vector4(Vector2 v, float z, float w) {
    this(v.x(), v.y(), z, w);
  }

  /**
   * Creates a Vector4 from a Color (RGBA).
   *
   * @param c the color
   * @return vector representation of the color
   */
  public static Vector4 createColor(Color c) {
    return new Vector4(c.red(), c.green(), c.blue(), c.alpha());
  }

  /**
   * Returns the Euclidean distance between two vectors.
   *
   * @param a first vector
   * @param b second vector
   * @return the distance
   */
  public static float distance(Vector4 a, Vector4 b) {
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
  public static float distanceSquared(Vector4 a, Vector4 b) {
    return a.subtract(b).lengthSquared();
  }

  /**
   * Returns a vector with component-wise minimum values.
   *
   * @param a first vector
   * @param b second vector
   * @return component-wise minimum
   */
  public static Vector4 min(Vector4 a, Vector4 b) {
    return new Vector4(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z), Math.min(a.w, b.w));
  }

  /**
   * Returns a vector with component-wise maximum values.
   *
   * @param a first vector
   * @param b second vector
   * @return component-wise maximum
   */
  public static Vector4 max(Vector4 a, Vector4 b) {
    return new Vector4(Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z), Math.max(a.w, b.w));
  }

  /**
   * Clamps each component of {@code v} between {@code min} and {@code max}.
   *
   * @param v   the vector to clamp
   * @param min lower bound
   * @param max upper bound
   * @return clamped vector
   */
  public static Vector4 clamp(Vector4 v, Vector4 min, Vector4 max) {
    return new Vector4(Math.clamp(v.x, min.x, max.x), Math.clamp(v.y, min.y, max.y), Math.clamp(v.z, min.z, max.z),
        Math.clamp(v.w, min.w, max.w));
  }

  /**
   * Linearly interpolates between two vectors.
   *
   * @param a start vector
   * @param b end vector
   * @param t interpolation factor (0 = {@code a}, 1 = {@code b})
   * @return interpolated vector
   */
  public static Vector4 lerp(Vector4 a, Vector4 b, float t) {
    float it = 1.0F - t;
    return new Vector4(a.x * it + b.x * t, a.y * it + b.y * t, a.z * it + b.z * t, a.w * it + b.w * t);
  }

  /**
   * Returns the sum of this vector and another.
   *
   * @param o the other vector
   * @return this + o
   */
  public Vector4 add(Vector4 o) {
    return new Vector4(x + o.x, y + o.y, z + o.z, w + o.w);
  }

  /**
   * Returns the difference of this vector and another.
   *
   * @param o the other vector
   * @return this - o
   */
  public Vector4 subtract(Vector4 o) {
    return new Vector4(x - o.x, y - o.y, z - o.z, w - o.w);
  }

  /**
   * Returns the negation of this vector.
   *
   * @return -this
   */
  public Vector4 negate() {
    return new Vector4(-x, -y, -z, -w);
  }

  /**
   * Returns this vector scaled by a scalar.
   *
   * @param s the scaling factor
   * @return this * s
   */
  public Vector4 multiply(float s) {
    return new Vector4(x * s, y * s, z * s, w * s);
  }

  /**
   * Returns this vector divided by a scalar.
   *
   * @param s the divisor (must be non-zero)
   * @return this / s
   */
  public Vector4 divide(float s) {
    return new Vector4(x / s, y / s, z / s, w / s);
  }

  /**
   * Returns the squared length (magnitude) of this vector.
   *
   * @return x^2 + y^2 + z^2 + w^2
   */
  public float lengthSquared() {
    return x * x + y * y + z * z + w * w;
  }

  /**
   * Returns the length (magnitude) of this vector.
   *
   * @return sqrt(x^2 + y^2 + z^2 + w^2)
   */
  public float length() {
    return (float) Math.sqrt(lengthSquared());
  }

  /**
   * Returns a normalized copy of this vector. If the vector length is near zero, returns {@link #ZERO}.
   *
   * @return unit vector in the same direction
   */
  public Vector4 normalize() {
    float len = length();
    return len < 1E-10F ? ZERO : divide(len);
  }

  /**
   * Performs perspective divide: returns xyz / w.
   *
   * @return homogeneous normalized vector
   */
  public Vector3 homogeneousNormalize() {
    return new Vector3(x / w, y / w, z / w);
  }

  /**
   * Returns the dot product of this vector and another.
   *
   * @param o the other vector
   * @return x * o.x + y * o.y + z * o.z + w * o.w
   */
  public float dot(Vector4 o) {
    return x * o.x + y * o.y + z * o.z + w * o.w;
  }

  /**
   * Returns the reflection of this vector off a surface with the given normal. Assumes the normal is normalized.
   *
   * @param normal the surface normal
   * @return reflected vector
   */
  public Vector4 reflect(Vector4 normal) {
    float d = 2.0F * dot(normal);
    return subtract(normal.multiply(d));
  }

  /**
   * Returns the projection of this vector onto another.
   *
   * @param onto the vector to project onto
   * @return projection of this onto {@code onto}
   */
  public Vector4 project(Vector4 onto) {
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
  public Vector4 reject(Vector4 onto) {
    return subtract(project(onto));
  }

  /**
   * Drops the Z and W components.
   *
   * @return (x, y) as a 2D vector
   */
  public Vector2 xy() {
    return new Vector2(x, y);
  }

  /**
   * Drops the W component.
   *
   * @return (x, y, z) as a 3D vector
   */
  public Vector3 xyz() {
    return new Vector3(x, y, z);
  }

  /**
   * Reinterprets XYZW as RGBA.
   *
   * @return color representation
   */
  public Color toColor() {
    return new Color(x, y, z, w);
  }
}