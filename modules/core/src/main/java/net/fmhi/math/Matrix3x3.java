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
 * Immutable column-major 3×3 matrix.
 *
 * <p>Storage: {@code m[col][row]}, i.e. {@code m00} is column 0, row 0.
 * Multiplication follows standard math convention: {@code A * B} = A then B applied.
 *
 * @param m00 column 0, row 0
 * @param m10 column 1, row 0
 * @param m20 column 2, row 0
 * @param m01 column 0, row 1
 * @param m11 column 1, row 1
 * @param m21 column 2, row 1
 * @param m02 column 0, row 2
 * @param m12 column 1, row 2
 * @param m22 column 2, row 2
 * @see Matrix2x2
 * @see Matrix3x2
 * @see Matrix4x4
 */
public record Matrix3x3(float m00, float m10, float m20, float m01, float m11, float m21, float m02, float m12,
                        float m22) {
  public static final Matrix3x3 IDENTITY = new Matrix3x3(1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F);

  /**
   * Creates a uniform scaling matrix.
   *
   * @param scalar uniform scaling factor
   * @return scaling matrix
   */
  public static Matrix3x3 createScale(float scalar) {
    return createScale(scalar, scalar, scalar);
  }

  /**
   * Creates a scaling matrix.
   *
   * @param scalarX scaling factor along X
   * @param scalarY scaling factor along Y
   * @param scalarZ scaling factor along Z
   * @return scaling matrix
   */
  public static Matrix3x3 createScale(float scalarX, float scalarY, float scalarZ) {
    return new Matrix3x3(scalarX, 0.0F, 0.0F, 0.0F, scalarY, 0.0F, 0.0F, 0.0F, scalarZ);
  }

  /**
   * Creates a scaling matrix.
   *
   * @param scalar the scaling vector
   * @return scaling matrix
   */
  public static Matrix3x3 createScale(Vector3 scalar) {
    return createScale(scalar.x(), scalar.y(), scalar.z());
  }

  /**
   * Creates a rotation matrix around the X axis.
   *
   * @param radians rotation angle in radians
   * @return rotation matrix
   */
  public static Matrix3x3 createRotationX(float radians) {
    float[] sc = FastTrigonometric.sincos(radians);
    float sin = sc[0];
    float cos = sc[1];
    return new Matrix3x3(1.0F, 0.0F, 0.0F, 0.0F, cos, sin, 0.0F, -sin, cos);
  }

  /**
   * Creates a rotation matrix around the Y axis.
   *
   * @param radians rotation angle in radians
   * @return rotation matrix
   */
  public static Matrix3x3 createRotationY(float radians) {
    float[] sc = FastTrigonometric.sincos(radians);
    float sin = sc[0];
    float cos = sc[1];
    return new Matrix3x3(cos, 0.0F, -sin, 0.0F, 1.0F, 0.0F, sin, 0.0F, cos);
  }

  /**
   * Creates a rotation matrix around the Z axis.
   *
   * @param radians rotation angle in radians
   * @return rotation matrix
   */
  public static Matrix3x3 createRotationZ(float radians) {
    float[] sc = FastTrigonometric.sincos(radians);
    float sin = sc[0];
    float cos = sc[1];
    return new Matrix3x3(cos, sin, 0.0F, -sin, cos, 0.0F, 0.0F, 0.0F, 1.0F);
  }

  /**
   * Creates a rotation matrix around an arbitrary axis.
   *
   * @param radians rotation angle in radians
   * @param axis    the axis of rotation (will be normalized)
   * @return rotation matrix
   */
  public static Matrix3x3 createRotation(float radians, Vector3 axis) {
    return createByQuaternion(Quaternion.createFromAxisAngle(axis, radians));
  }

  /**
   * Creates a rotation matrix from a quaternion.
   *
   * @param q the quaternion
   * @return rotation matrix
   */
  public static Matrix3x3 createByQuaternion(Quaternion q) {
    float x = q.x();
    float y = q.y();
    float z = q.z();
    float w = q.w();
    float xx = x * x;
    float yy = y * y;
    float zz = z * z;
    float xy = x * y;
    float xz = x * z;
    float yz = y * z;
    float wx = w * x;
    float wy = w * y;
    float wz = w * z;
    return new Matrix3x3(1 - 2 * (yy + zz), 2 * (xy + wz), 2 * (xz - wy), 2 * (xy - wz), 1 - 2 * (xx + zz),
        2 * (yz + wx), 2 * (xz + wy), 2 * (yz - wx), 1 - 2 * (xx + yy));
  }

  /**
   * Creates a shear matrix.
   *
   * @param xy shear XY
   * @param xz shear XZ
   * @param yx shear YX
   * @param yz shear YZ
   * @param zx shear ZX
   * @param zy shear ZY
   * @return shear matrix
   */
  public static Matrix3x3 createShear(float xy, float xz, float yx, float yz, float zx, float zy) {
    return new Matrix3x3(1.0F, yx, zx, xy, 1.0F, zy, xz, yz, 1.0F);
  }

  /**
   * Creates a combined 2D transform (scale + rotation + translation in homogeneous coordinates).
   *
   * @param translation translation vector
   * @param rotation    rotation angle in radians
   * @param scalar      scale vector
   * @return transformation matrix
   */
  public static Matrix3x3 createTransform(Vector2 translation, float rotation, Vector2 scalar) {
    float[] sc = FastTrigonometric.sincos(rotation);
    float sin = sc[0];
    float cos = sc[1];
    return new Matrix3x3(scalar.x() * cos, scalar.x() * sin, 0.0F, scalar.y() * -sin, scalar.y() * cos, 0.0F,
        translation.x(), translation.y(), 1.0F);
  }

  /**
   * Creates a 2D orthographic projection matrix.
   *
   * @param left   left boundary
   * @param right  right boundary
   * @param bottom bottom boundary
   * @param top    top boundary
   * @return orthographic matrix
   */
  public static Matrix3x3 createOrthographic(float left, float right, float bottom, float top) {
    return new Matrix3x3(2.0F / (right - left), 0.0F, 0.0F, 0.0F, 2.0F / (top - bottom), 0.0F, 0.0F, 0.0F, 1.0F);
  }

  /**
   * Linearly interpolates between two matrices.
   *
   * @param a start matrix
   * @param b end matrix
   * @param t interpolation factor (0 = {@code a}, 1 = {@code b})
   * @return interpolated matrix
   */
  public static Matrix3x3 lerp(Matrix3x3 a, Matrix3x3 b, float t) {
    t = Math.clamp(t, 0.0F, 1.0F);
    return new Matrix3x3(a.m00 + (b.m00 - a.m00) * t, a.m10 + (b.m10 - a.m10) * t, a.m20 + (b.m20 - a.m20) * t,
        a.m01 + (b.m01 - a.m01) * t, a.m11 + (b.m11 - a.m11) * t, a.m21 + (b.m21 - a.m21) * t,
        a.m02 + (b.m02 - a.m02) * t, a.m12 + (b.m12 - a.m12) * t, a.m22 + (b.m22 - a.m22) * t);
  }

  /**
   * Returns the determinant of this matrix.
   *
   * @return determinant
   */
  public float determinant() {
    return m00 * (m11 * m22 - m21 * m12) - m01 * (m10 * m22 - m20 * m12) + m02 * (m10 * m21 - m20 * m11);
  }

  /**
   * Multiplies this matrix by another.
   *
   * @param o the other matrix
   * @return this * o
   */
  public Matrix3x3 multiply(Matrix3x3 o) {
    return new Matrix3x3(m00 * o.m00 + m01 * o.m10 + m02 * o.m20, m10 * o.m00 + m11 * o.m10 + m12 * o.m20,
        m20 * o.m00 + m21 * o.m10 + m22 * o.m20, m00 * o.m01 + m01 * o.m11 + m02 * o.m21,
        m10 * o.m01 + m11 * o.m11 + m12 * o.m21, m20 * o.m01 + m21 * o.m11 + m22 * o.m21,
        m00 * o.m02 + m01 * o.m12 + m02 * o.m22, m10 * o.m02 + m11 * o.m12 + m12 * o.m22,
        m20 * o.m02 + m21 * o.m12 + m22 * o.m22);
  }

  /**
   * Scales all elements by a scalar.
   *
   * @param scalar the scaling factor
   * @return scaled matrix
   */
  public Matrix3x3 scale(float scalar) {
    return scale(scalar, scalar, scalar);
  }

  /**
   * Scales each column independently.
   *
   * @param scalarX scale for column X
   * @param scalarY scale for column Y
   * @param scalarZ scale for column Z
   * @return scaled matrix
   */
  public Matrix3x3 scale(float scalarX, float scalarY, float scalarZ) {
    return new Matrix3x3(m00 * scalarX, m10 * scalarX, m20 * scalarX, m01 * scalarY, m11 * scalarY, m21 * scalarY,
        m02 * scalarZ, m12 * scalarZ, m22 * scalarZ);
  }

  /**
   * Scales each column by the components of a vector.
   *
   * @param scalar the scaling vector
   * @return scaled matrix
   */
  public Matrix3x3 scale(Vector3 scalar) {
    return scale(scalar.x(), scalar.y(), scalar.z());
  }

  /**
   * Applies a shear transformation.
   *
   * @param xy shear XY
   * @param xz shear XZ
   * @param yx shear YX
   * @param yz shear YZ
   * @param zx shear ZX
   * @param zy shear ZY
   * @return sheared matrix
   */
  public Matrix3x3 shear(float xy, float xz, float yx, float yz, float zx, float zy) {
    return new Matrix3x3(m00 + m01 * xy + m02 * xz, m10 + m11 * xy + m12 * xz, m20 + m21 * xy + m22 * xz,
        m01 + m00 * yx + m02 * yz, m11 + m10 * yx + m12 * yz, m21 + m20 * yx + m22 * yz, m02 + m00 * zx + m01 * zy,
        m12 + m10 * zx + m11 * zy, m22 + m20 * zx + m21 * zy);
  }

  /**
   * Applies a rotation around the X axis.
   *
   * @param radians rotation angle in radians
   * @return rotated matrix
   */
  public Matrix3x3 rotateX(float radians) {
    float[] sc = FastTrigonometric.sincos(radians);
    float sin = sc[0];
    float cos = sc[1];
    return new Matrix3x3(m00, m10 * cos + m20 * sin, m10 * -sin + m20 * cos, m01, m11 * cos + m21 * sin,
        m11 * -sin + m21 * cos, m02, m12 * cos + m22 * sin, m12 * -sin + m22 * cos);
  }

  /**
   * Applies a rotation around the Y axis.
   *
   * @param radians rotation angle in radians
   * @return rotated matrix
   */
  public Matrix3x3 rotateY(float radians) {
    float[] sc = FastTrigonometric.sincos(radians);
    float sin = sc[0];
    float cos = sc[1];
    return new Matrix3x3(m00 * cos + m02 * -sin, m10 * cos + m12 * -sin, m20 * cos + m22 * -sin, m01, m11, m21,
        m00 * sin + m02 * cos, m10 * sin + m12 * cos, m20 * sin + m22 * cos);
  }

  /**
   * Applies a rotation around the Z axis.
   *
   * @param radians rotation angle in radians
   * @return rotated matrix
   */
  public Matrix3x3 rotateZ(float radians) {
    float[] sc = FastTrigonometric.sincos(radians);
    float sin = sc[0];
    float cos = sc[1];
    return new Matrix3x3(m00 * cos + m01 * sin, m10 * cos + m11 * sin, m20 * cos + m21 * sin, m00 * -sin + m01 * cos,
        m10 * -sin + m11 * cos, m20 * -sin + m21 * cos, m02, m12, m22);
  }

  /**
   * Applies a rotation around an arbitrary axis.
   *
   * @param axis    the rotation axis
   * @param radians rotation angle in radians
   * @return rotated matrix
   */
  public Matrix3x3 rotate(Vector3 axis, float radians) {
    return multiply(createByQuaternion(Quaternion.createFromAxisAngle(axis, radians)));
  }

  /**
   * Returns the transpose of this matrix.
   *
   * @return transposed matrix
   */
  public Matrix3x3 transpose() {
    return new Matrix3x3(m00, m01, m02, m10, m11, m12, m20, m21, m22);
  }

  /**
   * Returns the inverse of this matrix. Returns {@link #IDENTITY} if the matrix is singular.
   *
   * @return inverse matrix
   */
  public Matrix3x3 invert() {
    float det = determinant();
    if (Math.abs(det) < 1E-10F) {
      return IDENTITY;
    }
    float inv = 1.0F / det;
    return new Matrix3x3((m11 * m22 - m21 * m12) * inv, (m12 * m20 - m22 * m10) * inv, (m10 * m21 - m20 * m11) * inv,
        (m02 * m21 - m01 * m22) * inv, (m00 * m22 - m02 * m20) * inv, (m01 * m20 - m00 * m21) * inv,
        (m01 * m12 - m02 * m11) * inv, (m02 * m10 - m00 * m12) * inv, (m00 * m11 - m01 * m10) * inv);
  }

  /**
   * Transforms a vector by this matrix.
   *
   * @param v the vector to transform
   * @return transformed vector
   */
  public Vector3 transform(Vector3 v) {
    return new Vector3(m00 * v.x() + m01 * v.y() + m02 * v.z(), m10 * v.x() + m11 * v.y() + m12 * v.z(),
        m20 * v.x() + m21 * v.y() + m22 * v.z());
  }

  /**
   * Converts to a 2×2 matrix (drops the Z components).
   *
   * @return a 2×2 matrix
   */
  public Matrix2x2 toMatrix2x2() {
    return new Matrix2x2(m00, m10, m01, m11);
  }

  /**
   * Converts to a 3×2 affine matrix (drops the last row).
   *
   * @return a 3×2 matrix
   */
  public Matrix3x2 toMatrix3x2() {
    return new Matrix3x2(m00, m10, m01, m11, m02, m12);
  }

  /**
   * Converts to a 4×4 matrix.
   *
   * @return a 4×4 matrix
   */
  public Matrix4x4 toMatrix4x4() {
    return new Matrix4x4(m00, m10, m20, 0.0F, m01, m11, m21, 0.0F, m02, m12, m22, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
  }
}
