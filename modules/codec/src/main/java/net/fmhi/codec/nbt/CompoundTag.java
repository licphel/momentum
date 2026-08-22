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

import net.fmhi.codec.streaming.CursorBuffer;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * NBT (Named Binary Tag) compound — a tree-structured key-value container with optional path-based access.
 *
 * <p>Similar in concept to a JSON object, this is the primary data structure for
 * NBT serialization. Keys are strings; values may be primitives ({@code byte}, {@code short}, {@code int},
 * {@code long}, {@code float}, {@code double}, {@code boolean}, {@link String}, {@code byte[]}), or nested
 * {@link CompoundTag} / {@link ListTag}.
 *
 * <h3>Path-based access</h3>
 * Keys starting with {@value #PATH_PREFIX} ({@code $}) are interpreted as dot-separated paths into nested compounds.
 * For example, {@code "$a.b.c"} accesses key {@code "c"} inside compound {@code "b"} inside compound {@code "a"}.
 * Intermediate compounds are automatically created on write.
 *
 * <p>This class is <strong>not</strong> thread-safe.</p>
 *
 * @see ListTag
 * @see TagMark
 */
public final class CompoundTag implements Iterable<Map.Entry<String, @Nullable Object>> {
  /**
   * Prefix character that enables dot-separated path traversal.
   *
   * <p>When a key starts with this character ({@code $}), it is interpreted
   * as a path like {@code "$a.b.c"} rather than a literal key. Intermediate {@link CompoundTag} nodes are created
   * automatically on write.
   */
  public static final char PATH_PREFIX = '$';

  private final Map<String, @Nullable Object> map = new HashMap<>();
  private boolean modifiable = true;

  /**
   * Creates an empty NBT compound.
   */
  public CompoundTag() {
  }

  /**
   * Creates a compound tag with the map content.
   *
   * @param map content source
   */
  public CompoundTag(Map<String, @Nullable Object> map) {
    this.map.putAll(map);
  }

  /**
   * Creates an unmodifiable compound tag from another tag.
   *
   * @param tag content source
   * @return an unmodifiable view of the given tag
   */
  public static CompoundTag ofUnmodifiable(CompoundTag tag) {
    CompoundTag unmodifiable = new CompoundTag(tag.map);
    unmodifiable.modifiable = false;
    return unmodifiable;
  }

  /**
   * Deserializes an NBT compound from a binary buffer.
   *
   * <p>The wire format reads entries sequentially until a {@link TagMark#END} tag
   * is encountered. Each entry consists of a 1-byte type tag ({@link TagMark#id()}), a length-prefixed UTF-8 key, and
   * the
   * value encoded according to its type.
   *
   * @param buffer the source buffer positioned at the start of the compound data
   * @return the deserialized compound
   * @throws IndexOutOfBoundsException if {@code buffer} has insufficient readable bytes
   */
  public static CompoundTag deserialize(CursorBuffer buffer) {
    CompoundTag compound = new CompoundTag();

    while (true) {
      TagMark tagClass = TagMark.fromID(buffer.read());
      if (tagClass == TagMark.END) {
        break;
      }

      String key = buffer.readUTF8();
      Object value = readValue(buffer, tagClass);
      compound.put(key, value);
    }

    return compound;
  }

  /**
   * Writes a single value to the buffer according to its runtime type.
   *
   * <p>Dispatches to the correct primitive or compound serialization method
   * based on {@code instanceof} checks. {@code null} values are silently skipped.
   *
   * @param buffer the destination buffer
   * @param value  the value to write, may be {@code null}
   * @throws IllegalArgumentException if the value's type is not a supported NBT type
   */
  public static void writeValue(CursorBuffer buffer, @Nullable Object value) {
    if (value == null) {
      return;
    }

    switch (value) {
      case Byte b -> buffer.write(b);
      case Boolean b -> buffer.write((byte) (b ? 1 : 0));
      case Short s -> buffer.writeShort(s);
      case Integer i -> buffer.writeInt(i);
      case Long l -> buffer.writeLong(l);
      case Float f -> buffer.writeFloat(f);
      case Double d -> buffer.writeDouble(d);
      case String s -> buffer.writeUTF8(s);
      case byte[] bytes -> {
        buffer.writeInt(bytes.length);
        buffer.writeBytes(bytes);
      }
      case CompoundTag compound -> compound.serialize(buffer);
      case ListTag list -> list.serialize(buffer);
      default -> throw new IllegalArgumentException("Unsupported NBT type: " + value.getClass());
    }
  }

  /**
   * Reads a single value from the buffer based on its tag type.
   *
   * <p>Returns {@code null} for {@link TagMark#END}, {@link TagMark#NULL}, and any
   * unrecognized tag — the caller is responsible for handling these cases.
   *
   * @param buffer  the source buffer
   * @param tagType the NBT tag type that identifies how to decode the value
   * @return the decoded value, or {@code null} if the tag carries no data
   */
  public static @Nullable Object readValue(CursorBuffer buffer, TagMark tagType) {
    return switch (tagType) {
      case BYTE -> buffer.read();
      case BOOLEAN -> buffer.read() != 0;
      case SHORT -> buffer.readShort();
      case INT -> buffer.readInt();
      case LONG -> buffer.readLong();
      case FLOAT -> buffer.readFloat();
      case DOUBLE -> buffer.readDouble();
      case STRING -> buffer.readUTF8();
      case BYTE_ARRAY -> buffer.readBytes(buffer.readInt());
      case LIST -> ListTag.deserialize(buffer);
      case COMPOUND -> deserialize(buffer);
      default -> null;
    };
  }

  private static boolean isPather(String key) {
    return !key.isEmpty() && key.charAt(0) == PATH_PREFIX;
  }

  /**
   * Returns the number of key-value pairs in this compound.
   *
   * @return the size of this compound
   */
  public int size() {
    return map.size();
  }

  /**
   * Returns whether this compound contains no key-value pairs.
   *
   * @return true if empty, false otherwise
   */
  public boolean isEmpty() {
    return map.isEmpty();
  }

  /**
   * Removes all key-value pairs from this compound.
   */
  public void clear() {
    map.clear();
  }

  /**
   * Returns an unmodifiable view of the underlying map.
   *
   * <p>Note: This view does NOT support path-based access. Use the compound's
   * own methods for path operations.
   *
   * @return an unmodifiable map view
   */
  public Map<String, ?> mapped() {
    return Collections.unmodifiableMap(map);
  }

  /**
   * Returns an unmodifiable set of the keys in this compound.
   *
   * @return a set of keys
   */
  public Set<String> keySet() {
    return Collections.unmodifiableSet(map.keySet());
  }

  /**
   * Returns an unmodifiable collection of the values in this compound.
   *
   * @return a collection of values
   */
  public Collection<@Nullable Object> values() {
    return Collections.unmodifiableCollection(map.values());
  }

  /**
   * Returns an unmodifiable set of the entries in this compound.
   *
   * @return a set of entries
   */
  public Set<Map.Entry<String, @Nullable Object>> entrySet() {
    return Collections.unmodifiableSet(map.entrySet());
  }

  /**
   * Checks if a key or path exists.
   *
   * @param key the key (may start with '$' for path)
   * @return true if exists, false otherwise
   */
  public boolean contains(String key) {
    if (isPather(key)) {
      return seekValue(key) != null;
    }
    return map.containsKey(key);
  }

  /**
   * Removes a key or path.
   *
   * @param key the key (may start with '$' for path)
   */
  public void remove(String key) {
    if (!modifiable) {
      return;
    }

    if (isPather(key)) {
      String[] parts = key.substring(1).split("\\.");
      if (parts.length == 0) {
        return;
      }

      String lastKey = parts[parts.length - 1];
      CompoundTag parent = seekParent(key, false);
      if (parent != null) {
        parent.map.remove(lastKey);
      }
    } else {
      map.remove(key);
    }
  }

  /**
   * Stores a value. Supports path syntax with '$' prefix. Intermediate compounds are automatically created for paths.
   *
   * @param key   the key (may start with '$' for path)
   * @param value the value to store
   * @throws IllegalArgumentException if the value type is not supported
   */
  public void put(String key, @Nullable Object value) {
    if (!modifiable) {
      return;
    }

    TagMark.validate(value);

    if (isPather(key)) {
      String[] parts = key.substring(1).split("\\.");
      if (parts.length == 0) {
        return;
      }

      String lastKey = parts[parts.length - 1];
      CompoundTag target = seekParent(key, true);
      if (target != null) {
        target.map.put(lastKey, value);
      }
    } else {
      map.put(key, value);
    }
  }

  /**
   * Stores a byte value.
   *
   * @param key   the key
   * @param value the value
   */
  public void putByte(String key, byte value) {
    put(key, value);
  }

  /**
   * Stores a short value.
   *
   * @param key   the key
   * @param value the value
   */
  public void putShort(String key, short value) {
    put(key, value);
  }

  /**
   * Stores an integer value.
   *
   * @param key   the key
   * @param value the value
   */
  public void putInt(String key, int value) {
    put(key, value);
  }

  /**
   * Stores a long value.
   *
   * @param key   the key
   * @param value the value
   */
  public void putLong(String key, long value) {
    put(key, value);
  }

  /**
   * Stores a float value.
   *
   * @param key   the key
   * @param value the value
   */
  public void putFloat(String key, float value) {
    put(key, value);
  }

  /**
   * Stores a double value.
   *
   * @param key   the key
   * @param value the value
   */
  public void putDouble(String key, double value) {
    put(key, value);
  }

  /**
   * Stores a boolean value.
   *
   * @param key   the key
   * @param value the value
   */
  public void putBoolean(String key, boolean value) {
    put(key, value);
  }

  /**
   * Stores a String value.
   *
   * @param key   the key
   * @param value the value
   */
  public void putString(String key, String value) {
    put(key, value);
  }

  /**
   * Stores a byte array value (copied).
   *
   * @param key   the key
   * @param value the byte array
   */
  public void putBytes(String key, byte[] value) {
    put(key, value.clone());
  }

  /**
   * Stores a nested CompoundTag.
   *
   * @param key   the key
   * @param value the nested compound
   */
  public void putCompound(String key, CompoundTag value) {
    put(key, value);
  }

  /**
   * Stores a nested ListTag.
   *
   * @param key   the key
   * @param value the nested list
   */
  public void putList(String key, ListTag value) {
    put(key, value);
  }

  /**
   * Retrieves a value by key or path with unchecked type casting.
   *
   * @param key the key (may start with '$' for path)
   * @param <T> the expected type
   * @return the value, or null if not found
   */
  @SuppressWarnings("unchecked")
  public <T> @Nullable T get(String key) {
    if (isPather(key)) {
      return (T) seekValue(key);
    }
    return (T) map.get(key);
  }

  /**
   * Retrieves a value by key or path with a fallback.
   *
   * @param key      the key (may start with '$' for path)
   * @param fallback the value to return if not found
   * @param <T>      the expected type
   * @return the value, or fallback if not found
   */
  @SuppressWarnings("unchecked")
  public <T> T get(String key, T fallback) {
    Object value = get(key);
    return value != null ? (T) value : fallback;
  }

  /**
   * Retrieves a value as Optional.
   *
   * @param key the key (may start with '$' for path)
   * @param <T> the expected type
   * @return Optional containing the value, or empty if not found
   */
  public <T> Optional<T> tryGet(String key) {
    return Optional.ofNullable(get(key));
  }

  /**
   * Retrieves a byte value.
   *
   * @param key      the key
   * @param fallback the value to return if not found or type mismatch
   * @return the byte value, or fallback
   */
  public byte getByte(String key, byte fallback) {
    Object v = get(key);
    if (v instanceof Number) {
      return ((Number) v).byteValue();
    }
    if (v instanceof Boolean) {
      return (byte) ((boolean) v ? 1 : 0);
    }
    return fallback;
  }

  /**
   * Retrieves a short value.
   *
   * @param key      the key
   * @param fallback the value to return if not found or type mismatch
   * @return the short value, or fallback
   */
  public short getShort(String key, short fallback) {
    Object v = get(key);
    if (v instanceof Number) {
      return ((Number) v).shortValue();
    }
    return fallback;
  }

  /**
   * Retrieves an integer value.
   *
   * @param key      the key
   * @param fallback the value to return if not found or type mismatch
   * @return the int value, or fallback
   */
  public int getInt(String key, int fallback) {
    Object v = get(key);
    if (v instanceof Number) {
      return ((Number) v).intValue();
    }
    return fallback;
  }

  /**
   * Retrieves a long value.
   *
   * @param key      the key
   * @param fallback the value to return if not found or type mismatch
   * @return the long value, or fallback
   */
  public long getLong(String key, long fallback) {
    Object v = get(key);
    if (v instanceof Number) {
      return ((Number) v).longValue();
    }
    return fallback;
  }

  /**
   * Retrieves a float value.
   *
   * @param key      the key
   * @param fallback the value to return if not found or type mismatch
   * @return the float value, or fallback
   */
  public float getFloat(String key, float fallback) {
    Object v = get(key);
    if (v instanceof Number) {
      return ((Number) v).floatValue();
    }
    return fallback;
  }

  /**
   * Retrieves a double value.
   *
   * @param key      the key
   * @param fallback the value to return if not found or type mismatch
   * @return the double value, or fallback
   */
  public double getDouble(String key, double fallback) {
    Object v = get(key);
    if (v instanceof Number) {
      return ((Number) v).doubleValue();
    }
    return fallback;
  }

  /**
   * Retrieves a boolean value.
   *
   * @param key      the key
   * @param fallback the value to return if not found or type mismatch
   * @return the boolean value, or fallback
   */
  public boolean getBoolean(String key, boolean fallback) {
    Object v = get(key);
    if (v instanceof Boolean) {
      return (boolean) v;
    }
    if (v instanceof Number) {
      return ((Number) v).intValue() != 0;
    }
    return fallback;
  }

  /**
   * Retrieves a String value.
   *
   * @param key      the key
   * @param fallback the value to return if not found or type mismatch
   * @return the String value, or fallback
   */
  public String getString(String key, String fallback) {
    Object v = get(key);
    return v instanceof String ? (String) v : fallback;
  }

  /**
   * Retrieves a byte array value.
   *
   * @param key the key
   * @return the byte array, or null if not found
   */
  public byte @Nullable [] getBytes(String key) {
    Object v = get(key);
    return v instanceof byte[] ? (byte[]) v : null;
  }

  /**
   * Retrieves a nested CompoundTag.
   *
   * @param key the key
   * @return the nested compound, or null if not found
   */
  public @Nullable CompoundTag getCompound(String key) {
    Object v = get(key);
    return v instanceof CompoundTag ? (CompoundTag) v : null;
  }

  /**
   * Retrieves a nested ListTag.
   *
   * @param key the key
   * @return the nested list, or null if not found
   */
  public @Nullable ListTag getList(String key) {
    Object v = get(key);
    return v instanceof ListTag ? (ListTag) v : null;
  }

  private @Nullable CompoundTag seekParent(String path, boolean shouldCreate) {
    if (!path.startsWith(String.valueOf(PATH_PREFIX))) {
      return this;
    }

    String[] parts = path.substring(1).split("\\.");
    if (parts.length <= 1) {
      return this;
    }

    CompoundTag current = this;
    for (int i = 0; i < parts.length - 1; i++) {
      String part = parts[i];
      Object next = current.map.get(part);

      if (next == null) {
        if (shouldCreate) {
          next = new CompoundTag();
          current.map.put(part, next);
        } else {
          return null;
        }
      }

      if (!(next instanceof CompoundTag)) {
        return null;
      }
      current = (CompoundTag) next;
    }
    return current;
  }

  private @Nullable Object seekValue(String path) {
    if (!isPather(path)) {
      return map.get(path);
    }

    String[] parts = path.substring(1).split("\\.");
    if (parts.length == 0) {
      return null;
    }

    Object current = this;
    for (String part : parts) {
      if (current instanceof CompoundTag compound) {
        current = compound.map.get(part);
      } else {
        return null;
      }
    }
    return current;
  }

  /**
   * Creates a deep copy of this compound.
   *
   * @return a new CompoundTag with the same data
   */
  public CompoundTag copy() {
    CompoundTag copy = new CompoundTag();

    for (Map.Entry<String, @Nullable Object> entry : map.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      switch (value) {
        case CompoundTag entries -> copy.putCompound(key, entries.copy());
        case ListTag objects -> copy.putList(key, objects.copy());
        case byte[] bytes -> copy.putBytes(key, bytes);
        case null, default -> copy.put(key, value);
      }
    }
    return copy;
  }

  @Override
  public Iterator<Map.Entry<String, @Nullable Object>> iterator() {
    return new ArrayList<>(map.entrySet()).iterator();
  }

  @Override
  public int hashCode() {
    return map.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof CompoundTag other)) {
      return false;
    }
    return map.equals(other.map);
  }

  @Override
  public String toString() {
    return map.toString();
  }

  /**
   * Serializes this NBT compound to a byte buffer. Format: for each entry -> [tag_type(byte)][name][value] Ends with
   * TAG_END(0)
   *
   * @param buffer the destination buffer
   */
  public void serialize(CursorBuffer buffer) {
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      TagMark tagType = TagMark.fromValue(value);
      buffer.write(tagType.id());
      buffer.writeUTF8(key);
      writeValue(buffer, value);
    }
    buffer.write(TagMark.END.id());
  }
}