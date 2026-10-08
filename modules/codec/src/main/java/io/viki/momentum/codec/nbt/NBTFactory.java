package io.viki.momentum.codec.nbt;

import io.viki.momentum.codec.nbt.primitives.BooleanNBT;
import io.viki.momentum.codec.nbt.primitives.ByteArrayNBT;
import io.viki.momentum.codec.nbt.primitives.NullNBT;
import io.viki.momentum.codec.nbt.primitives.StringNBT;
import io.viki.momentum.util.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;

/**
 * Converts NBT values to Java values using extensible readers for exact target types.
 *
 * <p>Registered readers take precedence over returning compatible tags by reference and
 * converting strings to enum constants. Default values are used only for absent or null
 * tags; malformed present values fail conversion. Supplied defaults and compatible tags
 * are returned without copying, while built-in array and collection conversions produce
 * new containers.
 *
 * <p>Registration is not thread-safe. Configure an instance before safely publishing it
 * for concurrent reads, and do not register readers while it is shared. Concurrent
 * conversions also require thread-safe readers and source tags that are not mutated.
 */
public final class NBTFactory {
  private final Map<Class<?>, BiFunction<NBT, NBTFactory, ?>> readers = new HashMap<>();

  /**
   * Creates a factory with readers for strings, booleans, numeric values,
   * {@link Identifier} values, and byte, int, long, float, and double arrays.
   *
   * <p>Primitive boolean and numeric target types share the corresponding boxed-type
   * conversions. Numeric conversions may narrow values or lose precision. Array readers
   * accept lists of convertible elements; the byte-array reader also accepts
   * {@link ByteArrayNBT} and returns a copy of its contents.
   */
  public NBTFactory() {
    registerBuiltinTypes();
  }

  void registerBuiltinTypes() {
    register(String.class, (tag, factory) -> ((StringNBT) tag).get());
    register(Boolean.class, (tag, factory) -> ((BooleanNBT) tag).get());
    register(Byte.class, (tag, factory) -> ((NumericNBT) tag).asByte());
    register(Short.class, (tag, factory) -> ((NumericNBT) tag).asShort());
    register(Integer.class, (tag, factory) -> ((NumericNBT) tag).asInt());
    register(Long.class, (tag, factory) -> ((NumericNBT) tag).asLong());
    register(Float.class, (tag, factory) -> ((NumericNBT) tag).asFloat());
    register(Double.class, (tag, factory) -> ((NumericNBT) tag).asDouble());
    register(Identifier.class, (tag, factory) -> Identifier.of(factory.supply(tag, String.class)));
    register(float[].class, (tag, factory) -> {
      ListNBT list = (ListNBT) tag;
      float[] result = new float[list.size()];
      for (int i = 0; i < result.length; i++) {
        result[i] = list.getFloat(i, 0);
      }
      return result;
    });
    register(double[].class, (tag, factory) -> {
      ListNBT list = (ListNBT) tag;
      double[] result = new double[list.size()];
      for (int i = 0; i < result.length; i++) {
        result[i] = list.getDouble(i, 0);
      }
      return result;
    });
    register(int[].class, (tag, factory) -> {
      ListNBT list = (ListNBT) tag;
      int[] result = new int[list.size()];
      for (int i = 0; i < result.length; i++) {
        result[i] = list.getInt(i, 0);
      }
      return result;
    });
    register(long[].class, (tag, factory) -> {
      ListNBT list = (ListNBT) tag;
      long[] result = new long[list.size()];
      for (int i = 0; i < result.length; i++) {
        result[i] = list.getLong(i, 0);
      }
      return result;
    });
    register(byte[].class, (tag, factory) -> {
      if (tag instanceof ByteArrayNBT bytes) {
        return bytes.get();
      }
      ListNBT list = (ListNBT) tag;
      byte[] result = new byte[list.size()];
      for (int i = 0; i < result.length; i++) {
        result[i] = list.getByte(i, (byte) 0);
      }
      return result;
    });

    // Simply copy these primitive classes.
    readers.put(boolean.class, readers.get(Boolean.class));
    readers.put(byte.class, readers.get(Byte.class));
    readers.put(short.class, readers.get(Short.class));
    readers.put(int.class, readers.get(Integer.class));
    readers.put(long.class, readers.get(Long.class));
    readers.put(float.class, readers.get(Float.class));
    readers.put(double.class, readers.get(Double.class));
  }

  /**
   * Adds a reader for an exact target type without replacing an existing reader.
   *
   * <p>The reader receives this factory for nested conversions and must return a
   * non-null value. Registering a boxed type does not register its primitive counterpart.
   *
   * @param <T>    the converted value type
   * @param type   the exact target type handled by the reader
   * @param reader the conversion function, which may reject malformed tags
   * @throws IllegalArgumentException if a reader is already registered for {@code type}
   */
  public <T> void register(Class<T> type, BiFunction<NBT, NBTFactory, ? extends T> reader) {
    if (readers.putIfAbsent(type, reader) != null) {
      throw new IllegalArgumentException("NBT reader is already registered for " + type.getName());
    }
  }

  /**
   * Converts a present tag or returns the supplied default for an absent or null tag.
   *
   * <p>Malformed present values fail as described by {@link #supply(NBT, Class)};
   * conversion failures do not select the default.
   *
   * @param <T>          the requested value type
   * @param tag          the source tag, or {@code null} if absent
   * @param type         the target type
   * @param defaultValue the default returned as-is for {@code null} or {@link NullNBT}
   * @return the converted value, or {@code defaultValue} if the tag is absent or null
   * @throws IllegalArgumentException if a present tag cannot be converted to {@code type}
   */
  public <T> T supply(@Nullable NBT tag, Class<T> type, T defaultValue) {
    return tag == null || tag instanceof NullNBT ? defaultValue : supply(tag, type);
  }

  /**
   * Converts a required tag to the requested type, rejecting absent and explicit null values.
   *
   * <p>A reader registered for the exact target type takes precedence. Otherwise,
   * a tag already assignable to that type is returned by reference. Enum targets
   * accept string tags whose names are uppercased independently of the default locale
   * before matching an enum constant. Built-in numeric readers accept numeric tags,
   * while the boolean reader requires a boolean tag.
   *
   * @param <T>  the requested value type
   * @param tag  the source tag, or null if the required value is absent
   * @param type the target type
   * @return the converted value, or the original tag if it is compatible and no reader exists
   * @throws IllegalArgumentException if the tag is {@link NullNBT}, no conversion is
   *                                  available, or conversion fails; reader failures are retained as the cause
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public <T> T supply(@Nullable NBT tag, Class<T> type) {
    if (tag == null || tag instanceof NullNBT) {
      throw new IllegalArgumentException("Missing NBT value for " + type.getName());
    }
    BiFunction<NBT, NBTFactory, ?> reader = readers.get(type);
    try {
      if (reader != null) {
        return (T) reader.apply(tag, this);
      }
      if (type.isInstance(tag)) {
        return type.cast(tag);
      }
      if (type.isEnum()) {
        return (T) Enum.valueOf((Class) type, supply(tag, String.class).toUpperCase(Locale.ROOT));
      }
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Cannot convert " + tag.dataType() + " to " + type.getName()
          + ": " + exception.getMessage(), exception);
    }
    throw new IllegalArgumentException("No NBT reader registered for " + type.getName());
  }

  /**
   * Converts a list tag to a new mutable list, or returns the default for an absent or null tag.
   *
   * <p>Elements retain their source order and are converted using {@link #supply(NBT, Class)}.
   * Explicit null elements fail conversion. Element failures identify the source index;
   * neither a malformed list nor a malformed element selects the default.
   *
   * @param <T>          the converted element type
   * @param tag          the source list tag, or {@code null} if absent
   * @param elementType  the target type for each element
   * @param defaultValue the list returned as-is for {@code null} or {@link NullNBT}
   * @return a new list of converted elements, or {@code defaultValue} if absent or null
   * @throws IllegalArgumentException if a present tag is not convertible to {@link ListNBT}
   *                                  or an element cannot be converted to {@code elementType}
   */
  public <T> List<T> supplyList(@Nullable NBT tag, Class<T> elementType, List<T> defaultValue) {
    if (tag == null || tag instanceof NullNBT) {
      return defaultValue;
    }
    ListNBT list = supply(tag, ListNBT.class);
    List<T> result = new ArrayList<>(list.size());
    for (int i = 0; i < list.size(); i++) {
      try {
        result.add(supply(list.get(i), elementType));
      } catch (IllegalArgumentException exception) {
        throw new IllegalArgumentException("NBT list element [" + i + "]: " + exception.getMessage(), exception);
      }
    }
    return result;
  }

  /**
   * Converts a compound tag to a new mutable map, or returns the default for an absent or null tag.
   *
   * <p>Keys remain literal and retain their source iteration order. Values are converted
   * using {@link #supply(NBT, Class)}. Explicit null values fail conversion. Value failures
   * identify the source key; malformed compounds and values do not select the default.
   *
   * @param <T>          the converted value type
   * @param tag          the source compound tag, or {@code null} if absent
   * @param valueType    the target type for each value
   * @param defaultValue the map returned as-is for {@code null} or {@link NullNBT}
   * @return a new map of converted values, or {@code defaultValue} if absent or null
   * @throws IllegalArgumentException if a present tag is not convertible to {@link CompoundNBT}
   *                                  or a value cannot be converted to {@code valueType}
   */
  public <T> Map<String, T> supplyMap(@Nullable NBT tag, Class<T> valueType, Map<String, T> defaultValue) {
    if (tag == null || tag instanceof NullNBT) {
      return defaultValue;
    }
    CompoundNBT data = supply(tag, CompoundNBT.class);
    Map<String, T> result = new LinkedHashMap<>();
    for (Map.Entry<String, NBT> entry : data) {
      try {
        result.put(entry.getKey(), supply(entry.getValue(), valueType));
      } catch (IllegalArgumentException exception) {
        throw new IllegalArgumentException("NBT field '" + entry.getKey() + "': " + exception.getMessage(), exception);
      }
    }
    return result;
  }
}
