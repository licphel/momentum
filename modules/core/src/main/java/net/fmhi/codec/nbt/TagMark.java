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

package net.fmhi.codec.nbt;

import org.jspecify.annotations.Nullable;

/**
 * NBT (Named Binary Tag) type identifiers for binary serialization.
 *
 * <p>Each tag value in a serialized NBT stream is prefixed with a single byte
 * identifying its type. This enum maps those byte IDs to the corresponding Java types and provides helpers for type
 * inspection and validation.
 *
 * @see CompoundTag#serialize
 * @see CompoundTag#deserialize
 */
public enum TagMark {
  END(0),
  NULL(1),
  BYTE(2),
  BOOLEAN(3),
  SHORT(4),
  INT(5),
  LONG(6),
  FLOAT(7),
  DOUBLE(8),
  BYTE_ARRAY(9),
  STRING(10),
  LIST(11),
  COMPOUND(12),
  UNKNOWN(255);

  private final byte id;

  TagMark(int id) {
    this.id = (byte) (Byte.MIN_VALUE + id);
  }

  /**
   * Looks up a {@code TagMark} by its raw byte identifier.
   *
   * <p>Returns {@link #UNKNOWN} if the byte does not correspond to any known tag type,
   * rather than throwing — this allows forward-compatible parsing of streams that may contain tags from a newer
   * specification.
   *
   * @param bid the byte identifier from the serialized stream
   * @return the matching mark, or {@link #UNKNOWN} if no match
   */
  public static TagMark fromID(byte bid) {
    for (TagMark type : values()) {
      if (type.id == bid) {
        return type;
      }
    }
    return UNKNOWN;
  }

  /**
   * Maps a Java class to its corresponding NBT tag type.
   *
   * <p>Both primitive types ({@code int.class}) and their boxed equivalents
   * ({@code Integer.class}) are recognized. Arrays of {@code byte} or {@code Byte} map to {@link #BYTE_ARRAY}.
   * NBT-specific types ({@link CompoundTag}, {@link ListTag}) are recognized directly.
   *
   * @param type the Java class to inspect
   * @return the corresponding mark, or {@link #UNKNOWN} if the class has no NBT mapping
   */
  public static TagMark fromClass(Class<?> type) {
    if (type == byte.class || type == Byte.class) {
      return BYTE;
    }
    if (type == boolean.class || type == Boolean.class) {
      return BOOLEAN;
    }
    if (type == short.class || type == Short.class) {
      return SHORT;
    }
    if (type == int.class || type == Integer.class) {
      return INT;
    }
    if (type == long.class || type == Long.class) {
      return LONG;
    }
    if (type == float.class || type == Float.class) {
      return FLOAT;
    }
    if (type == double.class || type == Double.class) {
      return DOUBLE;
    }
    if (type == byte[].class || type == Byte[].class) {
      return BYTE_ARRAY;
    }
    if (type == String.class) {
      return STRING;
    }
    if (type == ListTag.class) {
      return LIST;
    }
    if (type == CompoundTag.class) {
      return COMPOUND;
    }
    return UNKNOWN;
  }

  /**
   * Determines the NBT tag type of runtime value.
   *
   * <p>Returns {@link #NULL} for {@code null} input; otherwise delegates to
   * {@link #fromClass(Class)} using the value's concrete class.
   *
   * @param value the object to inspect
   * @return the corresponding mark
   */
  public static TagMark fromValue(@Nullable Object value) {
    if (value == null) {
      return NULL;
    }
    return fromClass(value.getClass());
  }

  /**
   * Validates that a value can be stored in an NBT structure.
   *
   * <p>This is a precondition check used by {@link CompoundTag#put} and
   * {@link ListTag#add} to reject unsupported types early.
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
   * <p>The ID is stored as an unsigned byte in the range {@code [0, 255]}.
   *
   * @return the NBT tag byte
   */
  public byte id() {
    return id;
  }
}
