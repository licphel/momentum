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

package net.fmhi.codec;

import net.fmhi.codec.nbt.DataType;
import net.fmhi.codec.nbt.ListNBT;
import net.fmhi.codec.nbt.NBT;
import net.fmhi.codec.streaming.CursorBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Codec for a single value type, handling both binary streaming and NBT.
 *
 * <p>Implementations define only the NBT mapping — {@link #toNbt} and
 * {@link #fromNbt} — and the NBT root may be any {@link NBT} value. Binary
 * streaming is derived from that mapping automatically: {@link #serialize}
 * writes the value's NBT tree through the tag binary format, and
 * {@link #deserialize} reads a tree back and converts it. One codec therefore
 * serves both formats, and the same object instance can be encoded to a byte
 * stream or a tag tree without any format-specific code in the object itself.
 *
 * <p>Codecs are decoupled from the objects they describe: they can be stored,
 * reused and composed independently of the value type. Instances must be
 * stateless and thread-safe.
 *
 * @param <A> the value type
 */
public interface Codec<A> {
  /**
   * Creates a codec from the two NBT mapping functions directly.
   *
   * @param encoder converts a value to its NBT representation
   * @param decoder converts an NBT tree back to a value
   * @param <A>     the value type
   * @return the codec
   */
  static <A> Codec<A> of(Function<A, NBT> encoder, Function<NBT, A> decoder) {
    return new Codec<>() {
      @Override
      public NBT toNbt(A value) {
        return encoder.apply(value);
      }

      @Override
      public A fromNbt(NBT nbt) {
        return decoder.apply(nbt);
      }
    };
  }

  /**
   * Creates a codec for a list of values, backed by a {@link ListNBT} root.
   *
   * @param element the element codec
   * @param <A>     the element type
   * @return the list codec
   */
  static <A> Codec<List<A>> listOf(Codec<A> element) {
    return Codec.of(
        values -> {
          ListNBT list = new ListNBT();
          for (A value : values) {
            list.add(element.toNbt(value));
          }
          return list;
        },
        nbt -> {
          if (!(nbt instanceof ListNBT list)) {
            throw new IllegalArgumentException("Expected ListNBT but got " + nbt.dataType());
          }
          List<A> result = new ArrayList<>(list.size());
          for (NBT nbtValue : list) {
            result.add(element.fromNbt(nbtValue));
          }
          return result;
        });
  }

  /**
   * Converts a value to its NBT representation.
   *
   * @param value the value to encode
   * @return the NBT tree
   */
  NBT toNbt(A value);

  /**
   * Converts an NBT tree back to a value.
   *
   * @param nbt the NBT tree to decode
   * @return the decoded value
   * @throws IllegalArgumentException if the tree does not match the expected shape
   */
  A fromNbt(NBT nbt);

  /**
   * Writes the value to a binary stream, using the NBT binary format.
   *
   * @param value  the value to encode
   * @param buffer the destination buffer
   */
  default void serialize(A value, CursorBuffer buffer) {
    NBT nbt = toNbt(value);
    buffer.write(nbt.dataType().id());
    buffer.writeNBT(nbt);
  }

  /**
   * Reads a value from a binary stream, using the NBT binary format.
   *
   * @param buffer the source buffer
   * @return the decoded value
   */
  default A deserialize(CursorBuffer buffer) {
    return fromNbt(buffer.readNBT(DataType.fromID(buffer.read())));
  }

  /**
   * Maps this codec to a different value type through a bidirectional conversion.
   *
   * <p>Encoding converts the target value back through {@code from} before
   * {@link #toNbt}, and decoding applies {@code to} after {@link #fromNbt}.
   * This is useful for wrapping raw types (UUIDs, instants) on top of an
   * existing codec.
   *
   * @param to   converts a value of this codec to the target type
   * @param from converts a value of the target type back to this codec's type
   * @param <B>  the target value type
   * @return the mapped codec
   */
  default <B> Codec<B> xmap(Function<? super A, ? extends B> to, Function<? super B, ? extends A> from) {
    return Codec.of(value -> toNbt(from.apply(value)), nbt -> to.apply(fromNbt(nbt)));
  }
}
