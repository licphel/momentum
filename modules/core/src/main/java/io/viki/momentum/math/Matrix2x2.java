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
 * Immutable column-major 2×2 matrix.
 *
 * <p>Storage: {@code m[col][row]}, i.e. {@code m00} is column 0, row 0.
 *
 * @param m00 column 0, row 0
 * @param m10 column 1, row 0
 * @param m01 column 0, row 1
 * @param m11 column 1, row 1
 * @see Matrix3x2
 * @see Matrix3x3
 * @see Matrix4x4
 */
public record Matrix2x2(float m00, float m10, float m01, float m11) {
  public static final Matrix2x2 IDENTITY = new Matrix2x2(1.0F, 0.0F, 0.0F, 1.0F);

  /**
   * Creates a uniform scaling matrix.
   *
   * @param scalar uniform scaling factor
   * @return scaling matrix
   */
  public static Matrix2x2 createScale(float scalar) {
    return new Matrix2x2(scalar, 0.0F, 0.0F, scalar);
  }

  /**
   * Creates a scaling matrix.
   *
   * @param scalarX scaling factor along X
   * @param scalarY scaling factor along Y
   * @return scaling matrix
   */
  public static Matrix2x2 createScale(float scalarX, float scalarY) {
    return new Matrix2x2(scalarX, 0.0F, 0.0F, scalarY);
  }

  /**
   * Creates a scaling matrix.
   *
   * @param scalar the scaling vector
   * @return scaling matrix
   */
  public static Matrix2x2 createScale(Vector2 scalar) {
    return createScale(scalar.x(), scalar.y());
  }

  /**
   * Creates a counter-clockwise rotation matrix.
   *
   * @param radians rotation angle in radians
   * @return rotation matrix
   */
  public static Matrix2x2 createRotation(float radians) {
    float[] sc = FastTrigonometric.sincos(radians);
    return new Matrix2x2(sc[1], sc[0], -sc[0], sc[1]);
  }

  /**
   * Creates a shear matrix.
   *
   * @param shearX shear along X
   * @param shearY shear along Y
   * @return shear matrix
   */
  public static Matrix2x2 createShear(float shearX, float shearY) {
    return new Matrix2x2(1.0F, shearY, shearX, 1.0F);
  }

  /**
   * Creates a shear matrix.
   *
   * @param shear the shear vector
   * @return shear matrix
   */
  public static Matrix2x2 createShear(Vector2 shear) {
    return createShear(shear.x(), shear.y());
  }

  /**
   * Creates a reflection matrix about the X-axis.
   *
   * @return reflection matrix
   */
  public static Matrix2x2 createReflectionX() {
    return new Matrix2x2(-1.0F, 0.0F, 0.0F, 1.0F);
  }

  /**
   * Creates a reflection matrix about the Y-axis.
   *
   * @return reflection matrix
   */
  public static Matrix2x2 createReflectionY() {
    return new Matrix2x2(1.0F, 0.0F, 0.0F, -1.0F);
  }

  /**
   * Creates a reflection matrix about a line through the origin at the given angle.
   *
   * @param angle angle of the reflection line in radians
   * @return reflection matrix
   */
  public static Matrix2x2 createReflection(float angle) {
    float[] sc = FastTrigonometric.sincos(angle * 2.0F);
    return new Matrix2x2(sc[1], sc[0], sc[0], -sc[1]);
  }

  /**
   * Creates a combined scale+rotation matrix.
   *
   * @param rotation rotation angle in radians
   * @param scale    scaling vector
   * @return transformation matrix
   */
  public static Matrix2x2 createTransform(float rotation, Vector2 scale) {
    float[] sc = FastTrigonometric.sincos(rotation);
    float sin = sc[0];
    float cos = sc[1];
    return new Matrix2x2(scale.x() * cos, scale.x() * sin, scale.y() * -sin, scale.y() * cos);
  }

  /**
   * Creates an orthographic 2D projection matrix (scale only, no translation).
   *
   * @param left   left boundary
   * @param right  right boundary
   * @param bottom bottom boundary
   * @param top    top boundary
   * @return orthographic matrix
   */
  public static Matrix2x2 createOrthographic(float left, float right, float bottom, float top) {
    return createScale(2.0F / (right - left), 2.0F / (top - bottom));
  }

  /**
   * Linearly interpolates between two matrices.
   *
   * @param a start matrix
   * @param b end matrix
   * @param t interpolation factor (0 = {@code a}, 1 = {@code b})
   * @return interpolated matrix
   */
  public static Matrix2x2 lerp(Matrix2x2 a, Matrix2x2 b, float t) {
    t = Math.clamp(t, 0.0F, 1.0F);
    return new Matrix2x2(a.m00 + (b.m00 - a.m00) * t, a.m10 + (b.m10 - a.m10) * t, a.m01 + (b.m01 - a.m01) * t,
        a.m11 + (b.m11 - a.m11) * t);
  }

  /**
   * Returns the determinant of this matrix.
   *
   * @return m00*m11 - m01*m10
   */
  public float determinant() {
    return m00 * m11 - m01 * m10;
  }

  /**
   * Multiplies this matrix by another.
   *
   * @param o the other matrix
   * @return this * o
   */
  public Matrix2x2 multiply(Matrix2x2 o) {
    return new Matrix2x2(m00 * o.m00 + m01 * o.m10, m10 * o.m00 + m11 * o.m10, m00 * o.m01 + m01 * o.m11,
        m10 * o.m01 + m11 * o.m11);
  }

  /**
   * Scales all elements by a scalar.
   *
   * @param scalar the scaling factor
   * @return scaled matrix
   */
  public Matrix2x2 scale(float scalar) {
    return new Matrix2x2(m00 * scalar, m10 * scalar, m01 * scalar, m11 * scalar);
  }

  /**
   * Scales each column independently.
   *
   * @param scalarX scale for column X (rows 0,1)
   * @param scalarY scale for column Y (rows 0,1)
   * @return scaled matrix
   */
  public Matrix2x2 scale(float scalarX, float scalarY) {
    return new Matrix2x2(m00 * scalarX, m10 * scalarX, m01 * scalarY, m11 * scalarY);
  }

  /**
   * Scales each column by the components of a vector.
   *
   * @param scalar the scaling vector
   * @return scaled matrix
   */
  public Matrix2x2 scale(Vector2 scalar) {
    return scale(scalar.x(), scalar.y());
  }

  /**
   * Applies a shear transformation.
   *
   * @param shearX shear along X
   * @param shearY shear along Y
   * @return sheared matrix
   */
  public Matrix2x2 shear(float shearX, float shearY) {
    return new Matrix2x2(m00 + shearY * m01, m10 + shearY * m11, m01 + shearX * m00, m11 + shearX * m10);
  }

  /**
   * Applies a rotation to this matrix.
   *
   * @param radians rotation angle in radians
   * @return rotated matrix
   */
  public Matrix2x2 rotate(float radians) {
    float[] sc = FastTrigonometric.sincos(radians);
    float sin = sc[0];
    float cos = sc[1];
    return new Matrix2x2(m00 * cos + m01 * sin, m10 * cos + m11 * sin, m00 * -sin + m01 * cos, m10 * -sin + m11 * cos);
  }

  /**
   * Returns the transpose of this matrix.
   *
   * @return transposed matrix
   */
  public Matrix2x2 transpose() {
    return new Matrix2x2(m00, m01, m10, m11);
  }

  /**
   * Returns the inverse of this matrix. Returns {@link #IDENTITY} if the matrix is singular.
   *
   * @return inverse matrix
   */
  public Matrix2x2 invert() {
    float det = determinant();
    if (Math.abs(det) < 1E-10F) {
      return IDENTITY;
    }
    float inv = 1.0F / det;
    return new Matrix2x2(inv * m11, inv * -m10, inv * -m01, inv * m00);
  }

  /**
   * Transforms a vector by this matrix.
   *
   * @param v the vector to transform
   * @return transformed vector
   */
  public Vector2 transform(Vector2 v) {
    return new Vector2(m00 * v.x() + m01 * v.y(), m10 * v.x() + m11 * v.y());
  }

  /**
   * Converts to a 3×2 affine matrix by adding a translation column.
   *
   * @param tx translation X
   * @param ty translation Y
   * @return a 3×2 matrix
   */
  public Matrix3x2 toMatrix3x2(float tx, float ty) {
    return new Matrix3x2(m00, m10, m01, m11, tx, ty);
  }

  /**
   * Converts to a 3×2 affine matrix with zero translation.
   *
   * @return a 3×2 matrix
   */
  public Matrix3x2 toMatrix3x2() {
    return toMatrix3x2(0.0F, 0.0F);
  }

  /**
   * Converts to a 3×3 matrix.
   *
   * @return a 3×3 matrix
   */
  public Matrix3x3 toMatrix3x3() {
    return new Matrix3x3(m00, m10, 0.0F, m01, m11, 0.0F, 0.0F, 0.0F, 1.0F);
  }

  /**
   * Converts to a 4×4 matrix.
   *
   * @return a 4×4 matrix
   */
  public Matrix4x4 toMatrix4x4() {
    return new Matrix4x4(m00, m10, 0.0F, 0.0F, m01, m11, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
  }
}
