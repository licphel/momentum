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
 * Immutable column-major affine 2D matrix (3 columns, 2 rows).
 *
 * <p>Represents a 2D affine transform: a 2×2 linear part plus a 2D translation column.
 * Storage: {@code m[col][row]}, columns 0–1 are the linear part, column 2 is translation.
 *
 * @param m00 column 0, row 0 (linear)
 * @param m10 column 1, row 0 (linear)
 * @param m01 column 0, row 1 (linear)
 * @param m11 column 1, row 1 (linear)
 * @param m02 column 2, row 0 (translation X)
 * @param m12 column 2, row 1 (translation Y)
 * @see Matrix2x2
 * @see Matrix3x3
 * @see Matrix4x4
 */
public record Matrix3x2(float m00, float m10, float m01, float m11, float m02, float m12) {
  public static final Matrix3x2 IDENTITY = new Matrix3x2(1.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F);

  /**
   * Creates a translation matrix.
   *
   * @param x translation X
   * @param y translation Y
   * @return translation matrix
   */
  public static Matrix3x2 createTranslation(float x, float y) {
    return new Matrix3x2(1.0F, 0.0F, 0.0F, 1.0F, x, y);
  }

  /**
   * Creates a translation matrix.
   *
   * @param translation translation vector
   * @return translation matrix
   */
  public static Matrix3x2 createTranslation(Vector2 translation) {
    return createTranslation(translation.x(), translation.y());
  }

  /**
   * Creates a uniform scaling matrix.
   *
   * @param scalar uniform scaling factor
   * @return scaling matrix
   */
  public static Matrix3x2 createScale(float scalar) {
    return new Matrix3x2(scalar, 0.0F, 0.0F, scalar, 0.0F, 0.0F);
  }

  /**
   * Creates a scaling matrix.
   *
   * @param scalarX scaling factor along X
   * @param scalarY scaling factor along Y
   * @return scaling matrix
   */
  public static Matrix3x2 createScale(float scalarX, float scalarY) {
    return new Matrix3x2(scalarX, 0.0F, 0.0F, scalarY, 0.0F, 0.0F);
  }

  /**
   * Creates a scaling matrix.
   *
   * @param scalar the scaling vector
   * @return scaling matrix
   */
  public static Matrix3x2 createScale(Vector2 scalar) {
    return createScale(scalar.x(), scalar.y());
  }

  /**
   * Creates a scaling matrix centered at a point.
   *
   * @param scalarX scaling factor along X
   * @param scalarY scaling factor along Y
   * @param center  center of scaling
   * @return scaling matrix
   */
  public static Matrix3x2 createScale(float scalarX, float scalarY, Vector2 center) {
    float tx = center.x() * (1.0F - scalarX);
    float ty = center.y() * (1.0F - scalarY);
    return new Matrix3x2(scalarX, 0.0F, 0.0F, scalarY, tx, ty);
  }

  /**
   * Creates a scaling matrix centered at a point.
   *
   * @param scalar the scaling vector
   * @param center center of scaling
   * @return scaling matrix
   */
  public static Matrix3x2 createScale(Vector2 scalar, Vector2 center) {
    return createScale(scalar.x(), scalar.y(), center);
  }

  /**
   * Creates a uniform scaling matrix centered at a point.
   *
   * @param scalar uniform scaling factor
   * @param center center of scaling
   * @return scaling matrix
   */
  public static Matrix3x2 createScale(float scalar, Vector2 center) {
    return createScale(scalar, scalar, center);
  }

  /**
   * Creates a rotation matrix.
   *
   * @param radians rotation angle in radians (counter-clockwise)
   * @return rotation matrix
   */
  public static Matrix3x2 createRotation(float radians) {
    float[] sc = FastTrigonometric.sincos(radians);
    return new Matrix3x2(sc[1], sc[0], -sc[0], sc[1], 0.0F, 0.0F);
  }

  /**
   * Creates a rotation matrix around a center point.
   *
   * @param radians rotation angle in radians
   * @param center  center of rotation
   * @return rotation matrix
   */
  public static Matrix3x2 createRotation(float radians, Vector2 center) {
    float[] sc = FastTrigonometric.sincos(radians);
    float cos = sc[1];
    float sin = sc[0];
    float _1mCos = 1.0F - cos;
    float tx = center.x() * _1mCos + center.y() * sin;
    float ty = -center.x() * sin + center.y() * _1mCos;
    return new Matrix3x2(cos, sin, -sin, cos, tx, ty);
  }

  /**
   * Creates a shear matrix.
   *
   * @param shearX shear along X
   * @param shearY shear along Y
   * @return shear matrix
   */
  public static Matrix3x2 createShear(float shearX, float shearY) {
    return new Matrix3x2(1.0F, shearY, shearX, 1.0F, 0.0F, 0.0F);
  }

  /**
   * Creates a shear matrix.
   *
   * @param shear shear vector
   * @return shear matrix
   */
  public static Matrix3x2 createShear(Vector2 shear) {
    return createShear(shear.x(), shear.y());
  }

  /**
   * Creates a combined translation, rotation, and scale matrix.
   *
   * @param position translation
   * @param rotation rotation angle in radians
   * @param scalar   scale vector
   * @return transformation matrix
   */
  public static Matrix3x2 createTransform(Vector2 position, float rotation, Vector2 scalar) {
    float[] sc = FastTrigonometric.sincos(rotation);
    float sin = sc[0];
    float cos = sc[1];
    return new Matrix3x2(scalar.x() * cos, scalar.x() * sin, scalar.y() * -sin, scalar.y() * cos, position.x(),
        position.y());
  }

  /**
   * Creates an orthographic projection matrix (scale + no translation).
   *
   * @param left   left boundary
   * @param right  right boundary
   * @param bottom bottom boundary
   * @param top    top boundary
   * @return orthographic matrix
   */
  public static Matrix3x2 createOrthographic(float left, float right, float bottom, float top) {
    return createScale(2.0F / (right - left), 2.0F / (top - bottom));
  }

  /**
   * Creates a matrix that maps one rectangle to another.
   *
   * @param src source rectangle
   * @param dst destination rectangle
   * @return mapping matrix
   */
  public static Matrix3x2 createRectMapping(Box2D src, Box2D dst) {
    float sx = (dst.maxX() - dst.minX()) / (src.maxX() - src.minX());
    float sy = (dst.maxY() - dst.minY()) / (src.maxY() - src.minY());
    float tx = dst.minX() - src.minX() * sx;
    float ty = dst.minY() - src.minY() * sy;
    return new Matrix3x2(sx, 0.0F, 0.0F, sy, tx, ty);
  }

  /**
   * Linearly interpolates between two matrices.
   *
   * @param a start matrix
   * @param b end matrix
   * @param t interpolation factor (0 = {@code a}, 1 = {@code b})
   * @return interpolated matrix
   */
  public static Matrix3x2 lerp(Matrix3x2 a, Matrix3x2 b, float t) {
    return new Matrix3x2(a.m00 + (b.m00 - a.m00) * t, a.m10 + (b.m10 - a.m10) * t, a.m01 + (b.m01 - a.m01) * t,
        a.m11 + (b.m11 - a.m11) * t, a.m02 + (b.m02 - a.m02) * t, a.m12 + (b.m12 - a.m12) * t);
  }

  /**
   * Returns the determinant of the 2×2 linear part.
   *
   * @return m00*m11 - m01*m10
   */
  public float determinant() {
    return m00 * m11 - m01 * m10;
  }

  /**
   * Multiplies this affine matrix by another.
   *
   * @param o the other matrix
   * @return this * o
   */
  public Matrix3x2 multiply(Matrix3x2 o) {
    return new Matrix3x2(m00 * o.m00 + m01 * o.m10, m10 * o.m00 + m11 * o.m10, m00 * o.m01 + m01 * o.m11,
        m10 * o.m01 + m11 * o.m11, m00 * o.m02 + m01 * o.m12 + m02, m10 * o.m02 + m11 * o.m12 + m12);
  }

  /**
   * Scales the linear part uniformly (translation is unchanged).
   *
   * @param scalar the scaling factor
   * @return scaled matrix
   */
  public Matrix3x2 scale(float scalar) {
    return new Matrix3x2(m00 * scalar, m10 * scalar, m01 * scalar, m11 * scalar, m02, m12);
  }

  /**
   * Scales each axis of the linear part independently (translation is unchanged).
   *
   * @param scalarX scale along X
   * @param scalarY scale along Y
   * @return scaled matrix
   */
  public Matrix3x2 scale(float scalarX, float scalarY) {
    return new Matrix3x2(m00 * scalarX, m10 * scalarX, m01 * scalarY, m11 * scalarY, m02, m12);
  }

  /**
   * Scales each axis by the components of a vector (translation is unchanged).
   *
   * @param scalar the scaling vector
   * @return scaled matrix
   */
  public Matrix3x2 scale(Vector2 scalar) {
    return scale(scalar.x(), scalar.y());
  }

  /**
   * Adds a translation to this matrix.
   *
   * @param x translation X
   * @param y translation Y
   * @return translated matrix
   */
  public Matrix3x2 translate(float x, float y) {
    return new Matrix3x2(m00, m10, m01, m11, m02 + m00 * x + m01 * y, m12 + m10 * x + m11 * y);
  }

  /**
   * Adds a translation to this matrix.
   *
   * @param translation translation vector
   * @return translated matrix
   */
  public Matrix3x2 translate(Vector2 translation) {
    return translate(translation.x(), translation.y());
  }

  /**
   * Applies a shear to this matrix (translation is unchanged).
   *
   * @param x shear along X
   * @param y shear along Y
   * @return sheared matrix
   */
  public Matrix3x2 shear(float x, float y) {
    return new Matrix3x2(m00 + y * m01, m10 + y * m11, m01 + x * m00, m11 + x * m10, m02, m12);
  }

  /**
   * Applies a rotation to the linear part of this matrix (translation is unchanged).
   *
   * @param radians rotation angle in radians
   * @return rotated matrix
   */
  public Matrix3x2 rotate(float radians) {
    float[] sc = FastTrigonometric.sincos(radians);
    float sin = sc[0];
    float cos = sc[1];
    return new Matrix3x2(m00 * cos + m01 * sin, m10 * cos + m11 * sin, m00 * -sin + m01 * cos, m10 * -sin + m11 * cos
        , m02, m12);
  }

  /**
   * Returns the inverse of this affine matrix. Returns {@link #IDENTITY} if the matrix is singular.
   *
   * @return inverse matrix
   */
  public Matrix3x2 invert() {
    float det = determinant();
    if (Math.abs(det) < 1E-10F) {
      return IDENTITY;
    }
    float inv = 1.0F / det;
    return new Matrix3x2(inv * m11, inv * -m10, inv * -m01, inv * m00, inv * (m01 * m12 - m11 * m02),
        inv * (m10 * m02 - m00 * m12));
  }

  /**
   * Transforms a point (applies translation).
   *
   * @param v the point to transform
   * @return transformed point
   */
  public Vector2 transform(Vector2 v) {
    return new Vector2(m00 * v.x() + m01 * v.y() + m02, m10 * v.x() + m11 * v.y() + m12);
  }

  /**
   * Transforms a bounding box by this matrix (computes the axis-aligned bounding box of the four transformed corners).
   *
   * @param box the box to transform
   * @return the transformed bounding box
   */
  public Box2D transform(Box2D box) {
    float x1 = m00 * box.minX() + m01 * box.maxY() + m02;
    float y1 = m10 * box.minX() + m11 * box.maxY() + m12;
    float x2 = m00 * box.maxX() + m01 * box.maxY() + m02;
    float y2 = m10 * box.maxX() + m11 * box.maxY() + m12;
    float x3 = m00 * box.maxX() + m01 * box.minY() + m02;
    float y3 = m10 * box.maxX() + m11 * box.minY() + m12;
    float x4 = m00 * box.minX() + m01 * box.minY() + m02;
    float y4 = m10 * box.minX() + m11 * box.minY() + m12;
    float minX = Math.min(Math.min(x1, x2), Math.min(x3, x4));
    float minY = Math.min(Math.min(y1, y2), Math.min(y3, y4));
    float maxX = Math.max(Math.max(x1, x2), Math.max(x3, x4));
    float maxY = Math.max(Math.max(y1, y2), Math.max(y3, y4));
    return new Box2D(minX, minY, maxX, maxY);
  }

  /**
   * Drops the translation column to produce the 2×2 linear part.
   *
   * @return a 2×2 matrix
   */
  public Matrix2x2 toMatrix2x2() {
    return new Matrix2x2(m00, m10, m01, m11);
  }

  /**
   * Converts to a 3×3 matrix (embeds the affine transform in homogeneous 2D).
   *
   * @return a 3×3 matrix
   */
  public Matrix3x3 toMatrix3x3() {
    return new Matrix3x3(m00, m10, 0.0F, m01, m11, 0.0F, m02, m12, 1.0F);
  }

  /**
   * Converts to a 4×4 matrix.
   *
   * @return a 4×4 matrix
   */
  public Matrix4x4 toMatrix4x4() {
    return new Matrix4x4(m00, m10, 0.0F, 0.0F, m01, m11, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, m02, m12, 0.0F, 1.0F);
  }
}
