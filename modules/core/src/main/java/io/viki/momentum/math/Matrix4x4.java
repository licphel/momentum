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
 * Immutable column-major 4×4 matrix.
 *
 * <p>Storage: {@code m[col][row]}, i.e. {@code m00} is column 0.0F, row 0.
 * Multiplication follows standard math convention: {@code A * B} = A then B applied.
 *
 * @param m00 column 0.0F, row 0
 * @param m10 column 1.0F, row 0
 * @param m20 column 2, row 0
 * @param m30 column 3, row 0
 * @param m01 column 0.0F, row 1
 * @param m11 column 1.0F, row 1
 * @param m21 column 2, row 1
 * @param m31 column 3, row 1
 * @param m02 column 0.0F, row 2
 * @param m12 column 1.0F, row 2
 * @param m22 column 2, row 2
 * @param m32 column 3, row 2
 * @param m03 column 0.0F, row 3
 * @param m13 column 1.0F, row 3
 * @param m23 column 2, row 3
 * @param m33 column 3, row 3
 * @see Matrix2x2
 * @see Matrix3x2
 * @see Matrix3x3
 */
public record Matrix4x4(float m00, float m10, float m20, float m30, float m01, float m11, float m21, float m31,
                        float m02, float m12, float m22, float m32, float m03, float m13, float m23, float m33) {
  public static final Matrix4x4 IDENTITY = new Matrix4x4(1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F,
      1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);

  /**
   * Creates a translation matrix.
   *
   * @param tx x translation
   * @param ty y translation
   * @param tz z translation
   * @return translation matrix
   */
  public static Matrix4x4 createTranslation(float tx, float ty, float tz) {
    return new Matrix4x4(1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, tx, ty, tz, 1.0F);
  }

  /**
   * Creates a translation matrix.
   *
   * @param t translation vector
   * @return translation matrix
   */
  public static Matrix4x4 createTranslation(Vector3 t) {
    return createTranslation(t.x(), t.y(), t.z());
  }

  /**
   * Creates a scale matrix.
   *
   * @param sx x scale factor
   * @param sy y scale factor
   * @param sz z scale factor
   * @return scale matrix
   */
  public static Matrix4x4 createScale(float sx, float sy, float sz) {
    return new Matrix4x4(sx, 0.0F, 0.0F, 0.0F, 0.0F, sy, 0.0F, 0.0F, 0.0F, 0.0F, sz, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
  }

  /**
   * Creates a scale matrix.
   *
   * @param s scale vector
   * @return scale matrix
   */
  public static Matrix4x4 createScale(Vector3 s) {
    return createScale(s.x(), s.y(), s.z());
  }

  /**
   * Creates a rotation matrix around the X axis.
   *
   * @param radians rotation angle in radians
   * @return rotation matrix
   */
  public static Matrix4x4 createRotationX(float radians) {
    float[] sc = FastTrigonometric.sincos(radians);
    float s = sc[0];
    float c = sc[1];
    return new Matrix4x4(1.0F, 0.0F, 0.0F, 0.0F, 0.0F, c, s, 0.0F, 0.0F, -s, c, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
  }

  /**
   * Creates a rotation matrix around the Y axis.
   *
   * @param radians rotation angle in radians
   * @return rotation matrix
   */
  public static Matrix4x4 createRotationY(float radians) {
    float[] sc = FastTrigonometric.sincos(radians);
    float s = sc[0];
    float c = sc[1];
    return new Matrix4x4(c, 0.0F, -s, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, s, 0.0F, c, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
  }

  /**
   * Creates a rotation matrix around the Z axis.
   *
   * @param radians rotation angle in radians
   * @return rotation matrix
   */
  public static Matrix4x4 createRotationZ(float radians) {
    float[] sc = FastTrigonometric.sincos(radians);
    float s = sc[0];
    float c = sc[1];
    return new Matrix4x4(c, s, 0.0F, 0.0F, -s, c, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
  }

  /**
   * Creates a rotation matrix around an arbitrary axis.
   *
   * @param axis    rotation axis (assumed normalized)
   * @param radians rotation angle in radians
   * @return rotation matrix
   */
  public static Matrix4x4 createRotation(Vector3 axis, float radians) {
    return createFromQuaternion(Quaternion.createFromAxisAngle(axis, radians));
  }

  /**
   * Creates a rotation matrix from a quaternion.
   *
   * @param q rotation quaternion (assumed unit)
   * @return rotation matrix
   */
  public static Matrix4x4 createFromQuaternion(Quaternion q) {
    float qx = q.x();
    float qy = q.y();
    float qz = q.z();
    float qw = q.w();
    return new Matrix4x4(1.0F - 2.0F * (qy * qy + qz * qz), 2.0F * (qx * qy + qz * qw), 2.0F * (qx * qz - qy * qw),
        0.0F, 2.0F * (qx * qy - qz * qw), 1.0F - 2.0F * (qx * qx + qz * qz), 2.0F * (qy * qz + qx * qw), 0.0F,
        2.0F * (qx * qz + qy * qw), 2.0F * (qy * qz - qx * qw), 1.0F - 2.0F * (qx * qx + qy * qy), 0.0F, 0.0F, 0.0F,
        0.0F, 1.0F);
  }

  /**
   * Creates a combined transform matrix (translation * rotation * scale).
   *
   * @param translation translation component
   * @param rotation    rotation component
   * @param scale       scale component
   * @return transform matrix
   */
  public static Matrix4x4 createTransform(Vector3 translation, Quaternion rotation, Vector3 scale) {
    return createTranslation(translation).multiply(createFromQuaternion(rotation)).multiply(createScale(scale));
  }

  /**
   * Creates a right-handed look-at view matrix (Y-up).
   *
   * @param eye    camera position
   * @param target look-at target
   * @param up     up direction (usually {@link Vector3#UNIT_Y})
   * @return view matrix
   */
  public static Matrix4x4 createLookAt(Vector3 eye, Vector3 target, Vector3 up) {
    Vector3 f = target.subtract(eye).normalize();
    Vector3 r = f.cross(up).normalize();
    Vector3 u = r.cross(f);
    return new Matrix4x4(r.x(), u.x(), -f.x(), 0.0F, r.y(), u.y(), -f.y(), 0.0F, r.z(), u.z(), -f.z(), 0.0F,
        -r.dot(eye), -u.dot(eye), f.dot(eye), 1.0F);
  }

  /**
   * Creates a right-handed perspective projection matrix.
   *
   * <p>Depth range: {@code [0, 1]} (Vulkan/Metal/DirectX convention).
   *
   * @param fovY   vertical field of view in radians
   * @param aspect width / height
   * @param near   near clip plane (positive)
   * @param far    far clip plane (positive)
   * @return projection matrix
   */
  public static Matrix4x4 createPerspective(float fovY, float aspect, float near, float far) {
    float tanHalfFov = (float) Math.tan(fovY * 0.5F);
    float f = 1.0F / tanHalfFov;
    float nf = 1.0F / (near - far);
    return new Matrix4x4(f / aspect, 0.0F, 0.0F, 0.0F, 0.0F, f, 0.0F, 0.0F, 0.0F, 0.0F, (far + near) * nf, -1.0F,
        0.0F, 0.0F, 2.0F * far * near * nf, 0.0F);
  }

  /**
   * Creates a right-handed orthographic projection matrix with {@code [0, 1]} depth range.
   *
   * @param left   left plane
   * @param right  right plane
   * @param bottom bottom plane
   * @param top    top plane
   * @param near   near clip plane
   * @param far    far clip plane
   * @return projection matrix
   */
  public static Matrix4x4 createOrthographic(float left, float right, float bottom, float top, float near, float far) {
    float rl = 1.0F / (right - left);
    float tb = 1.0F / (top - bottom);
    float fn = 1.0F / (far - near);
    return new Matrix4x4(2.0F * rl, 0.0F, 0.0F, 0.0F, 0.0F, 2.0F * tb, 0.0F, 0.0F, 0.0F, 0.0F, -fn, 0.0F,
        -(right + left) * rl, -(top + bottom) * tb, -near * fn, 1.0F);
  }

  /**
   * Linearly interpolates between two matrices component-wise.
   *
   * @param a start matrix
   * @param b end matrix
   * @param t interpolation factor (0 = {@code a}, 1 = {@code b})
   * @return interpolated matrix
   */
  public static Matrix4x4 lerp(Matrix4x4 a, Matrix4x4 b, float t) {
    float it = 1.0F - t;
    return new Matrix4x4(a.m00 * it + b.m00 * t, a.m10 * it + b.m10 * t, a.m20 * it + b.m20 * t,
        a.m30 * it + b.m30 * t, a.m01 * it + b.m01 * t, a.m11 * it + b.m11 * t, a.m21 * it + b.m21 * t,
        a.m31 * it + b.m31 * t, a.m02 * it + b.m02 * t, a.m12 * it + b.m12 * t, a.m22 * it + b.m22 * t,
        a.m32 * it + b.m32 * t, a.m03 * it + b.m03 * t, a.m13 * it + b.m13 * t, a.m23 * it + b.m23 * t,
        a.m33 * it + b.m33 * t);
  }

  /**
   * Returns the matrix product {@code this * b}.
   *
   * @param b right-hand matrix
   * @return multiplied matrix
   */
  public Matrix4x4 multiply(Matrix4x4 b) {
    return new Matrix4x4(m00 * b.m00 + m01 * b.m10 + m02 * b.m20 + m03 * b.m30,
        m10 * b.m00 + m11 * b.m10 + m12 * b.m20 + m13 * b.m30, m20 * b.m00 + m21 * b.m10 + m22 * b.m20 + m23 * b.m30,
        m30 * b.m00 + m31 * b.m10 + m32 * b.m20 + m33 * b.m30,

        m00 * b.m01 + m01 * b.m11 + m02 * b.m21 + m03 * b.m31, m10 * b.m01 + m11 * b.m11 + m12 * b.m21 + m13 * b.m31,
        m20 * b.m01 + m21 * b.m11 + m22 * b.m21 + m23 * b.m31, m30 * b.m01 + m31 * b.m11 + m32 * b.m21 + m33 * b.m31,

        m00 * b.m02 + m01 * b.m12 + m02 * b.m22 + m03 * b.m32, m10 * b.m02 + m11 * b.m12 + m12 * b.m22 + m13 * b.m32,
        m20 * b.m02 + m21 * b.m12 + m22 * b.m22 + m23 * b.m32, m30 * b.m02 + m31 * b.m12 + m32 * b.m22 + m33 * b.m32,

        m00 * b.m03 + m01 * b.m13 + m02 * b.m23 + m03 * b.m33, m10 * b.m03 + m11 * b.m13 + m12 * b.m23 + m13 * b.m33,
        m20 * b.m03 + m21 * b.m13 + m22 * b.m23 + m23 * b.m33, m30 * b.m03 + m31 * b.m13 + m32 * b.m23 + m33 * b.m33);
  }

  /**
   * Transforms a 3D position (w = 1).
   *
   * @param v position vector
   * @return transformed position
   */
  public Vector3 transform(Vector3 v) {
    float x = m00 * v.x() + m01 * v.y() + m02 * v.z() + m03;
    float y = m10 * v.x() + m11 * v.y() + m12 * v.z() + m13;
    float z = m20 * v.x() + m21 * v.y() + m22 * v.z() + m23;
    return new Vector3(x, y, z);
  }

  /**
   * Transforms a 3D/4D position (w = 1).
   *
   * @param v position vector, must be 3D/4D
   * @throws IllegalArgumentException if input array is not 3D/4D
   */
  public void transformInplace(float[] v) {
    if (v.length == 3) {
      float x = m00 * v[0] + m01 * v[1] + m02 * v[2] + m03;
      float y = m10 * v[0] + m11 * v[1] + m12 * v[2] + m13;
      float z = m20 * v[0] + m21 * v[1] + m22 * v[2] + m23;
      v[0] = x;
      v[1] = y;
      v[2] = z;
    } else if (v.length == 4) {
      float x = m00 * v[0] + m01 * v[1] + m02 * v[2] + m03 * v[3];
      float y = m10 * v[0] + m11 * v[1] + m12 * v[2] + m13 * v[3];
      float z = m20 * v[0] + m21 * v[1] + m22 * v[2] + m23 * v[3];
      float w = m30 * v[0] + m31 * v[1] + m32 * v[2] + m33 * v[3];
      v[0] = x;
      v[1] = y;
      v[2] = z;
      v[3] = w;
    } else {
      throw new IllegalArgumentException("Not 3D/4D vector");
    }
  }

  /**
   * Transforms a 4D vector.
   *
   * @param v vector to transform
   * @return transformed vector (caller may need perspective divide)
   */
  public Vector4 transform(Vector4 v) {
    float x = m00 * v.x() + m01 * v.y() + m02 * v.z() + m03 * v.w();
    float y = m10 * v.x() + m11 * v.y() + m12 * v.z() + m13 * v.w();
    float z = m20 * v.x() + m21 * v.y() + m22 * v.z() + m23 * v.w();
    float w = m30 * v.x() + m31 * v.y() + m32 * v.z() + m33 * v.w();
    return new Vector4(x, y, z, w);
  }

  /**
   * Returns the transpose of this matrix.
   *
   * @return transposed matrix
   */
  public Matrix4x4 transpose() {
    return new Matrix4x4(m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23, m30, m31, m32, m33);
  }

  /**
   * Returns the determinant of this matrix.
   *
   * @return determinant
   */
  public float determinant() {
    float c0 = m11 * (m22 * m33 - m32 * m23) - m21 * (m12 * m33 - m32 * m13) + m31 * (m12 * m23 - m22 * m13);
    float c1 = -(m01 * (m22 * m33 - m32 * m23) - m21 * (m02 * m33 - m32 * m03) + m31 * (m02 * m23 - m22 * m03));
    float c2 = m01 * (m12 * m33 - m32 * m13) - m11 * (m02 * m33 - m32 * m03) + m31 * (m02 * m13 - m12 * m03);
    float c3 = -(m01 * (m12 * m23 - m22 * m13) - m11 * (m02 * m23 - m22 * m03) + m21 * (m02 * m13 - m12 * m03));
    return m00 * c0 + m10 * c1 + m20 * c2 + m30 * c3;
  }

  /**
   * Returns the inverse of this matrix. If the matrix is singular (determinant near zero), returns {@link #IDENTITY}.
   *
   * @return inverse matrix
   */
  public Matrix4x4 invert() {
    float det = determinant();
    if (Math.abs(det) < 1E-10F) {
      return IDENTITY;
    }

    float invDet = 1.0F / det;
    return new Matrix4x4(
        (m11 * m22 * m33 - m11 * m32 * m23 - m12 * m21 * m33 + m12 * m31 * m23 + m13 * m21 * m32 - m13 * m31 * m22) * invDet,
        (-m10 * m22 * m33 + m10 * m32 * m23 + m12 * m20 * m33 - m12 * m30 * m23 - m13 * m20 * m32 + m13 * m30 * m22) * invDet,
        (m10 * m21 * m33 - m10 * m31 * m23 - m11 * m20 * m33 + m11 * m30 * m23 + m13 * m20 * m31 - m13 * m30 * m21) * invDet,
        (-m10 * m21 * m32 + m10 * m31 * m22 + m11 * m20 * m32 - m11 * m30 * m22 - m12 * m20 * m31 + m12 * m30 * m21) * invDet,
        (-m01 * m22 * m33 + m01 * m32 * m23 + m02 * m21 * m33 - m02 * m31 * m23 - m03 * m21 * m32 + m03 * m31 * m22) * invDet,
        (m00 * m22 * m33 - m00 * m32 * m23 - m02 * m20 * m33 + m02 * m30 * m23 + m03 * m20 * m32 - m03 * m30 * m22) * invDet,
        (-m00 * m21 * m33 + m00 * m31 * m23 + m01 * m20 * m33 - m01 * m30 * m23 - m03 * m20 * m31 + m03 * m30 * m21) * invDet,
        (m00 * m21 * m32 - m00 * m31 * m22 - m01 * m20 * m32 + m01 * m30 * m22 + m02 * m20 * m31 - m02 * m30 * m21) * invDet,
        (m01 * m12 * m33 - m01 * m32 * m13 - m02 * m11 * m33 + m02 * m31 * m13 + m03 * m11 * m32 - m03 * m31 * m12) * invDet,
        (-m00 * m12 * m33 + m00 * m32 * m13 + m02 * m10 * m33 - m02 * m30 * m13 - m03 * m10 * m32 + m03 * m30 * m12) * invDet,
        (m00 * m11 * m33 - m00 * m31 * m13 - m01 * m10 * m33 + m01 * m30 * m13 + m03 * m10 * m31 - m03 * m30 * m11) * invDet,
        (-m00 * m11 * m32 + m00 * m31 * m12 + m01 * m10 * m32 - m01 * m30 * m12 - m02 * m10 * m31 + m02 * m30 * m11) * invDet,
        (-m01 * m12 * m23 + m01 * m22 * m13 + m02 * m11 * m23 - m02 * m21 * m13 - m03 * m11 * m22 + m03 * m21 * m12) * invDet,
        (m00 * m12 * m23 - m00 * m22 * m13 - m02 * m10 * m23 + m02 * m20 * m13 + m03 * m10 * m22 - m03 * m20 * m12) * invDet,
        (-m00 * m11 * m23 + m00 * m21 * m13 + m01 * m10 * m23 - m01 * m20 * m13 - m03 * m10 * m21 + m03 * m20 * m11) * invDet,
        (m00 * m11 * m22 - m00 * m21 * m12 - m01 * m10 * m22 + m01 * m20 * m12 + m02 * m10 * m21 - m02 * m20 * m11) * invDet
    );
  }

  /**
   * Extracts the upper-left 3x3 submatrix (rotation/scale component).
   *
   * @return a 3x3 matrix
   */
  public Matrix3x3 toMatrix3x3() {
    return new Matrix3x3(m00, m01, m02, m10, m11, m12, m20, m21, m22);
  }

  /**
   * Extracts the upper-left 3x2 submatrix.
   *
   * @return a 3x2 matrix
   */
  public Matrix3x2 toMatrix3x2() {
    return new Matrix3x2(m00, m01, m10, m11, m20, m21);
  }

  /**
   * Extracts the upper-left 2x2 submatrix.
   *
   * @return a 2x2 matrix
   */
  public Matrix2x2 toMatrix2x2() {
    return new Matrix2x2(m00, m01, m10, m11);
  }
}