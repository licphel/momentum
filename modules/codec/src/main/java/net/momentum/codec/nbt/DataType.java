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

package net.momentum.codec.nbt;

import net.momentum.codec.Codec;
import net.momentum.codec.nbt.primitives.*;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * NBT type identifiers mapped from binary byte IDs to Java types.
 */
public enum DataType {
  /** End marker. */
  END(0, NullNBT.CODEC),
  /** Null value. */
  NULL(1, NullNBT.CODEC),
  /** Byte value. */
  BYTE(2, ByteNBT.CODEC),
  /** Boolean value. */
  BOOLEAN(3, BooleanNBT.CODEC),
  /** Short value. */
  SHORT(4, ShortNBT.CODEC),
  /** Integer value. */
  INT(5, IntNBT.CODEC),
  /** Long value. */
  LONG(6, LongNBT.CODEC),
  /** Float value. */
  FLOAT(7, FloatNBT.CODEC),
  /** Double value. */
  DOUBLE(8, DoubleNBT.CODEC),
  /** Byte array. */
  BYTE_ARRAY(9, ByteArrayNBT.CODEC),
  /** String value. */
  STRING(10, StringNBT.CODEC),
  /** List tag. */
  LIST(11, ListNBT.CODEC),
  /** Compound tag. */
  COMPOUND(12, CompoundNBT.CODEC),
  /** Unknown type. */
  UNKNOWN(255, NullNBT.CODEC);

  private static final Map<Byte, DataType> ID_LOOKUP;
  private static final Map<Class<?>, DataType> CLASS_LOOKUP;

  static {
    Map<Byte, DataType> idMap = new HashMap<>();
    Map<Class<?>, DataType> classMap = new HashMap<>();

    for (DataType mark : values()) {
      idMap.put(mark.id, mark);
    }

    classMap.put(byte.class, BYTE);
    classMap.put(Byte.class, BYTE);
    classMap.put(boolean.class, BOOLEAN);
    classMap.put(Boolean.class, BOOLEAN);
    classMap.put(short.class, SHORT);
    classMap.put(Short.class, SHORT);
    classMap.put(int.class, INT);
    classMap.put(Integer.class, INT);
    classMap.put(long.class, LONG);
    classMap.put(Long.class, LONG);
    classMap.put(float.class, FLOAT);
    classMap.put(Float.class, FLOAT);
    classMap.put(double.class, DOUBLE);
    classMap.put(Double.class, DOUBLE);
    classMap.put(byte[].class, BYTE_ARRAY);
    classMap.put(Byte[].class, BYTE_ARRAY);
    classMap.put(String.class, STRING);
    classMap.put(ListNBT.class, LIST);
    classMap.put(CompoundNBT.class, COMPOUND);

    ID_LOOKUP = Collections.unmodifiableMap(idMap);
    CLASS_LOOKUP = Collections.unmodifiableMap(classMap);
  }

  private final byte id;
  private final Codec<? extends NBT> codec;

  DataType(int id, Codec<? extends NBT> codec) {
    this.id = (byte) (Byte.MIN_VALUE + id);
    this.codec = codec;
  }

  /**
   * Looks up a {@code TagMark} by its raw byte identifier. O(1).
   *
   * @param bid the byte identifier from the serialized stream
   * @return the matching mark, or {@link #UNKNOWN} if no match
   */
  public static DataType fromID(byte bid) {
    return ID_LOOKUP.getOrDefault(bid, UNKNOWN);
  }

  /**
   * Maps a Java class to its corresponding NBT tag type. O(1).
   *
   * @param type the Java class to inspect
   * @return the corresponding mark, or {@link #UNKNOWN} if the class has no NBT mapping
   */
  public static DataType fromClass(@Nullable Class<?> type) {
    if (type == null) {
      return NULL;
    }
    return CLASS_LOOKUP.getOrDefault(type, UNKNOWN);
  }

  /**
   * Determines the NBT tag type of a runtime value.
   *
   * <p>{@link NBT} instances are dispatched via {@link NBT#dataType()}; raw
   * values (boxed primitives, {@code String}, {@code byte[]}, {@link CompoundNBT}, {@link ListNBT})
   * are mapped through {@link #fromClass(Class)}.
   *
   * @param value the object to inspect
   * @return the corresponding mark
   */
  public static DataType fromValue(@Nullable Object value) {
    if (value == null) {
      return NULL;
    }
    if (value instanceof NBT nbt) {
      return nbt.dataType();
    }
    return fromClass(value.getClass());
  }

  /**
   * Validates that a value can be stored in an NBT structure.
   *
   * @param object the value to validate, may be {@code null}
   * @throws IllegalArgumentException if the value's type has no NBT tag mapping
   */
  public static void validate(@Nullable Object object) {
    if (fromValue(object) == UNKNOWN) {
      throw new IllegalArgumentException("Invalid NBT value: " + object);
    }
  }

  /**
   * Returns the raw byte identifier used in the binary format.
   *
   * @return the NBT tag byte
   */
  public byte id() {
    return id;
  }

  /**
   * Returns the codec that encodes and decodes values of this type.
   *
   * <p>{@link #END}, {@link #NULL} and {@link #UNKNOWN} share the null codec,
   * which carries no payload.
   *
   * @return the type's codec
   */
  public Codec<? extends NBT> codec() {
    return codec;
  }
}