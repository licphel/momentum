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

package net.fmhi.codec.tag;

import net.fmhi.codec.streaming.Buf;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * An ordered, heterogeneous list of NBT values.
 *
 * <p>Unlike standard Minecraft NBT (where every list element shares the same type),
 * this list stores a type tag per element. This enables lossless round-tripping of JSON arrays that mix numbers,
 * strings, booleans, and nested structures.
 *
 * <p>Values can be primitives ({@code byte}, {@code short}, {@code int}, {@code long},
 * {@code float}, {@code double}, {@code boolean}, {@link String}, {@code byte[]}), or nested {@link CompoundTag} /
 * {@link ListTag}.
 * 
 * <p>This is not designed for high performance circumstances. You may use {@link CompoundTag#putBytes(String, byte[])}
 *
 * @see CompoundTag
 * @see TagMark
 */
public final class ListTag implements Iterable<@Nullable Object> {
  private final List<@Nullable Object> list = new ArrayList<>();

  /**
   * Creates an empty NBT list.
   */
  public ListTag() {
  }

  /**
   * Reads an NBT list from a binary buffer.
   *
   * <p>The wire format is:
   * <ol>
   *   <li>A 4-byte int element count</li>
   *   <li>For each element: a 1-byte {@link TagMark} tag followed by the encoded value</li>
   * </ol>
   *
   * @param buffer the source buffer positioned at the start of the list data
   * @return a new list containing the deserialized elements
   * @throws IndexOutOfBoundsException if {@code buffer} has insufficient readable bytes
   */
  public static ListTag deserialize(Buf buffer) {
    ListTag list = new ListTag();

    int size = buffer.readInt();
    for (int i = 0; i < size; i++) {
      TagMark elementType = TagMark.fromID(buffer.read());
      Object value = CompoundTag.readValue(buffer, elementType);
      list.add(value);
    }

    return list;
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
   * @param <T>   the expected type
   * @return the element, or {@code null} if absent
   */
  @SuppressWarnings("unchecked")
  public <T> @Nullable T get(int index) {
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
   * @param <T>      the expected type
   * @return the element, or {@code fallback}
   */
  @SuppressWarnings("unchecked")
  public <T> T get(int index, T fallback) {
    if (index < 0 || index >= list.size()) {
      return fallback;
    }
    Object value = list.get(index);
    return value != null ? (T) value : fallback;
  }

  /**
   * Returns the element at the given index wrapped in an {@link Optional}.
   *
   * @param index the element index
   * @param <T>   the expected type
   * @return an {@code Optional} containing the element, or empty if out of bounds or null
   */
  @SuppressWarnings("unchecked")
  public <T> Optional<T> tryGet(int index) {
    if (index < 0 || index >= list.size()) {
      return Optional.empty();
    }
    return Optional.ofNullable((T) list.get(index));
  }

  /**
   * Returns the element at the given index as a {@code byte}, with fallback.
   *
   * <p>Accepts {@link Number} (via {@link Number#byteValue()}) and {@link Boolean}
   * ({@code true} → 1, {@code false} → 0).
   *
   * @param index    the element index
   * @param fallback the value to return if out of bounds or type mismatch
   * @return the byte value, or {@code fallback}
   */
  public byte getByte(int index, byte fallback) {
    Object v = get(index);
    if (v instanceof Number) {
      return ((Number) v).byteValue();
    }
    if (v instanceof Boolean) {
      return (byte) ((boolean) v ? 1 : 0);
    }
    return fallback;
  }

  /**
   * Returns the element as a {@code short}, with fallback.
   *
   * @param index    the element index
   * @param fallback the value to return if out of bounds or type mismatch
   * @return the short value, or {@code fallback}
   */
  public short getShort(int index, short fallback) {
    Object v = get(index);
    if (v instanceof Number) {
      return ((Number) v).shortValue();
    }
    return fallback;
  }

  /**
   * Returns the element as an {@code int}, with fallback.
   *
   * @param index    the element index
   * @param fallback the value to return if out of bounds or type mismatch
   * @return the int value, or {@code fallback}
   */
  public int getInt(int index, int fallback) {
    Object v = get(index);
    if (v instanceof Number) {
      return ((Number) v).intValue();
    }
    return fallback;
  }

  /**
   * Returns the element as a {@code long}, with fallback.
   *
   * @param index    the element index
   * @param fallback the value to return if out of bounds or type mismatch
   * @return the long value, or {@code fallback}
   */
  public long getLong(int index, long fallback) {
    Object v = get(index);
    if (v instanceof Number) {
      return ((Number) v).longValue();
    }
    return fallback;
  }

  /**
   * Returns the element as a {@code float}, with fallback.
   *
   * @param index    the element index
   * @param fallback the value to return if out of bounds or type mismatch
   * @return the float value, or {@code fallback}
   */
  public float getFloat(int index, float fallback) {
    Object v = get(index);
    if (v instanceof Number) {
      return ((Number) v).floatValue();
    }
    return fallback;
  }

  /**
   * Returns the element as a {@code double}, with fallback.
   *
   * @param index    the element index
   * @param fallback the value to return if out of bounds or type mismatch
   * @return the double value, or {@code fallback}
   */
  public double getDouble(int index, double fallback) {
    Object v = get(index);
    if (v instanceof Number) {
      return ((Number) v).doubleValue();
    }
    return fallback;
  }

  /**
   * Returns the element as a {@code boolean}, with fallback.
   *
   * <p>Accepts {@link Boolean} directly and {@link Number} (non-zero → {@code true}).
   *
   * @param index    the element index
   * @param fallback the value to return if out of bounds or type mismatch
   * @return the boolean value, or {@code fallback}
   */
  public boolean getBoolean(int index, boolean fallback) {
    Object v = get(index);
    if (v instanceof Boolean) {
      return (boolean) v;
    }
    if (v instanceof Number) {
      return ((Number) v).intValue() != 0;
    }
    return fallback;
  }

  /**
   * Returns the element as a {@link String}, with fallback.
   *
   * @param index    the element index
   * @param fallback the value to return if out of bounds or type mismatch
   * @return the string value, or {@code fallback}
   */
  public String getString(int index, String fallback) {
    Object v = get(index);
    return v instanceof String ? (String) v : fallback;
  }

  /**
   * Returns the element as a byte array.
   *
   * @param index the element index
   * @return the byte array, or {@code null} if out of bounds or type mismatch
   */
  public byte @Nullable [] getBytes(int index) {
    Object v = get(index);
    return v instanceof byte[] ? (byte[]) v : null;
  }

  /**
   * Returns the element as an {@link CompoundTag}.
   *
   * @param index the element index
   * @return the nested compound, or {@code null} if out of bounds or type mismatch
   */
  public @Nullable CompoundTag getCompound(int index) {
    Object v = get(index);
    return v instanceof CompoundTag ? (CompoundTag) v : null;
  }

  /**
   * Returns the element as an {@code ListTag}.
   *
   * @param index the element index
   * @return the nested list, or {@code null} if out of bounds or type mismatch
   */
  public @Nullable ListTag getList(int index) {
    Object v = get(index);
    return v instanceof ListTag ? (ListTag) v : null;
  }

  /**
   * Appends a value to the end of this list.
   *
   * @param value the value to add, may be {@code null}
   * @throws IllegalArgumentException if the value's type is not a valid NBT type
   */
  public void add(@Nullable Object value) {
    TagMark.validate(value);
    list.add(value);
  }

  /**
   * Appends a {@code byte} value.
   *
   * @param value the value to add
   */
  public void addByte(byte value) {
    list.add(value);
  }

  /**
   * Appends a {@code short} value.
   *
   * @param value the value to add
   */
  public void addShort(short value) {
    list.add(value);
  }

  /**
   * Appends an {@code int} value.
   *
   * @param value the value to add
   */
  public void addInt(int value) {
    list.add(value);
  }

  /**
   * Appends a {@code long} value.
   *
   * @param value the value to add
   */
  public void addLong(long value) {
    list.add(value);
  }

  /**
   * Appends a {@code float} value.
   *
   * @param value the value to add
   */
  public void addFloat(float value) {
    list.add(value);
  }

  /**
   * Appends a {@code double} value.
   *
   * @param value the value to add
   */
  public void addDouble(double value) {
    list.add(value);
  }

  /**
   * Appends a {@code boolean} value.
   *
   * @param value the value to add
   */
  public void addBoolean(boolean value) {
    list.add(value);
  }

  /**
   * Appends a {@link String} value.
   *
   * @param value the value to add
   */
  public void addString(String value) {
    list.add(value);
  }

  /**
   * Appends a copy of the given byte array.
   *
   * @param value the byte array to copy and add
   */
  public void addBytes(byte[] value) {
    list.add(value.clone());
  }

  /**
   * Appends an {@link CompoundTag}.
   *
   * @param value the compound to add
   */
  public void addCompound(CompoundTag value) {
    list.add(value);
  }

  /**
   * Appends an {@code ListTag}.
   *
   * @param value the list to add
   */
  public void addList(ListTag value) {
    list.add(value);
  }

  /**
   * Inserts a value at the given index, shifting subsequent elements right.
   *
   * @param index the insertion index
   * @param value the value to insert, may be {@code null}
   * @throws IndexOutOfBoundsException if {@code index} is out of range
   * @throws IllegalArgumentException  if the value's type is not a valid NBT type
   */
  public void insert(int index, @Nullable Object value) {
    TagMark.validate(value);
    list.add(index, value);
  }

  /**
   * Replaces the element at the given index.
   *
   * @param index the index to replace
   * @param value the new value, may be {@code null}
   * @throws IndexOutOfBoundsException if {@code index} is out of range
   * @throws IllegalArgumentException  if the value's type is not a valid NBT type
   */
  public void set(int index, @Nullable Object value) {
    TagMark.validate(value);
    list.set(index, value);
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
   * Removes the first occurrence of the given value from this list.
   *
   * @param value the value to remove
   * @return {@code true} if an element was removed
   */
  public boolean remove(@Nullable Object value) {
    return list.remove(value);
  }

  /**
   * Creates a deep copy of this list.
   *
   * <p>Nested {@link CompoundTag} and {@code ListTag} elements are recursively
   * copied. {@code byte[]} elements are cloned. All other values are shared by reference (they are immutable primitives
   * or strings).
   *
   * @return a new list with independent copies of all nested structures
   */
  public ListTag copy() {
    ListTag copy = new ListTag();

    for (Object value : list) {
      switch (value) {
        case CompoundTag entries -> copy.addCompound(entries.copy());
        case ListTag objects -> copy.addList(objects.copy());
        case byte[] bytes -> copy.addBytes(bytes);
        case null, default -> copy.add(value);
      }
    }
    return copy;
  }

  /**
   * Returns an unmodifiable view of the underlying list.
   *
   * <p>Changes to this list are reflected in the returned view.
   *
   * @return an unmodifiable list view
   */
  public List<@Nullable Object> listed() {
    return Collections.unmodifiableList(list);
  }

  @Override
  public Iterator<@Nullable Object> iterator() {
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
    if (!(obj instanceof ListTag other)) {
      return false;
    }
    return list.equals(other.list);
  }

  @Override
  public String toString() {
    return list.toString();
  }

  /**
   * Serializes this list to a binary buffer.
   *
   * <p>Wire format:
   * <ol>
   *   <li>A 4-byte int element count (buffer byte order)</li>
   *   <li>For each element: a 1-byte type tag ({@link TagMark#id()})
   *       followed by the value encoded according to that type</li>
   * </ol>
   *
   * <p>Each element carries its own type tag because this list supports
   * heterogeneous element types — a departure from standard Minecraft NBT
   * that enables faithful JSON round-tripping.
   *
   * @param buffer the destination buffer
   */
  public void serialize(Buf buffer) {
    if (isEmpty()) {
      buffer.writeInt(0);
      return;
    }
    buffer.writeInt(list.size());

    for (Object value : list) {
      TagMark elementType = TagMark.fromValue(value);
      buffer.write(elementType.id());
      CompoundTag.writeValue(buffer, value);
    }
  }
}
