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

package io.viki.momentum.math.util;

/**
 * Provides table-based fast trigonometric calculations.
 *
 * <p>Uses a 4096-entry sine lookup table with linear interpolation. Calls to {@link #sincos(float)} reuse a
 * thread-local result array, so each call on the same thread overwrites the previous result.
 * Cosine is derived from sine via a quarter-period offset. Accuracy is approximately 3–4 decimal places; use
 * {@link Math#sin}/{@link Math#cos} when full precision is required.
 */
public final class FastTrigonometric {
  /** Number of samples in the periodic sine lookup table. */
  private static final int TABLE_SIZE = 4096;
  /** One full turn in radians, represented as a float. */
  private static final float TWO_PI = (float) (Math.PI * 2.0);
  /** Reciprocal of {@link #TWO_PI}, used to map angles to table positions. */
  private static final float INV_TWO_PI = 1.0F / TWO_PI;
  /** Precomputed sine values sampled uniformly over one full turn. */
  private static final float[] TABLE = new float[TABLE_SIZE];
  /** Per-thread result storage used by {@link #sincos(float)}. */
  private static final ThreadLocal<float[]> VARS = ThreadLocal.withInitial(() -> new float[2]);

  static {
    for (int i = 0; i < TABLE_SIZE; i++) {
      TABLE[i] = (float) Math.sin(i * TWO_PI / TABLE_SIZE);
    }
  }

  private FastTrigonometric() {
  }

  /**
   * Looks up the sine and cosine of an angle using the pre-calculated table.
   *
   * @param radians the angle in radians (any value, not clamped)
   * @return a reusable two-element thread-local array containing {@code [sin, cos]}
   */
  public static float[] sincos(float radians) {
    float index = mod(radians) * INV_TWO_PI * TABLE_SIZE;
    int i = (int) index & (TABLE_SIZE - 1);
    float frac = index - (int) index;
    int next = (i + 1) & (TABLE_SIZE - 1);
    int cosI = (i + TABLE_SIZE / 4) & (TABLE_SIZE - 1);
    int cosNext = (cosI + 1) & (TABLE_SIZE - 1);

    float[] out = VARS.get();
    out[0] = lerp(TABLE[i], TABLE[next], frac);
    out[1] = lerp(TABLE[cosI], TABLE[cosNext], frac);
    return out;
  }

  /**
   * Returns the sine of an angle using the pre-calculated table.
   *
   * @param radians the angle in radians
   * @return approximate sine value
   */
  public static float sin(float radians) {
    float index = mod(radians) * INV_TWO_PI * TABLE_SIZE;
    int i = (int) index & (TABLE_SIZE - 1);
    int next = (i + 1) & (TABLE_SIZE - 1);
    return lerp(TABLE[i], TABLE[next], index - (int) index);
  }

  /**
   * Returns the cosine of an angle using the pre-calculated table.
   *
   * @param radians the angle in radians
   * @return approximate cosine value
   */
  public static float cos(float radians) {
    float index = mod(radians) * INV_TWO_PI * TABLE_SIZE;
    int i = ((int) index + TABLE_SIZE / 4) & (TABLE_SIZE - 1);
    int next = (i + 1) & (TABLE_SIZE - 1);
    return lerp(TABLE[i], TABLE[next], index - (int) index);
  }

  /** Wraps an angle into one full turn. */
  private static float mod(float rad) {
    rad %= TWO_PI;
    return rad < 0 ? rad + TWO_PI : rad;
  }

  /** Linearly interpolates between two table samples. */
  private static float lerp(float a, float b, float t) {
    return a + (b - a) * t;
  }
}
