package io.viki.momentum.codec;

import io.viki.momentum.codec.nbt.CompoundNBT;
import io.viki.momentum.codec.nbt.NBT;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Immutable field schema for mutable objects, safe to share when callbacks are thread-safe.
 * Each decode creates a fresh instance, and extending a schema leaves the parent unchanged.
 *
 * @param <T> the object type described by this schema
 */
public final class ObjectCodec<T> implements Codec<T> {
  private final Supplier<T> factory;
  private final List<Binding<? super T>> fields;

  ObjectCodec(Supplier<T> factory) {
    this(factory, List.of());
  }

  private ObjectCodec(Supplier<T> factory, List<Binding<? super T>> fields) {
    this.factory = factory;
    this.fields = List.copyOf(fields);
  }

  /**
   * Adds a required field to this schema.
   *
   * @param name the literal compound-field name
   * @param codec converts the field value
   * @param getter obtains the field value during encoding
   * @param setter applies the decoded field value to the new object
   * @param <V> the field value type
   * @return a schema containing the added field
   * @throws IllegalArgumentException if the schema already declares {@code name}
   */
  public <V> ObjectCodec<T> field(String name, Codec<V> codec, Function<T, V> getter,
                                  BiConsumer<T, V> setter) {
    return append(new Binding<>(name, (object, data) -> setter.accept(object, codec.readField(data, name)),
        (object, data) -> codec.writeField(data, name, getter.apply(object))));
  }

  /**
   * Adds a field that retains the factory-provided value when absent and decodes any present value normally.
   *
   * @param name the literal compound-field name
   * @param codec converts the field value
   * @param getter obtains the field value during encoding
   * @param setter applies a decoded field value to the new object
   * @param <V> the field value type
   * @return a schema containing the optional field
   * @throws IllegalArgumentException if the schema already declares {@code name}
   */
  public <V> ObjectCodec<T> optionalField(String name, Codec<V> codec, Function<T, V> getter,
                                          BiConsumer<T, V> setter) {
    return append(new Binding<>(name, (object, data) -> {
      if (data.directGet(name) != null) {
        setter.accept(object, codec.readField(data, name));
      }
    }, (object, data) -> codec.writeField(data, name, getter.apply(object))));
  }

  /**
   * Adds a field that uses a fallback only when the field is absent.
   *
   * @param name the literal compound-field name
   * @param codec converts the field value
   * @param getter obtains the field value during encoding
   * @param setter applies the decoded or fallback value to the new object
   * @param fallback the value used when the field is missing
   * @param <V> the field value type
   * @return a schema containing the optional field
   * @throws IllegalArgumentException if the schema already declares {@code name}
   */
  public <V> ObjectCodec<T> optionalField(String name, Codec<V> codec, Function<T, V> getter,
                                          BiConsumer<T, V> setter, V fallback) {
    return append(new Binding<>(name,
        (object, data) -> setter.accept(object, codec.readField(data, name, fallback)),
        (object, data) -> codec.writeField(data, name, getter.apply(object))));
  }

  /**
   * Adds an optional field, omitting empty values on write and accepting missing or explicit-null values on read.
   *
   * @param name the literal compound-field name
   * @param codec converts the contained value
   * @param getter obtains the optional field during encoding
   * @param setter applies the decoded optional value to the new object
   * @param <V> the contained value type
   * @return a schema containing the optional field
   * @throws IllegalArgumentException if the schema already declares {@code name}
   */
  public <V> ObjectCodec<T> optional(String name, Codec<V> codec, Function<T, Optional<V>> getter,
                                     BiConsumer<T, Optional<V>> setter) {
    Codec<Optional<V>> optional = codec.optional();
    return append(new Binding<>(name,
        (object, data) -> setter.accept(object, optional.readField(data, name, Optional.empty())),
        (object, data) -> getter.apply(object).ifPresent(value -> codec.writeField(data, name, value))));
  }

  /**
   * Creates a subtype schema that inherits these fields and uses its own factory.
   *
   * @param factory creates a fresh subtype instance for decoding
   * @param <S> the subtype
   * @return an independent schema for {@code S} containing the inherited fields
   */
  public <S extends T> ObjectCodec<S> extend(Supplier<S> factory) {
    List<Binding<? super S>> inherited = new ArrayList<>(fields);
    return new ObjectCodec<>(factory, inherited);
  }

  /**
   * Encodes the object's declared fields into a compound tag.
   *
   * @param value the object to encode
   * @return its compound representation
   */
  @Override
  public CompoundNBT serialize(T value) {
    CompoundNBT data = new CompoundNBT();
    for (Binding<? super T> field : fields) {
      field.encode.accept(value, data);
    }
    return data;
  }

  /**
   * Creates a fresh object and applies its declared fields from a compound tag.
   *
   * @param tag the compound tag to decode
   * @return the newly created and populated object
   * @throws IllegalArgumentException if {@code tag} is not a compound
   */
  @Override
  public T deserialize(NBT tag) {
    CompoundNBT data = CompoundNBT.CODEC.deserialize(tag);
    T value = factory.get();
    for (Binding<? super T> field : fields) {
      field.decode.accept(value, data);
    }
    return value;
  }

  private ObjectCodec<T> append(Binding<T> field) {
    for (Binding<? super T> existing : fields) {
      if (existing.name.equals(field.name)) {
        throw new IllegalArgumentException("Duplicate codec field: " + field.name);
      }
    }
    List<Binding<? super T>> result = new ArrayList<>(fields);
    result.add(field);
    return new ObjectCodec<>(factory, result);
  }

  private record Binding<T>(String name, BiConsumer<T, CompoundNBT> decode,
                            BiConsumer<T, CompoundNBT> encode) {
  }
}
