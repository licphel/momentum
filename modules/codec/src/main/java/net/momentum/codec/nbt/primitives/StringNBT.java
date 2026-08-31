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

import java.util.Objects;

/**
 * An NBT string tag wrapper — a type-safe alternative to storing a raw {@code String}
 * as an {@code Object} in a {@link CompoundNBT} map or {@link ListNBT} list.
 */
public final class StringNBT implements NBT {
  public static final Codec<StringNBT> CODEC = new Codec<>() {
    @Override
    public NBT serialize(StringNBT value) {
      return value;
    }

    @Override
    public StringNBT deserialize(NBT nbt) {
      return (StringNBT) nbt;
    }

    @Override
    public void serialize(StringNBT value, CursorBuffer buffer) {
      buffer.writeUTF8(value.get());
    }

    @Override
    public StringNBT deserialize(CursorBuffer buffer) {
      return new StringNBT(buffer.readUTF8());
    }
  };

  private final String value;

  /**
   * Wraps the given string value.
   *
   * @param value the string value
   */
  public StringNBT(String value) {
    this.value = value;
  }

  /**
   * Returns the wrapped string value.
   *
   * @return the value
   */
  public String get() {
    return value;
  }

  @Override
  public DataType dataType() {
    return DataType.STRING;
  }

  @Override
  public Object asObject() {
    return value;
  }

  @Override
  public NBT copy() {
    return new StringNBT(value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof StringNBT other)) {
      return false;
    }
    return Objects.equals(value, other.value);
  }

  @Override
  public String toString() {
    return value;
  }
}