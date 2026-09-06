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
 * Immutable unit quaternion for representing 3D rotations.
 *
 * <p>Convention: {@code q = xi + yj + zk + w}, where {@code w} is the scalar part.
 * Quaternions are not automatically normalized on construction — callers are responsible for passing unit quaternions
 * to rotation methods.
 *
 * @param x i component
 * @param y j component
 * @param z k component
 * @param w scalar component
 * @see Euler
 */
public record Quaternion(float x, float y, float z, float w) {
  public static final Quaternion IDENTITY = new Quaternion(0.0F, 0.0F, 0.0F, 1.0F);

  /**
   * Creates a quaternion from an axis and angle.
   *
   * @param axis    rotation axis (assumed normalized)
   * @param radians rotation angle in radians
   * @return rotation quaternion
   */
  public static Quaternion createFromAxisAngle(Vector3 axis, float radians) {
    float half = radians * 0.5F;
    float sin = FastTrigonometric.sin(half);
    Vector3 n = axis.normalize();
    return new Quaternion(n.x() * sin, n.y() * sin, n.z() * sin, FastTrigonometric.cos(half));
  }

  /**
   * Creates a quaternion from Euler angles (Y-X-Z order).
   *
   * @param yaw   rotation around Y axis
   * @param pitch rotation around X axis
   * @param roll  rotation around Z axis
   * @return rotation quaternion
   */
  public static Quaternion createFromEuler(float yaw, float pitch, float roll) {
    float hy = yaw * 0.5F;
    float hp = pitch * 0.5F;
    float hr = roll * 0.5F;
    float[] scy = FastTrigonometric.sincos(hy);
    float sy = scy[0];
    float cy = scy[1];
    float[] scp = FastTrigonometric.sincos(hp);
    float sp = scp[0];
    float cp = scp[1];
    float[] scr = FastTrigonometric.sincos(hr);
    float sr = scr[0];
    float cr = scr[1];
    return new Quaternion(cy * sp * cr + sy * cp * sr, sy * cp * cr - cy * sp * sr, cy * cp * sr - sy * sp * cr,
        cy * cp * cr + sy * sp * sr);
  }

  /**
   * Creates a quaternion from Euler angles.
   *
   * @param e Euler angles
   * @return rotation quaternion
   */
  public static Quaternion createFromEuler(Euler e) {
    return createFromEuler(e.yaw(), e.pitch(), e.roll());
  }

  /**
   * Creates a rotation that maps {@code from} direction to {@code to} direction.
   *
   * @param from source direction
   * @param to   target direction
   * @return rotation quaternion
   */
  public static Quaternion createFromToRotation(Vector3 from, Vector3 to) {
    Vector3 f = from.normalize();
    Vector3 t = to.normalize();
    float d = f.dot(t);
    if (d >= 1.0F - 1E-6F) {
      return IDENTITY;
    }
    if (d <= -1.0F + 1E-6F) {
      Vector3 perpendicular = Math.abs(f.x()) < 0.9F ? f.cross(Vector3.UNIT_X) : f.cross(Vector3.UNIT_Y);
      return createFromAxisAngle(perpendicular.normalize(), (float) Math.PI);
    }
    Vector3 axis = f.cross(t);
    return new Quaternion(axis.x(), axis.y(), axis.z(), 1.0F + d).normalize();
  }

  /**
   * Spherical linear interpolation between two quaternions. Takes the shortest path.
   *
   * @param a start quaternion
   * @param b end quaternion
   * @param t interpolation factor (0 = {@code a}, 1 = {@code b})
   * @return interpolated quaternion
   */
  public static Quaternion slerp(Quaternion a, Quaternion b, float t) {
    float dot = Math.clamp(a.dot(b), -1.0F, 1.0F);
    Quaternion bAdjust = dot < 0.0F ? b.negate() : b;
    dot = Math.abs(dot);

    if (dot > 0.9995F) {
      // Nearly identical — linear interpolation + normalize
      return new Quaternion(a.x + t * (bAdjust.x - a.x), a.y + t * (bAdjust.y - a.y), a.z + t * (bAdjust.z - a.z),
          a.w + t * (bAdjust.w - a.w)).normalize();
    }
    float theta0 = (float) Math.acos(dot);
    float theta = theta0 * t;
    float sinTheta = FastTrigonometric.sin(theta);
    float sinTheta0 = FastTrigonometric.sin(theta0);
    float s0 = FastTrigonometric.cos(theta) - dot * sinTheta / sinTheta0;
    float s1 = sinTheta / sinTheta0;
    return new Quaternion(s0 * a.x + s1 * bAdjust.x, s0 * a.y + s1 * bAdjust.y, s0 * a.z + s1 * bAdjust.z,
        s0 * a.w + s1 * bAdjust.w).normalize();
  }

  /**
   * Normalized linear interpolation between two quaternions. Faster than {@link #slerp} but not constant velocity.
   *
   * @param a start quaternion
   * @param b end quaternion
   * @param t interpolation factor (0 = {@code a}, 1 = {@code b})
   * @return interpolated quaternion
   */
  public static Quaternion lerp(Quaternion a, Quaternion b, float t) {
    float it = 1.0F - t;
    return new Quaternion(a.x * it + b.x * t, a.y * it + b.y * t, a.z * it + b.z * t, a.w * it + b.w * t).normalize();
  }

  /**
   * Returns the sum of this quaternion and another.
   *
   * @param o the other quaternion
   * @return this + o
   */
  public Quaternion add(Quaternion o) {
    return new Quaternion(x + o.x, y + o.y, z + o.z, w + o.w);
  }

  /**
   * Returns the difference of this quaternion and another.
   *
   * @param o the other quaternion
   * @return this - o
   */
  public Quaternion subtract(Quaternion o) {
    return new Quaternion(x - o.x, y - o.y, z - o.z, w - o.w);
  }

  /**
   * Returns the negation of this quaternion.
   *
   * @return -this
   */
  public Quaternion negate() {
    return new Quaternion(-x, -y, -z, -w);
  }

  /**
   * Returns this quaternion scaled by a scalar.
   *
   * @param s scaling factor
   * @return this * s
   */
  public Quaternion multiply(float s) {
    return new Quaternion(x * s, y * s, z * s, w * s);
  }

  /**
   * Hamilton product {@code this * o}.
   *
   * @param o the other quaternion
   * @return product
   */
  public Quaternion multiply(Quaternion o) {
    return new Quaternion(w * o.x + x * o.w + y * o.z - z * o.y, w * o.y - x * o.z + y * o.w + z * o.x,
        w * o.z + x * o.y - y * o.x + z * o.w, w * o.w - x * o.x - y * o.y - z * o.z);
  }

  /**
   * Returns this quaternion divided by a scalar.
   *
   * @param s divisor (must be non-zero)
   * @return this / s
   */
  public Quaternion divide(float s) {
    return multiply(1.0F / s);
  }

  /**
   * Returns the dot product of this quaternion and another.
   *
   * @param o the other quaternion
   * @return dot product
   */
  public float dot(Quaternion o) {
    return x * o.x + y * o.y + z * o.z + w * o.w;
  }

  /**
   * Returns the squared length of this quaternion.
   *
   * @return x² + y² + z² + w²
   */
  public float lengthSquared() {
    return x * x + y * y + z * z + w * w;
  }

  /**
   * Returns the length of this quaternion.
   *
   * @return sqrt(x² + y² + z² + w²)
   */
  public float length() {
    return (float) Math.sqrt(lengthSquared());
  }

  /**
   * Returns a normalized copy of this quaternion. If the length is near zero, returns {@link #IDENTITY}.
   *
   * @return unit quaternion
   */
  public Quaternion normalize() {
    float len = length();
    return len < 1E-10F ? IDENTITY : divide(len);
  }

  /**
   * Returns the conjugate of this quaternion.
   *
   * @return conjugate
   */
  public Quaternion conjugate() {
    return new Quaternion(-x, -y, -z, w);
  }

  /**
   * Returns the inverse of this quaternion. For unit quaternions, this is the same as {@link #conjugate}.
   *
   * @return inverse
   */
  public Quaternion invert() {
    float lenSq = lengthSquared();
    if (lenSq < 1E-10F) {
      return IDENTITY;
    }
    return conjugate().divide(lenSq);
  }

  /**
   * Rotates a 3D vector by this quaternion using the formula {@code q * v * q⁻¹}.
   *
   * @param v vector to rotate
   * @return rotated vector
   */
  public Vector3 rotate(Vector3 v) {
    float tx = 2.0F * (y * v.z() - z * v.y());
    float ty = 2.0F * (z * v.x() - x * v.z());
    float tz = 2.0F * (x * v.y() - y * v.x());
    return new Vector3(v.x() + w * tx + (y * tz - z * ty), v.y() + w * ty + (z * tx - x * tz),
        v.z() + w * tz + (x * ty - y * tx));
  }

  /**
   * Decomposes this quaternion into an axis and angle.
   *
   * @return {@code float[4] = { axisX, axisY, axisZ, angleRadians }}
   */
  public float[] toAxisAngle() {
    Quaternion q = normalize();
    float angle = 2.0F * (float) Math.acos(Math.clamp(q.w, -1.0F, 1.0F));
    float s = (float) Math.sqrt(1.0F - q.w * q.w);
    if (s < 1E-10F) {
      return new float[] {1.0F, 0.0F, 0.0F, angle};
    }
    return new float[] {q.x / s, q.y / s, q.z / s, angle};
  }

  /**
   * Converts to Euler angles (yaw, pitch, roll) in radians (YXZ order).
   *
   * @return Euler angles
   */
  public Euler toEuler() {
    float sinPitch = 2.0F * (w * x - y * z);
    float pitch;
    if (Math.abs(sinPitch) >= 1.0F) {
      pitch = (float) Math.copySign(Math.PI / 2.0F, sinPitch);
    } else {
      pitch = (float) Math.asin(sinPitch);
    }
    float yaw = (float) Math.atan2(2.0F * (w * y + x * z), 1.0F - 2.0F * (x * x + y * y));
    float roll = (float) Math.atan2(2.0F * (w * z + x * y), 1.0F - 2.0F * (x * x + z * z));
    return new Euler(yaw, pitch, roll);
  }
}