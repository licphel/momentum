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
import java.util.Optional;
import java.util.function.Supplier;

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
   * Returns the tag at the index without checking its subtype.
   *
   * <p>The caller must ensure that the stored tag matches the expected subtype.
   * A stored {@link NullNBT} is returned as a tag and is not treated as absent.
   *
   * @param index the zero-based element index
   * @param <T> the expected tag subtype; no runtime subtype check is performed
   * @return the stored tag, or {@code null} for a out-of-bounds index
   */
  @SuppressWarnings("unchecked")
  public <T extends NBT> @Nullable T get(int index) {
    if (index < 0 || index >= list.size()) {
      return null;
    }
    return (T) list.get(index);
  }

  /**
   * Returns the numeric value at the index, preserving its numeric type.
   *
   * <p>Boolean tags are not numeric values.
   *
   * @param index the zero-based element index
   * @return the numeric value, or {@code null} for a out-of-bounds index or nonnumeric tag
   */
  public @Nullable Number getNumber(int index) {
    NBT value = get(index);
    return value instanceof NumericNBT ? (Number) value.asObject() : null;
  }

  /**
   * Returns the tag at the index without checking its subtype.
   *
   * <p>The caller must ensure that the stored tag matches the expected subtype.
   * A stored {@link NullNBT} is returned as a tag and is not treated as absent.
   *
   * @param index the zero-based element index
   * @param <T> the expected tag subtype; no runtime subtype check is performed
   * @param fallback the fallback value to return as-is when no compatible value is available
   * @return the stored tag, or {@code fallback} for a out-of-bounds index
   */
  public <T extends NBT> T get(int index, T fallback) {
    T value = get(index);
    return value != null ? value : fallback;
  }

  /**
   * Returns the value at the index as a {@code byte}.
   *
   * <p>Numeric tags use {@link Number#byteValue()} conversion, which may lose precision or narrow the value. Boolean tags map {@code true} to {@code 1} and {@code false} to {@code 0}.
   *
   * @param index the zero-based element index
   * @return an optional containing the converted value, or empty for a out-of-bounds index or incompatible tag
   */
  public Optional<Byte> getByte(int index) {
    Number value = getNumber(index);
    if (value == null && get(index) instanceof BooleanNBT bool) {
      return Optional.of((byte) (bool.get() ? 1 : 0));
    }
    return value == null ? Optional.empty() : Optional.of(value.byteValue());
  }

  /**
   * Returns the value at the index as a {@code short}.
   *
   * <p>Numeric tags use {@link Number#shortValue()} conversion, which may lose precision or narrow the value.
   *
   * @param index the zero-based element index
   * @return an optional containing the converted value, or empty for a out-of-bounds index or incompatible tag
   */
  public Optional<Short> getShort(int index) {
    Number value = getNumber(index);
    return value == null ? Optional.empty() : Optional.of(value.shortValue());
  }

  /**
   * Returns the value at the index as a {@code int}.
   *
   * <p>Numeric tags use {@link Number#intValue()} conversion, which may lose precision or narrow the value.
   *
   * @param index the zero-based element index
   * @return an optional containing the converted value, or empty for a out-of-bounds index or incompatible tag
   */
  public Optional<Integer> getInt(int index) {
    Number value = getNumber(index);
    return value == null ? Optional.empty() : Optional.of(value.intValue());
  }

  /**
   * Returns the value at the index as a {@code long}.
   *
   * <p>Numeric tags use {@link Number#longValue()} conversion, which may lose precision or narrow the value.
   *
   * @param index the zero-based element index
   * @return an optional containing the converted value, or empty for a out-of-bounds index or incompatible tag
   */
  public Optional<Long> getLong(int index) {
    Number value = getNumber(index);
    return value == null ? Optional.empty() : Optional.of(value.longValue());
  }

  /**
   * Returns the value at the index as a {@code float}.
   *
   * <p>Numeric tags use {@link Number#floatValue()} conversion, which may lose precision or narrow the value.
   *
   * @param index the zero-based element index
   * @return an optional containing the converted value, or empty for a out-of-bounds index or incompatible tag
   */
  public Optional<Float> getFloat(int index) {
    Number value = getNumber(index);
    return value == null ? Optional.empty() : Optional.of(value.floatValue());
  }

  /**
   * Returns the value at the index as a {@code double}.
   *
   * <p>Numeric tags use {@link Number#doubleValue()} conversion, which may lose precision or narrow the value.
   *
   * @param index the zero-based element index
   * @return an optional containing the converted value, or empty for a out-of-bounds index or incompatible tag
   */
  public Optional<Double> getDouble(int index) {
    Number value = getNumber(index);
    return value == null ? Optional.empty() : Optional.of(value.doubleValue());
  }

  /**
   * Returns the value at the index as a {@code boolean}.
   *
   * <p>Boolean tags are returned directly; numeric tags are true when their {@link Number#longValue()} is nonzero.
   *
   * @param index the zero-based element index
   * @return an optional containing the converted value, or empty for a out-of-bounds index or incompatible tag
   */
  public Optional<Boolean> getBoolean(int index) {
    NBT value = get(index);
    if (value instanceof BooleanNBT bool) return Optional.of(bool.get());
    Number number = getNumber(index);
    return number == null ? Optional.empty() : Optional.of(number.longValue() != 0);
  }

  /**
   * Returns the value at the index as a string.
   *
   * @param index the zero-based element index
   * @return an optional containing the stored value, or empty for a out-of-bounds index or incompatible tag
   */
  public Optional<String> getString(int index) {
    NBT value = get(index);
    return value instanceof StringNBT string ? Optional.of(string.get()) : Optional.empty();
  }

  /**
   * Returns the value at the index as a {@code byte}.
   *
   * <p>Numeric tags use {@link Number#byteValue()} conversion, which may lose precision or narrow the value. Boolean tags map {@code true} to {@code 1} and {@code false} to {@code 0}.
   *
   * @param index the zero-based element index
   * @param fallback the fallback value to return as-is when no compatible value is available
   * @return the converted value, or {@code fallback} for a out-of-bounds index or incompatible tag
   */
  public byte getByte(int index, byte fallback) {
    Number value = getNumber(index);
    if (value == null && get(index) instanceof BooleanNBT bool) {
      return (byte) (bool.get() ? 1 : 0);
    }
    return value == null ? fallback : value.byteValue();
  }

  /**
   * Returns the value at the index as a {@code short}.
   *
   * <p>Numeric tags use {@link Number#shortValue()} conversion, which may lose precision or narrow the value.
   *
   * @param index the zero-based element index
   * @param fallback the fallback value to return as-is when no compatible value is available
   * @return the converted value, or {@code fallback} for a out-of-bounds index or incompatible tag
   */
  public short getShort(int index, short fallback) {
    Number value = getNumber(index);
    return value == null ? fallback : value.shortValue();
  }

  /**
   * Returns the value at the index as a {@code int}.
   *
   * <p>Numeric tags use {@link Number#intValue()} conversion, which may lose precision or narrow the value.
   *
   * @param index the zero-based element index
   * @param fallback the fallback value to return as-is when no compatible value is available
   * @return the converted value, or {@code fallback} for a out-of-bounds index or incompatible tag
   */
  public int getInt(int index, int fallback) {
    Number value = getNumber(index);
    return value == null ? fallback : value.intValue();
  }

  /**
   * Returns the value at the index as a {@code long}.
   *
   * <p>Numeric tags use {@link Number#longValue()} conversion, which may lose precision or narrow the value.
   *
   * @param index the zero-based element index
   * @param fallback the fallback value to return as-is when no compatible value is available
   * @return the converted value, or {@code fallback} for a out-of-bounds index or incompatible tag
   */
  public long getLong(int index, long fallback) {
    Number value = getNumber(index);
    return value == null ? fallback : value.longValue();
  }

  /**
   * Returns the value at the index as a {@code float}.
   *
   * <p>Numeric tags use {@link Number#floatValue()} conversion, which may lose precision or narrow the value.
   *
   * @param index the zero-based element index
   * @param fallback the fallback value to return as-is when no compatible value is available
   * @return the converted value, or {@code fallback} for a out-of-bounds index or incompatible tag
   */
  public float getFloat(int index, float fallback) {
    Number value = getNumber(index);
    return value == null ? fallback : value.floatValue();
  }

  /**
   * Returns the value at the index as a {@code double}.
   *
   * <p>Numeric tags use {@link Number#doubleValue()} conversion, which may lose precision or narrow the value.
   *
   * @param index the zero-based element index
   * @param fallback the fallback value to return as-is when no compatible value is available
   * @return the converted value, or {@code fallback} for a out-of-bounds index or incompatible tag
   */
  public double getDouble(int index, double fallback) {
    Number value = getNumber(index);
    return value == null ? fallback : value.doubleValue();
  }

  /**
   * Returns the value at the index as a {@code boolean}.
   *
   * <p>Boolean tags are returned directly; numeric tags are true when their {@link Number#longValue()} is nonzero.
   *
   * @param index the zero-based element index
   * @param fallback the fallback value to return as-is when no compatible value is available
   * @return the converted value, or {@code fallback} for a out-of-bounds index or incompatible tag
   */
  public boolean getBoolean(int index, boolean fallback) {
    NBT v = get(index);
    if (v instanceof BooleanNBT b) {
      return b.get();
    }
    Number number = getNumber(index);
    return number == null ? fallback : number.longValue() != 0;
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
   * Returns the value at the index as a byte array.
   *
   * <p>The stored value is returned by reference; changes to it affect this container.
   *
   * @param index the zero-based element index
   * @return an optional containing the stored value, or empty for a out-of-bounds index or incompatible tag
   */
  public Optional<byte[]> getBytes(int index) {
    NBT value = get(index);
    return value instanceof ByteArrayNBT tag ? Optional.of(tag.get()) : Optional.empty();
  }

  /**
   * Returns the value at the index as a byte array.
   *
   * <p>The stored value is returned by reference; changes to it affect this container.
   *
   * @param index the zero-based element index
   * @param fallback the fallback value to return as-is when no compatible value is available
   * @return the stored value, or {@code fallback} for a out-of-bounds index or incompatible tag
   */
  public byte[] getBytes(int index, byte[] fallback) {
    return getBytes(index).orElse(fallback);
  }

  /**
   * Returns the value at the index as a byte array.
   *
   * <p>The stored value is returned by reference; changes to it affect this container.
   *
   * <p>The fallback supplier is invoked only for a out-of-bounds index or incompatible tag.
   *
   * @param index the zero-based element index
   * @param fallback supplies a non-null fallback value only when no compatible value is available
   * @return the stored value, or the supplied fallback value for a out-of-bounds index or incompatible tag
   */
  public byte[] getBytes(int index, Supplier<? extends byte[]> fallback) {
    return getBytes(index).orElseGet(fallback);
  }

  /**
   * Returns the value at the index as a nested compound.
   *
   * <p>The stored value is returned by reference; changes to it affect this container.
   *
   * @param index the zero-based element index
   * @return an optional containing the stored value, or empty for a out-of-bounds index or incompatible tag
   */
  public Optional<CompoundNBT> getCompound(int index) {
    NBT value = get(index);
    return value instanceof CompoundNBT tag ? Optional.of(tag) : Optional.empty();
  }

  /**
   * Returns the value at the index as a nested compound.
   *
   * <p>The stored value is returned by reference; changes to it affect this container.
   *
   * @param index the zero-based element index
   * @param fallback the fallback value to return as-is when no compatible value is available
   * @return the stored value, or {@code fallback} for a out-of-bounds index or incompatible tag
   */
  public CompoundNBT getCompound(int index, CompoundNBT fallback) {
    return getCompound(index).orElse(fallback);
  }

  /**
   * Returns the value at the index as a nested compound.
   *
   * <p>The stored value is returned by reference; changes to it affect this container.
   *
   * <p>The fallback supplier is invoked only for a out-of-bounds index or incompatible tag.
   *
   * @param index the zero-based element index
   * @param fallback supplies a non-null fallback value only when no compatible value is available
   * @return the stored value, or the supplied fallback value for a out-of-bounds index or incompatible tag
   */
  public CompoundNBT getCompound(int index, Supplier<? extends CompoundNBT> fallback) {
    return getCompound(index).orElseGet(fallback);
  }

  /**
   * Returns the value at the index as a nested list.
   *
   * <p>The stored value is returned by reference; changes to it affect this container.
   *
   * @param index the zero-based element index
   * @return an optional containing the stored value, or empty for a out-of-bounds index or incompatible tag
   */
  public Optional<ListNBT> getList(int index) {
    NBT value = get(index);
    return value instanceof ListNBT tag ? Optional.of(tag) : Optional.empty();
  }

  /**
   * Returns the value at the index as a nested list.
   *
   * <p>The stored value is returned by reference; changes to it affect this container.
   *
   * @param index the zero-based element index
   * @param fallback the fallback value to return as-is when no compatible value is available
   * @return the stored value, or {@code fallback} for a out-of-bounds index or incompatible tag
   */
  public ListNBT getList(int index, ListNBT fallback) {
    return getList(index).orElse(fallback);
  }

  /**
   * Returns the value at the index as a nested list.
   *
   * <p>The stored value is returned by reference; changes to it affect this container.
   *
   * <p>The fallback supplier is invoked only for a out-of-bounds index or incompatible tag.
   *
   * @param index the zero-based element index
   * @param fallback supplies a non-null fallback value only when no compatible value is available
   * @return the stored value, or the supplied fallback value for a out-of-bounds index or incompatible tag
   */
  public ListNBT getList(int index, Supplier<? extends ListNBT> fallback) {
    return getList(index).orElseGet(fallback);
  }

  /**
   * Returns the tag at the index without checking its subtype.
   *
   * <p>The caller must ensure that the stored tag matches the expected subtype.
   * A stored {@link NullNBT} is returned as a tag and is not treated as absent.
   *
   * @param index the zero-based element index
   * @param <T> the expected tag subtype; no runtime subtype check is performed
   * @return an optional containing the stored tag, or empty for a out-of-bounds index
   */
  public <T extends NBT> Optional<T> tryGet(int index) {
    return Optional.ofNullable(get(index));
  }

  /**
   * Returns the tag at the index without checking its subtype.
   *
   * <p>The caller must ensure that the stored tag matches the expected subtype.
   * A stored {@link NullNBT} is returned as a tag and is not treated as absent.
   *
   * <p>The fallback supplier is invoked only for a out-of-bounds index.
   *
   * @param index the zero-based element index
   * @param <T> the expected tag subtype; no runtime subtype check is performed
   * @param fallback supplies a non-null fallback value only when no compatible value is available
   * @return the stored tag, or the supplied fallback value for a out-of-bounds index
   */
  public <T extends NBT> T get(int index, Supplier<? extends T> fallback) {
    T value = get(index);
    return value != null ? value : fallback.get();
  }

  /**
   * Returns the value at the index as a {@code byte}.
   *
   * <p>Numeric tags use {@link Number#byteValue()} conversion, which may lose precision or narrow the value. Boolean tags map {@code true} to {@code 1} and {@code false} to {@code 0}.
   *
   * <p>The fallback supplier is invoked only for a out-of-bounds index or incompatible tag.
   *
   * @param index the zero-based element index
   * @param fallback supplies a non-null fallback value only when no compatible value is available
   * @return the converted value, or the supplied fallback value for a out-of-bounds index or incompatible tag
   */
  public byte getByte(int index, Supplier<? extends Byte> fallback) {
    return getByte(index).orElseGet(fallback);
  }

  /**
   * Returns the value at the index as a {@code short}.
   *
   * <p>Numeric tags use {@link Number#shortValue()} conversion, which may lose precision or narrow the value.
   *
   * <p>The fallback supplier is invoked only for a out-of-bounds index or incompatible tag.
   *
   * @param index the zero-based element index
   * @param fallback supplies a non-null fallback value only when no compatible value is available
   * @return the converted value, or the supplied fallback value for a out-of-bounds index or incompatible tag
   */
  public short getShort(int index, Supplier<? extends Short> fallback) {
    return getShort(index).orElseGet(fallback);
  }

  /**
   * Returns the value at the index as a {@code int}.
   *
   * <p>Numeric tags use {@link Number#intValue()} conversion, which may lose precision or narrow the value.
   *
   * <p>The fallback supplier is invoked only for a out-of-bounds index or incompatible tag.
   *
   * @param index the zero-based element index
   * @param fallback supplies a non-null fallback value only when no compatible value is available
   * @return the converted value, or the supplied fallback value for a out-of-bounds index or incompatible tag
   */
  public int getInt(int index, Supplier<? extends Integer> fallback) {
    return getInt(index).orElseGet(fallback);
  }

  /**
   * Returns the value at the index as a {@code long}.
   *
   * <p>Numeric tags use {@link Number#longValue()} conversion, which may lose precision or narrow the value.
   *
   * <p>The fallback supplier is invoked only for a out-of-bounds index or incompatible tag.
   *
   * @param index the zero-based element index
   * @param fallback supplies a non-null fallback value only when no compatible value is available
   * @return the converted value, or the supplied fallback value for a out-of-bounds index or incompatible tag
   */
  public long getLong(int index, Supplier<? extends Long> fallback) {
    return getLong(index).orElseGet(fallback);
  }

  /**
   * Returns the value at the index as a {@code float}.
   *
   * <p>Numeric tags use {@link Number#floatValue()} conversion, which may lose precision or narrow the value.
   *
   * <p>The fallback supplier is invoked only for a out-of-bounds index or incompatible tag.
   *
   * @param index the zero-based element index
   * @param fallback supplies a non-null fallback value only when no compatible value is available
   * @return the converted value, or the supplied fallback value for a out-of-bounds index or incompatible tag
   */
  public float getFloat(int index, Supplier<? extends Float> fallback) {
    return getFloat(index).orElseGet(fallback);
  }

  /**
   * Returns the value at the index as a {@code double}.
   *
   * <p>Numeric tags use {@link Number#doubleValue()} conversion, which may lose precision or narrow the value.
   *
   * <p>The fallback supplier is invoked only for a out-of-bounds index or incompatible tag.
   *
   * @param index the zero-based element index
   * @param fallback supplies a non-null fallback value only when no compatible value is available
   * @return the converted value, or the supplied fallback value for a out-of-bounds index or incompatible tag
   */
  public double getDouble(int index, Supplier<? extends Double> fallback) {
    return getDouble(index).orElseGet(fallback);
  }

  /**
   * Returns the value at the index as a {@code boolean}.
   *
   * <p>Boolean tags are returned directly; numeric tags are true when their {@link Number#longValue()} is nonzero.
   *
   * <p>The fallback supplier is invoked only for a out-of-bounds index or incompatible tag.
   *
   * @param index the zero-based element index
   * @param fallback supplies a non-null fallback value only when no compatible value is available
   * @return the converted value, or the supplied fallback value for a out-of-bounds index or incompatible tag
   */
  public boolean getBoolean(int index, Supplier<? extends Boolean> fallback) {
    return getBoolean(index).orElseGet(fallback);
  }

  /**
   * Returns the value at the index as a string.
   *
   * <p>The fallback supplier is invoked only for a out-of-bounds index or incompatible tag.
   *
   * @param index the zero-based element index
   * @param fallback supplies a non-null fallback value only when no compatible value is available
   * @return the stored value, or the supplied fallback value for a out-of-bounds index or incompatible tag
   */
  public String getString(int index, Supplier<? extends String> fallback) {
    return getString(index).orElseGet(fallback);
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
