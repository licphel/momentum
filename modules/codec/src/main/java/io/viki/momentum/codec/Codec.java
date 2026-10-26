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

package io.viki.momentum.codec;

import io.viki.momentum.codec.nbt.*;
import io.viki.momentum.codec.nbt.primitives.*;
import io.viki.momentum.codec.streaming.BinaryBuffer;
import io.viki.momentum.util.Identifier;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Codec for a single value type, handling both binary streaming and NBT.
 *
 * <p>Implementations define only the NBT mapping — {@link #serialize} and
 * {@link #deserialize} — and the NBT root may be any {@link NBT} value. Binary
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
  /** Codec for string values. */
  Codec<String> STRING = of(StringNBT::new, tag -> {
    if (tag instanceof StringNBT value) {
      return value.get();
    }
    throw new IllegalArgumentException("Expected a string, got " + tag.dataType());
  });
  /** Codec for integer values. */
  Codec<Integer> INT = of(IntNBT::new, tag -> {
    if (tag instanceof NumericNBT value) {
      return value.asInt();
    }
    throw new IllegalArgumentException("Expected a number, got " + tag.dataType());
  });
  /** Codec for byte values. */
  Codec<Byte> BYTE = of(ByteNBT::new, tag -> {
    if (tag instanceof NumericNBT value) {
      return value.asByte();
    }
    throw new IllegalArgumentException("Expected a number, got " + tag.dataType());
  });
  /** Codec for short values. */
  Codec<Short> SHORT = of(ShortNBT::new, tag -> {
    if (tag instanceof NumericNBT value) {
      return value.asShort();
    }
    throw new IllegalArgumentException("Expected a number, got " + tag.dataType());
  });
  /** Codec for long values. */
  Codec<Long> LONG = of(LongNBT::new, tag -> {
    if (tag instanceof NumericNBT value) {
      return value.asLong();
    }
    throw new IllegalArgumentException("Expected a number, got " + tag.dataType());
  });
  /** Codec for float values. */
  Codec<Float> FLOAT = of(FloatNBT::new, tag -> {
    if (tag instanceof NumericNBT value) {
      return value.asFloat();
    }
    throw new IllegalArgumentException("Expected a number, got " + tag.dataType());
  });
  /** Codec for double values. */
  Codec<Double> DOUBLE = of(DoubleNBT::new, tag -> {
    if (tag instanceof NumericNBT value) {
      return value.asDouble();
    }
    throw new IllegalArgumentException("Expected a number, got " + tag.dataType());
  });
  /** Codec for boolean values. */
  Codec<Boolean> BOOLEAN = of(BooleanNBT::new, tag -> {
    if (tag instanceof BooleanNBT value) {
      return value.get();
    }
    throw new IllegalArgumentException("Expected a boolean, got " + tag.dataType());
  });
  /** Codec for identifiers represented by their string form. */
  Codec<Identifier> IDENTIFIER = STRING.xmap(Identifier::of, Identifier::toString);
  /** Codec for finite double values and string representations of positive or negative infinity. */
  Codec<Double> EXTENDED_DOUBLE = ifElse(value -> Double.isInfinite(value), tag -> tag instanceof StringNBT,
      STRING.xmap(text -> switch (text.toLowerCase(Locale.ROOT)) {
        case "inf", "+inf" -> Double.POSITIVE_INFINITY;
        case "-inf", "ninf" -> Double.NEGATIVE_INFINITY;
        default -> throw new IllegalArgumentException("Expected inf/+inf/-inf, got " + text);
      }, value -> value > 0 ? "+inf" : "-inf"), DOUBLE);
  /** Codec for integer values and string representations of the minimum and maximum bounds. */
  Codec<Integer> INT_BOUND = ifElse(value -> value == Integer.MIN_VALUE || value == Integer.MAX_VALUE,
      tag -> tag instanceof StringNBT,
      STRING.xmap(text -> switch (text.toLowerCase(Locale.ROOT)) {
        case "inf", "+inf" -> Integer.MAX_VALUE;
        case "-inf", "ninf" -> Integer.MIN_VALUE;
        default -> throw new IllegalArgumentException("Expected an integer bound, got " + text);
      }, value -> value == Integer.MIN_VALUE ? "-inf" : "+inf"), INT);

  /**
   * Creates a codec for integer lists.
   *
   * @return a list codec using {@link #INT} for each element
   */
  static Codec<List<Integer>> intList() {
    return INT.listOf();
  }

  /**
   * Creates a codec for long lists.
   *
   * @return a list codec using {@link #LONG} for each element
   */
  static Codec<List<Long>> longList() {
    return LONG.listOf();
  }

  /**
   * Creates a codec for float lists.
   *
   * @return a list codec using {@link #FLOAT} for each element
   */
  static Codec<List<Float>> floatList() {
    return FLOAT.listOf();
  }

  /**
   * Creates a codec for double lists.
   *
   * @return a list codec using {@link #DOUBLE} for each element
   */
  static Codec<List<Double>> doubleList() {
    return DOUBLE.listOf();
  }

  /**
   * Creates a codec for string lists.
   *
   * @return a list codec using {@link #STRING} for each element
   */
  static Codec<List<String>> stringList() {
    return STRING.listOf();
  }

  /**
   * Returns the codec for byte arrays.
   *
   * @return the byte-array codec
   */
  static Codec<byte[]> byteArray() {
    return PrimitiveArrayCodecs.BYTE;
  }

  /**
   * Returns the codec for integer arrays.
   *
   * @return the integer-array codec
   */
  static Codec<int[]> intArray() {
    return PrimitiveArrayCodecs.INT;
  }

  /**
   * Returns the codec for long arrays.
   *
   * @return the long-array codec
   */
  static Codec<long[]> longArray() {
    return PrimitiveArrayCodecs.LONG;
  }

  /**
   * Returns the codec for float arrays.
   *
   * @return the float-array codec
   */
  static Codec<float[]> floatArray() {
    return PrimitiveArrayCodecs.FLOAT;
  }

  /**
   * Returns the codec for double arrays.
   *
   * @return the double-array codec
   */
  static Codec<double[]> doubleArray() {
    return PrimitiveArrayCodecs.DOUBLE;
  }

  /**
   * Starts an immutable schema for mutable objects. The factory must return a fresh instance for each decode.
   *
   * @param factory creates the object populated during decoding
   * @param <A> the object type
   * @return an empty object schema
   */
  static <A> ObjectCodec<A> object(Supplier<A> factory) {
    return new ObjectCodec<>(factory);
  }

  /**
   * Starts an immutable schema for records and other constructor-created values.
   *
   * @param <A> the value type
   * @return an empty constructor-based schema
   */
  static <A> RecordCodec<A> record() {
    return new RecordCodec<>();
  }

  /**
   * Selects a codec branch independently for encoding and decoding. Encoding may canonicalize shorthand accepted
   * by decoding.
   *
   * @param valueCondition selects the encoding branch from a value
   * @param dataCondition selects the decoding branch from an NBT value
   * @param whenTrue the branch used when its corresponding condition is true
   * @param whenFalse the branch used when its corresponding condition is false
   * @param <A> the value type
   * @return a codec that delegates to the selected branch
   */
  static <A> Codec<A> ifElse(Predicate<A> valueCondition, Predicate<NBT> dataCondition,
                             Codec<A> whenTrue, Codec<A> whenFalse) {
    return of(value -> (valueCondition.test(value) ? whenTrue : whenFalse).serialize(value),
        tag -> (dataCondition.test(tag) ? whenTrue : whenFalse).deserialize(tag));
  }

  /**
   * Defers codec lookup until conversion, allowing recursive schemas without initialization cycles.
   *
   * @param supplier provides the codec used for each conversion
   * @param <A> the value type
   * @return a codec that delegates conversions to the supplied codec
   */
  static <A> Codec<A> lazy(Supplier<? extends Codec<A>> supplier) {
    return of(value -> supplier.get().serialize(value), tag -> supplier.get().deserialize(tag));
  }

  /**
   * Creates a codec for enum constants using their names.
   *
   * @param type the enum class
   * @param <E> the enum type
   * @return a codec that writes lowercase names and reads names without regard to case
   * @throws IllegalArgumentException if decoded text does not name a constant of {@code type}
   */
  static <E extends Enum<E>> Codec<E> enumOf(Class<E> type) {
    return STRING.xmap(name -> Enum.valueOf(type, name.toUpperCase(Locale.ROOT)),
        value -> value.name().toLowerCase(Locale.ROOT));
  }

  /**
   * Dispatches an object schema by a discriminator field. Subtype codecs must encode objects;
   * the discriminator is owned by this codec and must not be a subtype schema field.
   *
   * @param field the literal discriminator field name
   * @param keyCodec encodes and decodes discriminator values
   * @param keyOf obtains the discriminator value for an object
   * @param variants selects the subtype codec for a discriminator value
   * @param <A> the common value type
   * @param <K> the discriminator type
   * @return a codec that reads and writes the discriminator with the selected subtype
   * @throws IllegalArgumentException when a variant does not encode a compound or conflicts with the discriminator
   */
  static <A, K> Codec<A> dispatch(String field, Codec<K> keyCodec, Function<A, K> keyOf,
                                  Function<K, ? extends Codec<? extends A>> variants) {
    return object(value -> {
      K key = keyOf.apply(value);
      NBT encoded = encodeVariant(variants.apply(key), value);
      if (!(encoded instanceof CompoundNBT object)) {
        throw new IllegalArgumentException("Dispatch variant must encode an object: " + key);
      }
      CompoundNBT result = object.copy();
      if (result.directGet(field) != null) {
        throw new IllegalArgumentException("Dispatch field conflicts with variant field: " + field);
      }
      keyCodec.writeField(result, field, key);
      return result;
    }, data -> variants.apply(keyCodec.readField(data, field)).deserialize(data));
  }

  @SuppressWarnings("unchecked")
  private static <A> NBT encodeVariant(Codec<? extends A> codec, A value) {
    // The discriminator's keyOf/variants pair owns the association between subtype and codec.
    return ((Codec<A>) codec).serialize(value);
  }

  /**
   * Creates an object codec that requires a compound root before decoding.
   *
   * @param encoder converts a value to a compound tag
   * @param decoder converts a compound tag to a value
   * @param <A> the value type
   * @return a codec with the supplied object mappings
   */
  static <A> Codec<A> object(Function<A, CompoundNBT> encoder, Function<CompoundNBT, A> decoder) {
    return of(encoder::apply, tag -> {
      if (!(tag instanceof CompoundNBT object)) {
        throw new IllegalArgumentException("Expected an object, got " + tag.dataType());
      }
      return decoder.apply(object);
    });
  }

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
      public NBT serialize(A value) {
        return encoder.apply(value);
      }

      @Override
      public A deserialize(NBT nbt) {
        return decoder.apply(nbt);
      }
    };
  }

  /**
   * Creates a codec for a list of values, backed by a {@link ListNBT} root.
   * The returned codec throws {@link IllegalArgumentException} when decoding a non-list root or invalid element.
   *
   * @param element the element codec
   * @param <A>     the element type
   * @return a codec that converts lists using {@code element} for each item
   */
  static <A> Codec<List<A>> listOf(Codec<A> element) {
    return Codec.of(
        values -> {
          ListNBT list = new ListNBT();
          int index = 0;
          for (A value : values) {
            try {
              list.add(element.serialize(value));
            } catch (IllegalArgumentException exception) {
              throw new IllegalArgumentException("Element [" + index + "]: " + exception.getMessage(), exception);
            }
            index++;
          }
          return list;
        },
        nbt -> {
          if (!(nbt instanceof ListNBT list)) {
            throw new IllegalArgumentException("Expected ListNBT but got " + nbt.dataType());
          }
          List<A> result = new ArrayList<>(list.size());
          for (int index = 0; index < list.size(); index++) {
            try {
              NBT value = Objects.requireNonNull(list.get(index));
              result.add(element.deserialize(value));
            } catch (IllegalArgumentException exception) {
              throw new IllegalArgumentException("Element [" + index + "]: " + exception.getMessage(), exception);
            }
          }
          return result;
        });
  }

  /**
   * Represents an absent value as JSON/NBT null. Optional object fields may also be omitted.
   *
   * @return a codec that maps empty optionals to NBT null and back
   */
  default Codec<Optional<A>> optional() {
    return of(value -> value.map(this::serialize).orElse(NullNBT.INSTANCE),
        tag -> tag instanceof NullNBT ? Optional.empty() : Optional.of(deserialize(tag)));
  }

  /**
   * Checks a domain constraint in both directions.
   *
   * @param condition accepts values that satisfy the constraint
   * @param message explains why a value is rejected
   * @return a codec that validates values both before encoding and after decoding
   * @throws IllegalArgumentException when the condition rejects a value during conversion
   */
  default Codec<A> validate(Predicate<? super A> condition, String message) {
    Function<A, A> checked = value -> {
      if (!condition.test(value)) {
        throw new IllegalArgumentException(message);
      }
      return value;
    };
    return xmap(checked, checked);
  }

  /**
   * Supplies a fixed recovery value when decoding fails. Unlike optional fields, this also handles malformed data.
   *
   * @param fallback the value returned after an {@link IllegalArgumentException} during decoding
   * @return a codec that uses the fallback for invalid input
   */
  default Codec<A> orElse(A fallback) {
    return orElseGet(() -> fallback);
  }

  /**
   * Creates a fallback when decoding fails; encoding still uses the original schema.
   *
   * @param fallback supplies a value after an {@link IllegalArgumentException} during decoding
   * @return a codec that uses a supplied fallback for invalid input
   */
  default Codec<A> orElseGet(Supplier<? extends A> fallback) {
    return of(this::serialize, tag -> {
      try {
        return deserialize(tag);
      } catch (IllegalArgumentException exception) {
        return fallback.get();
      }
    });
  }

  /**
   * Encodes a string-keyed object map. Keys are literal names, never compound paths.
   *
   * @return a codec for maps whose values use this codec
   */
  default Codec<Map<String, A>> mapOf() {
    return object(values -> {
      CompoundNBT data = new CompoundNBT();
      values.forEach((key, value) -> writeField(data, key, value));
      return data;
    }, data -> {
      Map<String, A> result = new LinkedHashMap<>();
      for (String key : data.keySet()) {
        result.put(key, readField(data, key));
      }
      return result;
    });
  }

  /**
   * Reads a required field. Field names are literal, including names starting with {@code '$'}.
   *
   * @param object the compound value containing the field
   * @param name the literal field name
   * @return the decoded field value
   * @throws IllegalArgumentException if the field is missing or cannot be decoded
   */
  default A readField(CompoundNBT object, String name) {
    NBT value = object.directGet(name);
    if (value == null) {
      throw new IllegalArgumentException("Missing field '" + name + "'");
    }
    try {
      return deserialize(value);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Field '" + name + "': " + exception.getMessage(), exception);
    }
  }

  /**
   * Reads a field with a fallback used only when the field is absent.
   * Explicit null and invalid values are decoded normally.
   *
   * @param object the compound value containing the field
   * @param name the literal field name
   * @param fallback the value returned when the field is absent
   * @return the decoded field value or {@code fallback}
   * @throws IllegalArgumentException if a present field cannot be decoded
   */
  default A readField(CompoundNBT object, String name, A fallback) {
    return object.directGet(name) != null ? readField(object, name) : fallback;
  }

  /**
   * Encodes a value into a compound field using its literal name.
   *
   * @param object the compound value to update
   * @param name the literal field name
   * @param value the value to encode
   * @throws IllegalArgumentException if the value cannot be encoded
   */
  default void writeField(CompoundNBT object, String name, A value) {
    try {
      object.directPut(name, serialize(value));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Field '" + name + "': " + exception.getMessage(), exception);
    }
  }

  /**
   * Creates a codec for lists of this codec's value type.
   *
   * @return a list codec that uses this codec for each element
   */
  default Codec<List<A>> listOf() {
    return listOf(this);
  }

  /**
   * Encodes a value as JSON. Any NBT root is supported; registry names in this format are persistent identifiers.
   *
   * @param value the value to encode
   * @param pretty whether to include formatting whitespace
   * @return the JSON representation
   */
  default String toJson(A value, boolean pretty) {
    return JsonUtil.dump(serialize(value), pretty);
  }

  /**
   * Decodes a value from JSON.
   *
   * @param json the JSON text to decode
   * @return the decoded value
   * @throws IllegalArgumentException if the JSON or its decoded value is invalid
   */
  default A fromJson(String json) {
    return deserialize(JsonUtil.parseValue(json));
  }

  /**
   * Converts a value to its NBT representation.
   *
   * @param value the value to encode
   * @return the NBT tree
   */
  NBT serialize(A value);

  /**
   * Converts an NBT tree back to a value.
   *
   * @param nbt the NBT tree to decode
   * @return the decoded value
   * @throws IllegalArgumentException if the tree does not match the expected shape
   */
  A deserialize(NBT nbt);

  /**
   * Writes the value to a binary stream, using the NBT binary format.
   *
   * @param value  the value to encode
   * @param buffer the destination buffer
   */
  default void serialize(A value, BinaryBuffer buffer) {
    NBT nbt = serialize(value);
    buffer.write(nbt.dataType().id());
    buffer.writeNBT(nbt);
  }

  /**
   * Reads a value from a binary stream, using the NBT binary format.
   *
   * @param buffer the source buffer
   * @return the decoded value
   */
  default A deserialize(BinaryBuffer buffer) {
    return deserialize(buffer.readNBT(DataType.fromID(buffer.read())));
  }

  /**
   * Maps this codec to a different value type through a bidirectional conversion.
   *
   * <p>Encoding converts the target value back through {@code from} before
   * {@link #serialize}, and decoding applies {@code to} after {@link #deserialize}.
   * This is useful for wrapping raw types (UUIDs, instants) on top of an
   * existing codec.
   *
   * @param to   converts a value of this codec to the target type
   * @param from converts a value of the target type back to this codec's type
   * @param <B>  the target value type
   * @return the mapped codec
   */
  default <B> Codec<B> xmap(Function<? super A, ? extends B> to, Function<? super B, ? extends A> from) {
    return Codec.of(value -> serialize(from.apply(value)), nbt -> to.apply(deserialize(nbt)));
  }
}
