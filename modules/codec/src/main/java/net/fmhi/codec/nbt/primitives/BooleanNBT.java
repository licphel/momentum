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

package net.fmhi.codec.nbt.primitives;

import net.fmhi.codec.Codec;
import net.fmhi.codec.nbt.CompoundNBT;
import net.fmhi.codec.nbt.DataType;
import net.fmhi.codec.nbt.ListNBT;
import net.fmhi.codec.nbt.NBT;
import net.fmhi.codec.streaming.CursorBuffer;

/**
 * An NBT boolean tag wrapper — a type-safe alternative to storing a raw {@code boolean}
 * as an {@code Object} in a {@link CompoundNBT} map or {@link ListNBT} list.
 */
public final class BooleanNBT implements NBT {
  public static final Codec<BooleanNBT> CODEC = new Codec<>() {
    @Override
    public NBT toNbt(BooleanNBT value) {
      return value;
    }

    @Override
    public BooleanNBT fromNbt(NBT nbt) {
      return (BooleanNBT) nbt;
    }

    @Override
    public void serialize(BooleanNBT value, CursorBuffer buffer) {
      buffer.writeBoolean(value.get());
    }

    @Override
    public BooleanNBT deserialize(CursorBuffer buffer) {
      return new BooleanNBT(buffer.read() != 0);
    }
  };

  private final boolean value;

  /**
   * Wraps the given boolean value.
   *
   * @param value the primitive boolean
   */
  public BooleanNBT(boolean value) {
    this.value = value;
  }

  /**
   * Returns the wrapped boolean value.
   *
   * @return the primitive boolean
   */
  public boolean get() {
    return value;
  }

  @Override
  public DataType dataType() {
    return DataType.BOOLEAN;
  }

  @Override
  public Object asObject() {
    return value;
  }

  @Override
  public NBT copy() {
    return new BooleanNBT(value);
  }

  @Override
  public int hashCode() {
    return Boolean.hashCode(value);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof BooleanNBT other)) {
      return false;
    }
    return value == other.value;
  }

  @Override
  public String toString() {
    return Boolean.toString(value);
  }
}