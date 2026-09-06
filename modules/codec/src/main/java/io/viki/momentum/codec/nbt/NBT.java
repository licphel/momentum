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
import io.viki.momentum.codec.nbt.primitives.*;
import io.viki.momentum.codec.nbt.primitives.*;
import org.jspecify.annotations.Nullable;

/**
 * Contract for all NBT tag wrappers that provide type-safe serialization.
 *
 * <p>Implementations encapsulate a single NBT value and handle serialization,
 * deep copying, and identity comparison independently.
 */
public interface NBT {
  /**
   * Wraps a raw value in its corresponding tag representation.
   *
   * @param value the value to wrap, may be {@code null}
   * @return the tag instance, never {@code null}
   * @throws IllegalArgumentException if the value has no NBT mapping
   */
  static NBT wrap(@Nullable Object value) {
    if (value instanceof NBT nbt) {
      return nbt;
    }
    if (value == null) {
      return NullNBT.INSTANCE;
    }

    return switch (DataType.fromValue(value)) {
      case BYTE -> new ByteNBT((Byte) value);
      case SHORT -> new ShortNBT((Short) value);
      case INT -> new IntNBT((Integer) value);
      case LONG -> new LongNBT((Long) value);
      case FLOAT -> new FloatNBT((Float) value);
      case DOUBLE -> new DoubleNBT((Double) value);
      case BOOLEAN -> new BooleanNBT((Boolean) value);
      case STRING -> new StringNBT((String) value);
      case BYTE_ARRAY -> {
        if (value instanceof byte[] bytes) {
          yield new ByteArrayNBT(bytes);
        }
        Byte[] boxed = (Byte[]) value;
        byte[] bytes = new byte[boxed.length];
        for (int i = 0; i < boxed.length; i++) {
          bytes[i] = boxed[i];
        }
        yield new ByteArrayNBT(bytes);
      }
      default -> throw new IllegalArgumentException("Invalid NBT value: " + value);
    };
  }

  /**
   * Returns the type identifier for this tag.
   *
   * @return the corresponding {@link DataType}
   */
  DataType dataType();

  /**
   * Returns the codec that encodes and decodes this tag's binary payload.
   *
   * @return the codec for this tag's type
   */
  default Codec<? extends NBT> codec() {
    return dataType().codec();
  }

  /**
   * Returns the underlying Java value, or {@code null} for null tags.
   *
   * @return the raw value, may be {@code null}
   */
  @Nullable Object asObject();

  /**
   * Produces an independent copy with the same value.
   *
   * @return a new tag instance with equivalent data
   */
  NBT copy();

  /**
   * Returns the hash code for this tag.
   *
   * @return a hash code consistent with equality
   */
  @Override
  int hashCode();

  /**
   * Compares this tag to the given object for equality.
   *
   * @param obj the object to compare
   * @return {@code true} if the objects are equal tags
   */
  @Override
  boolean equals(Object obj);

  /**
   * Returns a readable representation of this tag's value.
   *
   * @return the string representation
   */
  @Override
  String toString();
}
