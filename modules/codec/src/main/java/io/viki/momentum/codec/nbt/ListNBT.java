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

import io.viki.momentum.codec.Codec;
import io.viki.momentum.codec.nbt.primitives.*;
import io.viki.momentum.codec.streaming.BinaryBuffer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * An ordered, heterogeneous list of NBT values.
 *
 * <p>Unlike standard Minecraft NBT (where every list element shares the same type),
 * this list stores a type tag per element. This enables lossless round-tripping of JSON arrays that mix numbers,
 * strings, booleans, and nested structures. Each element is a type-safe {@link NBT} instance.
 *
 * <p>Values can be primitives ({@code byte}, {@code short}, {@code int}, {@code long},
 * {@code float}, {@code double}, {@code boolean}, {@link String}, {@code byte[]}), or nested {@link CompoundNBT} /
 * {@link ListNBT}.
 *
 * <p>This class is <strong>not</strong> thread-safe.
 *
 * @see CompoundNBT
 * @see DataType
 */
public final class ListNBT implements NBT, Iterable<NBT> {
  /**
   * Codec for list values: the NBT tree form is the tag itself, and the binary
   * payload is a length-prefixed sequence of typed elements.
   */
  public static final Codec<ListNBT> CODEC = new Codec<>() {
    @Override
    public NBT serialize(ListNBT value) {
      return value;
    }

    @Override
    public ListNBT deserialize(NBT nbt) {
      return (ListNBT) nbt;
    }

    @Override
    public void serialize(ListNBT value, BinaryBuffer buffer) {
      buffer.writeInt(value.size());
      for (NBT child : value) {
        buffer.write(child.dataType().id());
        buffer.writeNBT(child);
      }
    }

    @Override
    public ListNBT deserialize(BinaryBuffer buffer) {
      ListNBT list = new ListNBT();
      int size = buffer.readInt();
      for (int i = 0; i < size; i++) {
        DataType elementType = DataType.fromID(buffer.read());
        list.add(elementType.codec().deserialize(buffer));
      }
      return list;
    }
  };

  private final List<NBT> list = new ArrayList<>();

  /**
   * Creates an empty NBT list.
   */
  public ListNBT() {
  }

  /**
   * Returns the number of elements in this list.
   *
   * @return the element count
   */
  public int size() {
    return list.size();
  }

  /**
   * Returns whether this list contains no elements.
   *
   * @return {@code true} if empty
   */
  public boolean isEmpty() {
    return list.isEmpty();
  }

  /**
   * Removes all elements from this list.
   */
  public void clear() {
    list.clear();
  }

  /**
   * Returns the element at the given index with an unchecked cast.
   *
   * <p>Returns {@code null} if the index is out of bounds, or if the element
   * itself is {@code null}.
   *
   * @param index the element index
   * @param <T>   the expected Tag subtype
   * @return the tag, or {@code null} if absent
   */
  @SuppressWarnings("unchecked")
  public <T extends NBT> @Nullable T get(int index) {
    if (index < 0 || index >= list.size()) {
      return null;
    }
    return (T) list.get(index);
  }

  /**
   * Returns the element at the given index, or {@code fallback} if out of bounds.
   *
   * @param index    the element index
   * @param fallback the value to return if the index is invalid
   * @param <T>      the expected Tag subtype
   * @return the tag, or {@code fallback} if absent
   */
  public <T extends NBT> T get(int index, T fallback) {
    T value = get(index);
    return value != null ? value : fallback;
  }

  /**
   * Returns the element as a {@code byte} via {@link NumericNBT},
   * converting across numeric types. {@link BooleanNBT} maps {@code true} → 1, {@code false} → 0.
   *
   * @param index    the element index
   * @param fallback the value to return if out of bounds or not numeric
   * @return the byte value, or {@code fallback}
   */
  public byte getByte(int index, byte fallback) {
    NBT v = get(index);
    if (v instanceof NumericNBT n) {
      return n.asByte();
    }
    if (v instanceof BooleanNBT b) {
      return (byte) (b.get() ? 1 : 0);
    }
    return fallback;
  }

  /**
   * Returns the element as a {@code short} via {@link NumericNBT}.
   *
   * @param index    the element index
   * @param fallback the value to return if out of bounds or not numeric
   * @return the short value, or {@code fallback}
   */
  public short getShort(int index, short fallback) {
    NBT v = get(index);
    if (v instanceof NumericNBT n) {
      return n.asShort();
    }
    return fallback;
  }

  /**
   * Returns the element as an {@code int} via {@link NumericNBT}.
   *
   * @param index    the element index
   * @param fallback the value to return if out of bounds or not numeric
   * @return the int value, or {@code fallback}
   */
  public int getInt(int index, int fallback) {
    NBT v = get(index);
    if (v instanceof NumericNBT n) {
      return n.asInt();
    }
    return fallback;
  }

  /**
   * Returns the element as a {@code long} via {@link NumericNBT}.
   *
   * @param index    the element index
   * @param fallback the value to return if out of bounds or not numeric
   * @return the long value, or {@code fallback}
   */
  public long getLong(int index, long fallback) {
    NBT v = get(index);
    if (v instanceof NumericNBT n) {
      return n.asLong();
    }
    return fallback;
  }

  /**
   * Returns the element as a {@code float} via {@link NumericNBT}.
   *
   * @param index    the element index
   * @param fallback the value to return if out of bounds or not numeric
   * @return the float value, or {@code fallback}
   */
  public float getFloat(int index, float fallback) {
    NBT v = get(index);
    if (v instanceof NumericNBT n) {
      return n.asFloat();
    }
    return fallback;
  }

  /**
   * Returns the element as a {@code double} via {@link NumericNBT}.
   *
   * @param index    the element index
   * @param fallback the value to return if out of bounds or not numeric
   * @return the double value, or {@code fallback}
   */
  public double getDouble(int index, double fallback) {
    NBT v = get(index);
    if (v instanceof NumericNBT n) {
      return n.asDouble();
    }
    return fallback;
  }

  /**
   * Returns the element as a {@code boolean}. {@link BooleanNBT} is returned
   * directly; numeric tags map non-zero to {@code true}.
   *
   * @param index    the element index
   * @param fallback the value to return if out of bounds or type mismatch
   * @return the boolean value, or {@code fallback}
   */
  public boolean getBoolean(int index, boolean fallback) {
    NBT v = get(index);
    if (v instanceof BooleanNBT b) {
      return b.get();
    }
    if (v instanceof NumericNBT n) {
      return n.asLong() != 0;
    }
    return fallback;
  }

  /**
   * Returns the element as a {@link String}.
   *
   * @param index    the element index
   * @param fallback the value to return if out of bounds or type mismatch
   * @return the string value, or {@code fallback}
   */
  public String getString(int index, String fallback) {
    NBT v = get(index);
    return v instanceof StringNBT s ? s.get() : fallback;
  }

  /**
   * Returns the element as a byte array.
   *
   * @param index the element index
   * @return the byte array, or {@code null} if out of bounds or type mismatch
   */
  public byte @Nullable [] getBytes(int index) {
    NBT v = get(index);
    return v instanceof ByteArrayNBT b ? b.get() : null;
  }

  /**
   * Returns the element as an {@link CompoundNBT}.
   *
   * @param index the element index
   * @return the nested compound, or {@code null} if out of bounds or type mismatch
   */
  public @Nullable CompoundNBT getCompound(int index) {
    NBT v = get(index);
    return v instanceof CompoundNBT c ? c : null;
  }

  /**
   * Returns the element as a {@link ListNBT}.
   *
   * @param index the element index
   * @return the nested list, or {@code null} if out of bounds or type mismatch
   */
  public @Nullable ListNBT getList(int index) {
    NBT v = get(index);
    return v instanceof ListNBT l ? l : null;
  }

  /**
   * Appends a tag value to the end of this list.
   *
   * @param value the tag value to add, may be {@code null} (stored as {@link NullNBT})
   */
  public void add(@Nullable NBT value) {
    list.add(value != null ? value : NullNBT.INSTANCE);
  }

  /**
   * Appends a {@code byte} value, wrapped in a {@link ByteNBT}.
   *
   * @param value the byte value to add
   */
  public void addByte(byte value) {
    list.add(new ByteNBT(value));
  }

  /**
   * Appends a {@code short} value, wrapped in a {@link ShortNBT}.
   *
   * @param value the short value to add
   */
  public void addShort(short value) {
    list.add(new ShortNBT(value));
  }

  /**
   * Appends an {@code int} value, wrapped in an {@link IntNBT}.
   *
   * @param value the int value to add
   */
  public void addInt(int value) {
    list.add(new IntNBT(value));
  }

  /**
   * Appends a {@code long} value, wrapped in a {@link LongNBT}.
   *
   * @param value the long value to add
   */
  public void addLong(long value) {
    list.add(new LongNBT(value));
  }

  /**
   * Appends a {@code float} value, wrapped in a {@link FloatNBT}.
   *
   * @param value the float value to add
   */
  public void addFloat(float value) {
    list.add(new FloatNBT(value));
  }

  /**
   * Appends a {@code double} value, wrapped in a {@link DoubleNBT}.
   *
   * @param value the double value to add
   */
  public void addDouble(double value) {
    list.add(new DoubleNBT(value));
  }

  /**
   * Appends a {@code boolean} value, wrapped in a {@link BooleanNBT}.
   *
   * @param value the boolean value to add
   */
  public void addBoolean(boolean value) {
    list.add(new BooleanNBT(value));
  }

  /**
   * Appends a {@link String} value, wrapped in a {@link StringNBT}.
   *
   * @param value the string value to add
   */
  public void addString(String value) {
    list.add(new StringNBT(value));
  }

  /**
   * Appends a copy of the given byte array, wrapped in a {@link ByteArrayNBT}.
   *
   * @param value the byte array to copy and add
   */
  public void addBytes(byte[] value) {
    list.add(new ByteArrayNBT(value));
  }

  /**
   * Appends a {@link CompoundNBT} by reference.
   *
   * @param value the compound to add
   */
  public void addCompound(CompoundNBT value) {
    list.add(value);
  }

  /**
   * Appends a {@link ListNBT} by reference.
   *
   * @param value the list to add
   */
  public void addList(ListNBT value) {
    list.add(value);
  }

  /**
   * Inserts a tag value at the given index, shifting subsequent elements right.
   *
   * @param index the insertion index
   * @param value the value to insert, may be {@code null} (stored as {@link NullNBT})
   * @throws IndexOutOfBoundsException if {@code index} is out of range
   */
  public void insert(int index, @Nullable NBT value) {
    list.add(index, value != null ? value : NullNBT.INSTANCE);
  }

  /**
   * Replaces the element at the given index.
   *
   * @param index the index to replace
   * @param value the new value, may be {@code null} (stored as {@link NullNBT})
   * @throws IndexOutOfBoundsException if {@code index} is out of range
   */
  public void set(int index, @Nullable NBT value) {
    list.set(index, value != null ? value : NullNBT.INSTANCE);
  }

  /**
   * Removes the element at the given index.
   *
   * @param index the index to remove
   * @throws IndexOutOfBoundsException if {@code index} is out of range
   */
  public void removeAt(int index) {
    list.remove(index);
  }

  /**
   * Removes the first occurrence of the given tag value from this list.
   *
   * @param value the value to remove
   * @return {@code true} if an element was removed
   */
  public boolean remove(@Nullable NBT value) {
    return list.remove(value);
  }

  /**
   * Returns an unmodifiable view of the underlying list.
   *
   * <p>Changes to this list are reflected in the returned view.
   *
   * @return an unmodifiable list view
   */
  public List<NBT> listed() {
    return Collections.unmodifiableList(list);
  }

  @Override
  public Iterator<NBT> iterator() {
    return new ArrayList<>(list).iterator();
  }

  @Override
  public int hashCode() {
    return list.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ListNBT other)) {
      return false;
    }
    return list.equals(other.list);
  }

  @Override
  public String toString() {
    return list.toString();
  }

  @Override
  public DataType dataType() {
    return DataType.LIST;
  }

  @Override
  public Object asObject() {
    return this;
  }

  /**
   * Creates a deep copy of this list.
   *
   * <p>Nested {@link CompoundNBT} and {@link ListNBT} elements are recursively
   * copied via {@link NBT#copy()}.
   *
   * @return a new list with independent copies of all nested structures
   */
  public ListNBT copy() {
    ListNBT copy = new ListNBT();

    for (NBT value : list) {
      copy.add(value.copy());
    }
    return copy;
  }
}