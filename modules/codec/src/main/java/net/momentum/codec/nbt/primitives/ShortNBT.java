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

package net.momentum.codec.nbt.primitives;

import net.momentum.codec.Codec;
import net.momentum.codec.nbt.*;
import net.momentum.codec.streaming.CursorBuffer;

/**
 * An NBT short tag wrapper — a type-safe alternative to storing a raw {@code short}
 * as an {@code Object} in a {@link CompoundNBT} map or {@link ListNBT} list.
 */
public final class ShortNBT implements NumericNBT {
  public static final Codec<ShortNBT> CODEC = new Codec<>() {
    @Override
    public NBT serialize(ShortNBT value) {
      return value;
    }

    @Override
    public ShortNBT deserialize(NBT nbt) {
      return (ShortNBT) nbt;
    }

    @Override
    public void serialize(ShortNBT value, CursorBuffer buffer) {
      buffer.writeShort(value.get());
    }

    @Override
    public ShortNBT deserialize(CursorBuffer buffer) {
      return new ShortNBT(buffer.readShort());
    }
  };

  private final short value;

  /**
   * Wraps the given short value.
   *
   * @param value the value
   */
  public ShortNBT(short value) {
    this.value = value;
  }

  /**
   * Returns the wrapped short value.
   *
   * @return the value
   */
  public short get() {
    return value;
  }

  @Override
  public DataType dataType() {
    return DataType.SHORT;
  }

  @Override
  public Object asObject() {
    return value;
  }

  @Override
  public NBT copy() {
    return new ShortNBT(value);
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
    return Short.hashCode(value);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ShortNBT other)) {
      return false;
    }
    return value == other.value;
  }

  @Override
  public String toString() {
    return Short.toString(value);
  }
}