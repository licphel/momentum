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

package io.viki.momentum.codec.nbt.primitives;

import io.viki.momentum.codec.Codec;
import io.viki.momentum.codec.nbt.*;
import io.viki.momentum.codec.nbt.*;
import io.viki.momentum.codec.nbt.*;
import io.viki.momentum.codec.streaming.CursorBuffer;

/**
 * An NBT float tag wrapper — a type-safe alternative to storing a raw {@code float}
 * as an {@code Object} in a {@link CompoundNBT} map or {@link ListNBT} list.
 */
public final class FloatNBT implements NumericNBT {
  public static final Codec<FloatNBT> CODEC = new Codec<>() {
    @Override
    public NBT serialize(FloatNBT value) {
      return value;
    }

    @Override
    public FloatNBT deserialize(NBT nbt) {
      return (FloatNBT) nbt;
    }

    @Override
    public void serialize(FloatNBT value, CursorBuffer buffer) {
      buffer.writeFloat(value.get());
    }

    @Override
    public FloatNBT deserialize(CursorBuffer buffer) {
      return new FloatNBT(buffer.readFloat());
    }
  };

  private final float value;

  /**
   * Wraps the given float value.
   *
   * @param value the primitive float
   */
  public FloatNBT(float value) {
    this.value = value;
  }

  /**
   * Returns the wrapped float value.
   *
   * @return the value
   */
  public float get() {
    return value;
  }

  @Override
  public DataType dataType() {
    return DataType.FLOAT;
  }

  @Override
  public Object asObject() {
    return value;
  }

  @Override
  public NBT copy() {
    return new FloatNBT(value);
  }

  @Override
  public long asLong() {
    return (long) value;
  }

  @Override
  public double asDouble() {
    return value;
  }

  @Override
  public int hashCode() {
    return Float.hashCode(value);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof FloatNBT other)) {
      return false;
    }
    return Float.floatToIntBits(value) == Float.floatToIntBits(other.value);
  }

  @Override
  public String toString() {
    return Float.toString(value);
  }
}