package io.viki.momentum.codec;

import io.viki.momentum.codec.nbt.CompoundNBT;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Immutable constructor-based schema. Fields are declared once for both directions.
 * Schemas and completed codecs are thread-safe when their callbacks are thread-safe.
 *
 * @param <T> the value type created from the schema
 */
public final class RecordCodec<T> {
  private final List<Field<T, ?>> fields;

  RecordCodec() {
    this(List.of());
  }

  private RecordCodec(List<Field<T, ?>> fields) {
    this.fields = List.copyOf(fields);
  }

  /**
   * Adds a required field to this schema.
   *
   * @param name the literal compound-field name
   * @param codec converts the field value
   * @param getter obtains the field value from the encoded object
   * @param <V> the field value type
   * @return a schema containing the added field
   * @throws IllegalArgumentException if the schema already declares {@code name}
   */
  public <V> RecordCodec<T> field(String name, Codec<V> codec, Function<T, V> getter) {
    return append(new Field<>(name, codec, getter, null, false));
  }

  /**
   * Adds an optional field whose fallback is used only when the field is missing.
   * Mutable defaults should use {@link #optionalFieldGet(String, Codec, Function, Supplier)}.
   *
   * @param name the literal compound-field name
   * @param codec converts the field value
   * @param getter obtains the field value from the encoded object
   * @param fallback the value used when the field is missing
   * @param <V> the field value type
   * @return a schema containing the optional field
   * @throws IllegalArgumentException if the schema already declares {@code name}
   */
  public <V> RecordCodec<T> optionalField(String name, Codec<V> codec, Function<T, V> getter, V fallback) {
    return optionalFieldGet(name, codec, getter, () -> fallback);
  }

  /**
   * Adds an optional field whose fallback is created for each decode when the field is missing.
   *
   * @param name the literal compound-field name
   * @param codec converts the field value
   * @param getter obtains the field value from the encoded object
   * @param fallback creates a fallback value for a missing field
   * @param <V> the field value type
   * @return a schema containing the optional field
   * @throws IllegalArgumentException if the schema already declares {@code name}
   */
  public <V> RecordCodec<T> optionalFieldGet(String name, Codec<V> codec, Function<T, V> getter,
                                             Supplier<V> fallback) {
    return append(new Field<>(name, codec, getter, fallback, false));
  }

  /**
   * Adds an optional value field that is omitted when empty and defaults to empty when absent or null.
   *
   * @param name the literal compound-field name
   * @param codec converts the contained value
   * @param getter obtains the optional value from the encoded object
   * @param <V> the contained value type
   * @return a schema containing the optional field
   * @throws IllegalArgumentException if the schema already declares {@code name}
   */
  public <V> RecordCodec<T> optional(String name, Codec<V> codec, Function<T, Optional<V>> getter) {
    return append(new Field<>(name, codec.optional(), getter, Optional::empty, true));
  }

  /**
   * Creates a value codec from the declared fields and a constructor callback.
   *
   * @param constructor creates a value from the decoded field values
   * @return a codec that encodes this schema and constructs decoded values
   */
  public Codec<T> create(Function<Values, T> constructor) {
    return Codec.object(value -> {
      CompoundNBT data = new CompoundNBT();
      for (Field<T, ?> field : fields) {
        field.write(value, data);
      }
      return data;
    }, data -> {
      Map<String, Object> values = new LinkedHashMap<>();
      for (Field<T, ?> field : fields) {
        values.put(field.name, field.read(data));
      }
      return constructor.apply(new Values(values));
    });
  }

  private <V> RecordCodec<T> append(Field<T, V> field) {
    for (Field<T, ?> existing : fields) {
      if (existing.name.equals(field.name)) {
        throw new IllegalArgumentException("Duplicate codec field: " + field.name);
      }
    }
    List<Field<T, ?>> result = new ArrayList<>(fields);
    result.add(field);
    return new RecordCodec<>(result);
  }

  /** Decoded constructor arguments. A missing schema key is a programming error. */
  public static final class Values {
    private final Map<String, Object> values;

    private Values(Map<String, Object> values) {
      this.values = values;
    }

    /**
     * Returns a previously declared field value.
     *
     * @param name the literal field name
     * @param <V> the declared field value type
     * @return the value associated with {@code name}
     * @throws IllegalArgumentException if {@code name} was not declared in the schema
     */
    @SuppressWarnings("unchecked")
    public <V> V get(String name) {
      if (!values.containsKey(name)) {
        throw new IllegalArgumentException("Undeclared codec field: " + name);
      }
      return (V) values.get(name);
    }
  }

  private record Field<T, V>(String name, Codec<V> codec, Function<T, V> getter,
                             @Nullable Supplier<V> fallback, boolean omitEmpty) {
    V read(CompoundNBT data) {
      return data.directGet(name) == null && fallback != null ? fallback.get() : codec.readField(data, name);
    }

    void write(T object, CompoundNBT data) {
      V value = getter.apply(object);
      if (omitEmpty && value instanceof Optional<?> optional && optional.isEmpty()) {
        return;
      }
      codec.writeField(data, name, value);
    }
  }
}
