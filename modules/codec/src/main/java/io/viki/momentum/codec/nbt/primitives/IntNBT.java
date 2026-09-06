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
 * An NBT int tag wrapper — a type-safe alternative to storing a raw {@code int}
 * as an {@code Object} in a {@link CompoundNBT} map or {@link ListNBT} list.
 */
public final class IntNBT implements NumericNBT {
  public static final Codec<IntNBT> CODEC = new Codec<>() {
    @Override
    public NBT serialize(IntNBT value) {
      return value;
    }

    @Override
    public IntNBT deserialize(NBT nbt) {
      return (IntNBT) nbt;
    }

    @Override
    public void serialize(IntNBT value, CursorBuffer buffer) {
      buffer.writeInt(value.get());
    }

    @Override
    public IntNBT deserialize(CursorBuffer buffer) {
      return new IntNBT(buffer.readInt());
    }
  };

  private final int value;

  /**
   * Wraps the given int value.
   *
   * @param value the primitive int
   */
  public IntNBT(int value) {
    this.value = value;
  }

  /**
   * Returns the wrapped int value.
   *
   * @return the value
   */
  public int get() {
    return value;
  }

  @Override
  public DataType dataType() {
    return DataType.INT;
  }

  @Override
  public Object asObject() {
    return value;
  }

  @Override
  public NBT copy() {
    return new IntNBT(value);
  }

  @Override
  public long asLong() {
    return value;
  }

  @Override
  public double asDouble() {
    return value;
  }

  @Override
  public int hashCode() {
    return Integer.hashCode(value);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof IntNBT other)) {
      return false;
    }
    return value == other.value;
  }

  @Override
  public String toString() {
    return Integer.toString(value);
  }
}