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

package io.viki.momentum.codec.nbt;

import io.viki.momentum.codec.nbt.primitives.*;

/**
 * Interface for numeric NBT tags ({@link ByteNBT}, {@link ShortNBT}, {@link IntNBT},
 * {@link LongNBT}, {@link FloatNBT}, {@link DoubleNBT}).
 *
 * <p>Only {@link #asLong()} and {@link #asDouble()} are abstract; the remaining
 * conversions ({@link #asByte()}, {@link #asShort()}, {@link #asInt()},
 * {@link #asFloat()}) are provided as default implementations that narrow-cast
 * from the abstract pair. This lets containers like {@link CompoundNBT} and
 * {@link ListNBT} retrieve any numeric value without per-type {@code instanceof}
 * dispatch.
 *
 * <p>Note: narrowing conversions may lose precision or overflow silently;
 * callers are responsible for choosing the appropriate method.
 */
public interface NumericNBT extends NBT {
  /**
   * Returns this tag's value as a {@code long}. Integral tags convert exactly;
   * floating-point tags truncate toward zero.
   *
   * @return the long value
   */
  long asLong();

  /**
   * Returns this tag's value as a {@code double}. Integral tags widen exactly
   * (up to 2^53 precision); floating-point tags return their stored value.
   *
   * @return the double value
   */
  double asDouble();

  /**
   * Returns this tag's value as a {@code byte} (narrowing).
   *
   * @return the byte value
   */
  default byte asByte() {
    return (byte) asLong();
  }

  /**
   * Returns this tag's value as a {@code short} (narrowing).
   *
   * @return the short value
   */
  default short asShort() {
    return (short) asLong();
  }

  /**
   * Returns this tag's value as an {@code int} (narrowing).
   *
   * @return the int value
   */
  default int asInt() {
    return (int) asLong();
  }

  /**
   * Returns this tag's value as a {@code float} (narrowing).
   *
   * @return the float value
   */
  default float asFloat() {
    return (float) asDouble();
  }
}