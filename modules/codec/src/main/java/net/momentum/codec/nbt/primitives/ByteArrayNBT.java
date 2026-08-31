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
import net.momentum.codec.nbt.CompoundNBT;
import net.momentum.codec.nbt.DataType;
import net.momentum.codec.nbt.ListNBT;
import net.momentum.codec.nbt.NBT;
import net.momentum.codec.streaming.CursorBuffer;

import java.util.Arrays;

/**
 * An NBT byte array tag wrapper — a type-safe alternative to storing a raw {@code byte[]}
 * as an {@code Object} in a {@link CompoundNBT} map or {@link ListNBT} list.
 * The array is cloned on construction to prevent external mutation.
 */
public final class ByteArrayNBT implements NBT {
  public static final Codec<ByteArrayNBT> CODEC = new Codec<>() {
    @Override
    public NBT serialize(ByteArrayNBT value) {
      return value;
    }

    @Override
    public ByteArrayNBT deserialize(NBT nbt) {
      return (ByteArrayNBT) nbt;
    }

    @Override
    public void serialize(ByteArrayNBT value, CursorBuffer buffer) {
      byte[] bytes = value.get();
      buffer.writeInt(bytes.length);
      buffer.writeBytes(bytes);
    }

    @Override
    public ByteArrayNBT deserialize(CursorBuffer buffer) {
      return new ByteArrayNBT(buffer.readBytes(buffer.readInt()));
    }
  };

  private final byte[] value;

  /**
   * Wraps the given byte array. A copy is made internally.
   *
   * @param value the binary value
   */
  public ByteArrayNBT(byte[] value) {
    this.value = Arrays.copyOf(value, value.length);
  }

  /**
   * Returns the wrapped byte array (copy). Modifying the returned array does not
   * affect the internal storage.
   *
   * @return the value
   */
  public byte[] get() {
    return value.clone();
  }

  /**
   * Returns the wrapped byte array (copy).
   *
   * @return the value
   */
  public byte[] getUnsafe() {
    return value;
  }

  @Override
  public DataType dataType() {
    return DataType.BYTE_ARRAY;
  }

  @Override
  public Object asObject() {
    return value.clone();
  }

  @Override
  public NBT copy() {
    return new ByteArrayNBT(value.clone());
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(value);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ByteArrayNBT other)) {
      return false;
    }
    return Arrays.equals(value, other.value);
  }

  @Override
  public String toString() {
    return Arrays.toString(value);
  }
}