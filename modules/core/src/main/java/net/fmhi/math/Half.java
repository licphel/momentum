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
 * IEEE 754 float16 (half-precision) utility. Cast float to half-precision.
 *
 * @see Color#pack()
 */
public final class Half {
  private Half() {
  }

  /**
   * Quickly converts a 32-bit float to a 16-bit half-precision float (IEEE 754 half). No special handling for
   * NaN/Infinity - simple truncation for graphics use.
   *
   * @param f the float value to convert
   * @return the half-precision bit pattern as an unsigned short
   */
  public static short quickHalf(float f) {
    int bits = Float.floatToRawIntBits(f);
    int sign = (bits >>> 31) & 0x1;
    int exp = Math.clamp(((bits >>> 23) & 0xFF) - 127 + 15, 0, 31);
    int mantissa = (bits & 0x7FFFFF) >>> 13;

    return (short) ((sign << 15) | (exp << 10) | mantissa);
  }

  /**
   * Converts a 32-bit float to a 16-bit half-precision float (IEEE 754 half).
   *
   * @param f the float value to convert
   * @return the half-precision bit pattern as an unsigned short
   */
  public static short half(float f) {
    int bits = Float.floatToRawIntBits(f);
    int sign = (bits >>> 31) & 0x1;
    int exp = (bits >>> 23) & 0xFF;
    int mantissa = bits & 0x7FFFFF;

    int hExp;
    int hMan;
    if (exp == 0xFF) {
      hExp = 0x1F;
      hMan = (mantissa != 0) ? 1 : 0;
    } else if (exp == 0) {
      hExp = 0;
      hMan = 0;
    } else {
      int newExp = exp - 127 + 15;
      if (newExp >= 31) {
        hExp = 0x1F;
        hMan = 0;
      } else if (newExp <= 0) {
        if (newExp < -10) {
          hExp = 0;
          hMan = 0;
        } else {
          mantissa |= 0x800000;
          int shift = 14 - newExp;
          hMan = mantissa >>> shift;
          hExp = 0;
        }
      } else {
        hExp = newExp;
        hMan = mantissa >>> 13;
      }
    }

    return (short) ((sign << 15) | (hExp << 10) | hMan);
  }
}
