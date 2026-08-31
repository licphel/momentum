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
 * An NBT byte tag wrapper — a type-safe alternative to storing a raw {@code byte}
 * as an {@code Object} in a {@link CompoundNBT} map or {@link ListNBT} list.
 */
public final class ByteNBT implements NumericNBT {
  public static final Codec<ByteNBT> CODEC = new Codec<>() {
    @Override
    public NBT serialize(ByteNBT value) {
      return value;
    }

    @Override
    public ByteNBT deserialize(NBT nbt) {
      return (ByteNBT) nbt;
    }

    @Override
    public void serialize(ByteNBT value, CursorBuffer buffer) {
      buffer.write(value.get());
    }

    @Override
    public ByteNBT deserialize(CursorBuffer buffer) {
      return new ByteNBT(buffer.read());
    }
  };

  private final byte value;

  /**
   * Wraps the given byte value.
   *
   * @param value the primitive byte
   */
  public ByteNBT(byte value) {
    this.value = value;
  }

  /**
   * Returns the wrapped byte value.
   *
   * @return the value
   */
  public byte get() {
    return value;
  }

  @Override
  public DataType dataType() {
    return DataType.BYTE;
  }

  @Override
  public Object asObject() {
    return value;
  }

  @Override
  public NBT copy() {
    return new ByteNBT(value);
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
    return Byte.hashCode(value);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ByteNBT other)) {
      return false;
    }
    return value == other.value;
  }

  @Override
  public String toString() {
    return Byte.toString(value);
  }
}