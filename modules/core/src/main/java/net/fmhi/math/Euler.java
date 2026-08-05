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
 * Euler angles in radians (Y-X-Z order, also known as yaw-pitch-roll).
 *
 * <p>Euler angles represent a rotation as three successive rotations around
 * the principal axes. This class uses the common Y-X-Z (yaw-pitch-roll) order:
 * <ol>
 *   <li>Yaw: rotation around the Y axis (up/down)</li>
 *   <li>Pitch: rotation around the X axis (left/right tilt)</li>
 *   <li>Roll: rotation around the Z axis (bank)</li>
 * </ol>
 *
 * <p>Note: Euler angles are prone to gimbal lock and interpolation issues.
 * For most rotation operations, prefer {@link Quaternion}.
 *
 * @param yaw   rotation around the Y axis in radians (range: -π to π)
 * @param pitch rotation around the X axis in radians (range: -π/2 to π/2)
 * @param roll  rotation around the Z axis in radians (range: -π to π)
 * @see Quaternion
 */
public record Euler(float yaw, float pitch, float roll) {
  public static final Euler ZERO = new Euler(0.0F, 0.0F, 0.0F);

  /**
   * Normalizes an angle to the range [-π, π].
   */
  private static float normalizeAngle(float angle) {
    float twoPi = (float) (Math.PI * 2);
    float a = angle % twoPi;
    if (a > Math.PI) {
      a -= twoPi;
    }
    if (a < -Math.PI) {
      a += twoPi;
    }
    return a;
  }

  /**
   * Normalizes pitch to the range [-π/2, π/2].
   */
  private static float normalizePitch(float pitch) {
    float halfPi = (float) (Math.PI / 2);
    if (pitch > halfPi) {
      return halfPi;
    }
    return Math.max(pitch, -halfPi);
  }

  /**
   * Clamps an angle to the range [-π, π].
   */
  private static float clampAngle(float angle) {
    float pi = (float) Math.PI;
    return Math.clamp(angle, -pi, pi);
  }

  /**
   * Clamps pitch to the range [-π/2, π/2].
   */
  private static float clampPitch(float pitch) {
    float halfPi = (float) (Math.PI / 2);
    return Math.clamp(pitch, -halfPi, halfPi);
  }

  /**
   * Returns the sum of this Euler angle and another (component-wise).
   *
   * @param o the other Euler angle
   * @return component-wise sum
   */
  public Euler add(Euler o) {
    return new Euler(yaw + o.yaw, pitch + o.pitch, roll + o.roll);
  }

  /**
   * Returns the difference of this Euler angle and another (component-wise).
   *
   * @param o the other Euler angle
   * @return component-wise difference
   */
  public Euler subtract(Euler o) {
    return new Euler(yaw - o.yaw, pitch - o.pitch, roll - o.roll);
  }

  /**
   * Returns this Euler angle scaled by a scalar.
   *
   * @param s scaling factor
   * @return scaled Euler angle
   */
  public Euler multiply(float s) {
    return new Euler(yaw * s, pitch * s, roll * s);
  }

  /**
   * Returns the negation of this Euler angle.
   *
   * @return component-wise negation
   */
  public Euler negate() {
    return new Euler(-yaw, -pitch, -roll);
  }

  /**
   * Normalizes each component to the range [-π, π] (or [-π/2, π/2] for pitch).
   *
   * <p>This brings large angles back to their canonical representation.
   *
   * @return normalized Euler angles
   */
  public Euler normalize() {
    float nYaw = normalizeAngle(yaw);
    float nPitch = normalizePitch(pitch);
    float nRoll = normalizeAngle(roll);
    return new Euler(nYaw, nPitch, nRoll);
  }

  /**
   * Returns a copy with each component clamped to valid ranges.
   *
   * @return clamped Euler angles
   */
  public Euler clamp() {
    return new Euler(clampAngle(yaw), clampPitch(pitch), clampAngle(roll));
  }

  /**
   * Converts to a {@link Quaternion} using Y-X-Z order.
   *
   * @return equivalent quaternion
   */
  public Quaternion toQuaternion() {
    return Quaternion.createFromEuler(yaw, pitch, roll);
  }

  /**
   * Converts to a {@link Vector3} using Y-X-Z order.
   *
   * @return equivalent quaternion
   */
  public Vector3 toVector3() {
    return new Vector3(yaw, pitch, roll);
  }

  /**
   * Returns the Euler angles as a float array {yaw, pitch, roll}.
   *
   * @return component array
   */
  public float[] toArray() {
    return new float[] {yaw, pitch, roll};
  }
}