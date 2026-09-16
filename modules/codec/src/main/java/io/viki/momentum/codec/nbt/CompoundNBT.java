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

import java.util.*;

/**
 * NBT (Named Binary Tag) compound — a tree-structured key-value container with optional path-based access.
 *
 * <p>Similar in concept to a JSON object, this is the primary data structure for
 * NBT serialization. Keys are strings; values are type-safe {@link NBT} instances
 * ({@code ByteTag}, {@code ShortTag}, {@code IntTag}, {@code LongTag}, {@code FloatTag},
 * {@code DoubleTag}, {@code BooleanTag}, {@link StringNBT}, {@link ByteArrayNBT}), or nested
 * {@link CompoundNBT} / {@link ListNBT}.
 *
 * <h3>Path-based access</h3>
 * Keys starting with {@value #PATH_PREFIX} ({@code $}) are interpreted as dot-separated paths into nested compounds.
 * For example, {@code "$a.b.c"} accesses key {@code "c"} inside compound {@code "b"} inside compound {@code "a"}.
 * Intermediate compounds are automatically created on write.
 *
 * <p>To store a literal key starting with {@code $}, escape it with a second {@code $}:
 * {@code "$$foo"} stores (and retrieves) the literal key {@code "$foo"}.
 *
 * <p>This class is <strong>not</strong> thread-safe.
 *
 * @see ListNBT
 * @see DataType
 */
public final class CompoundNBT implements NBT, Iterable<Map.Entry<String, NBT>> {
  /**
   * Prefix character that enables dot-separated path traversal.
   *
   * <p>When a key starts with this character ({@code $}), it is interpreted
   * as a path like {@code "$a.b.c"} rather than a literal key. Intermediate {@link CompoundNBT} nodes are created
   * automatically on write.
   *
   * <p>To store a literal key starting with {@code $}, use {@code $$} prefix
   * (e.g. {@code "$$foo"} stores key "$foo").
   */
  public static final char PATH_PREFIX = '$';
  /**
   * Codec for compound values: the NBT tree form is the tag itself, and the
   * binary payload is a sequence of typed entries terminated by an end marker.
   */
  public static final Codec<CompoundNBT> CODEC = new Codec<>() {
    @Override
    public NBT serialize(CompoundNBT value) {
      return value;
    }

    @Override
    public CompoundNBT deserialize(NBT nbt) {
      return (CompoundNBT) nbt;
    }

    @Override
    public void serialize(CompoundNBT value, BinaryBuffer buffer) {
      for (Map.Entry<String, NBT> entry : value.entrySet()) {
        NBT child = entry.getValue();
        buffer.write(child.dataType().id());
        buffer.writeUTF8(entry.getKey());
        buffer.writeNBT(child);
      }
      buffer.write(DataType.END.id());
    }

    @Override
    public CompoundNBT deserialize(BinaryBuffer buffer) {
      CompoundNBT compound = new CompoundNBT();
      while (true) {
        DataType tagClass = DataType.fromID(buffer.read());
        if (tagClass == DataType.END) {
          break;
        }
        String key = buffer.readUTF8();
        NBT value = tagClass.codec().deserialize(buffer);
        CompoundNBT.putDirect(compound, key, value);
      }
      return compound;
    }
  };

  /**
   * Map from key to tag value. Uses LinkedHashMap to preserve insertion order
   * for deterministic serialization.
   */
  private final Map<String, NBT> map = new LinkedHashMap<>();
  private boolean modifiable = true;

  /**
   * Creates an empty NBT compound.
   */
  public CompoundNBT() {
  }

  /**
   * Creates a compound tag with the map content.
   *
   * @param map content source
   */
  public CompoundNBT(Map<String, NBT> map) {
    // Preserve the non-null invariant: normalize any external nulls to NullTag.
    this.map.putAll(map);
  }

  /**
   * Creates an unmodifiable compound tag from another tag.
   *
   * @param tag content source
   * @return an unmodifiable view of the given tag
   */
  public static CompoundNBT ofUnmodifiable(CompoundNBT tag) {
    CompoundNBT unmodifiable = new CompoundNBT(new LinkedHashMap<>(tag.map));
    unmodifiable.modifiable = false;
    return unmodifiable;
  }

  /**
   * Inserts a key/value pair without path resolution or {@code $} escaping.
   *
   * <p>Keys read from an external representation (binary wire format, JSON)
   * are already literal — {@link #put(String, NBT)} would re-parse keys that
   * start with {@code $} as paths and corrupt them. Non-null invariant applies.
   */
  static void putDirect(CompoundNBT target, String key, NBT value) {
    target.map.put(key, value);
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
  public Collection<NBT> values() {
    return Collections.unmodifiableCollection(map.values());
  }

  /**
   * Returns an unmodifiable set of the entries in this compound.
   *
   * @return a set of entries
   */
  public Set<Map.Entry<String, NBT>> entrySet() {
    return Collections.unmodifiableSet(map.entrySet());
  }

  /**
   * Checks if a key or path exists.
   *
   * @param key the key (may start with '$' for path, or '$$' for literal dollar)
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
   * @param key the key (may start with '$' for path, or '$$' for literal dollar)
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
      CompoundNBT parent = seekParent(key, false);
      if (parent != null) {
        parent.map.remove(lastKey);
      }
    } else {
      map.remove(key);
    }
  }

  /**
   * Stores a tag value. Supports path syntax with '$' prefix.
   * Intermediate compounds are automatically created for paths.
   * To store a literal key starting with '$', use '$$' prefix (e.g. "$$foo" stores key "$foo").
   *
   * @param key   the key (may start with '$' for path, or '$$' for literal dollar)
   * @param value the tag value to store, may be {@code null} (stored as {@link NullNBT})
   */
  public void put(String key, @Nullable NBT value) {
    if (!modifiable) {
      return;
    }

    NBT stored = value != null ? value : NullNBT.INSTANCE;

    if (isPather(key)) {
      String[] parts = key.substring(1).split("\\.");
      if (parts.length == 0) {
        return;
      }

      String lastKey = parts[parts.length - 1];
      CompoundNBT target = seekParent(key, true);
      if (target != null) {
        target.map.put(lastKey, stored);
      }
    } else {
      map.put(key, stored);
    }
  }

  /**
   * Stores a byte value.
   *
   * @param key   the key
   * @param value the byte value
   */
  public void putByte(String key, byte value) {
    put(key, new ByteNBT(value));
  }

  /**
   * Stores a short value.
   *
   * @param key   the key
   * @param value the short value
   */
  public void putShort(String key, short value) {
    put(key, new ShortNBT(value));
  }

  /**
   * Stores an integer value.
   *
   * @param key   the key
   * @param value the int value
   */
  public void putInt(String key, int value) {
    put(key, new IntNBT(value));
  }

  /**
   * Stores a long value.
   *
   * @param key   the key
   * @param value the long value
   */
  public void putLong(String key, long value) {
    put(key, new LongNBT(value));
  }

  /**
   * Stores a float value.
   *
   * @param key   the key
   * @param value the float value
   */
  public void putFloat(String key, float value) {
    put(key, new FloatNBT(value));
  }

  /**
   * Stores a double value.
   *
   * @param key   the key
   * @param value the double value
   */
  public void putDouble(String key, double value) {
    put(key, new DoubleNBT(value));
  }

  /**
   * Stores a boolean value.
   *
   * @param key   the key
   * @param value the boolean value
   */
  public void putBoolean(String key, boolean value) {
    put(key, new BooleanNBT(value));
  }

  /**
   * Stores a String value.
   *
   * @param key   the key
   * @param value the String value
   */
  public void putString(String key, String value) {
    put(key, new StringNBT(value));
  }

  /**
   * Stores a byte array value (cloned).
   *
   * @param key   the key
   * @param value the byte array
   */
  public void putBytes(String key, byte[] value) {
    put(key, new ByteArrayNBT(value));
  }

  /**
   * Stores a nested CompoundTag.
   *
   * @param key   the key
   * @param value the nested compound
   */
  public void putCompound(String key, CompoundNBT value) {
    put(key, value);
  }

  /**
   * Stores a nested ListTag.
   *
   * @param key   the key
   * @param value the nested list
   */
  public void putList(String key, ListNBT value) {
    put(key, value);
  }

  /**
   * Retrieves a tag value by key or path with unchecked type casting.
   *
   * @param key the key (may start with '$' for path, or '$$' for literal dollar)
   * @param <T> the expected Tag subtype
   * @return the tag, or null if not found
   */
  @SuppressWarnings("unchecked")
  public <T extends NBT> @Nullable T get(String key) {
    if (isPather(key)) {
      return (T) seekValue(key);
    }
    return (T) map.get(key);
  }

  /**
   * Retrieves a tag value by key or path with a fallback.
   *
   * @param key      the key (may start with '$' for path, or '$$' for literal dollar)
   * @param fallback the value to return if not found
   * @param <T>      the expected Tag subtype
   * @return the tag, or fallback if not found
   */
  public <T extends NBT> T get(String key, T fallback) {
    T value = get(key);
    return value != null ? value : fallback;
  }

  /**
   * Retrieves a tag value as Optional.
   *
   * @param key the key (may start with '$' for path, or '$$' for literal dollar)
   * @param <T> the expected Tag subtype
   * @return Optional containing the tag, or empty if not found
   */
  public <T extends NBT> Optional<T> tryGet(String key) {
    return Optional.ofNullable(get(key));
  }

  /**
   * Retrieves a numeric value as a {@code byte} via {@link NumericNBT},
   * converting across numeric types. {@link BooleanNBT} maps {@code true} → 1, {@code false} → 0.
   *
   * @param key      the key
   * @param fallback the value to return if not found or not numeric
   * @return the byte value, or fallback
   */
  public byte getByte(String key, byte fallback) {
    NBT v = get(key);
    if (v instanceof NumericNBT n) {
      return n.asByte();
    }
    if (v instanceof BooleanNBT b) {
      return (byte) (b.get() ? 1 : 0);
    }
    return fallback;
  }

  /**
   * Retrieves a numeric value as a {@code short} via {@link NumericNBT}.
   *
   * @param key      the key
   * @param fallback the value to return if not found or not numeric
   * @return the short value, or fallback
   */
  public short getShort(String key, short fallback) {
    NBT v = get(key);
    if (v instanceof NumericNBT n) {
      return n.asShort();
    }
    return fallback;
  }

  /**
   * Retrieves a numeric value as an {@code int} via {@link NumericNBT}.
   *
   * @param key      the key
   * @param fallback the value to return if not found or not numeric
   * @return the int value, or fallback
   */
  public int getInt(String key, int fallback) {
    NBT v = get(key);
    if (v instanceof NumericNBT n) {
      return n.asInt();
    }
    return fallback;
  }

  /**
   * Retrieves a numeric value as a {@code long} via {@link NumericNBT}.
   *
   * @param key      the key
   * @param fallback the value to return if not found or not numeric
   * @return the long value, or fallback
   */
  public long getLong(String key, long fallback) {
    NBT v = get(key);
    if (v instanceof NumericNBT n) {
      return n.asLong();
    }
    return fallback;
  }

  /**
   * Retrieves a numeric value as a {@code float} via {@link NumericNBT}.
   *
   * @param key      the key
   * @param fallback the value to return if not found or not numeric
   * @return the float value, or fallback
   */
  public float getFloat(String key, float fallback) {
    NBT v = get(key);
    if (v instanceof NumericNBT n) {
      return n.asFloat();
    }
    return fallback;
  }

  /**
   * Retrieves a numeric value as a {@code double} via {@link NumericNBT}.
   *
   * @param key      the key
   * @param fallback the value to return if not found or not numeric
   * @return the double value, or fallback
   */
  public double getDouble(String key, double fallback) {
    NBT v = get(key);
    if (v instanceof NumericNBT n) {
      return n.asDouble();
    }
    return fallback;
  }

  /**
   * Retrieves a boolean value. {@link BooleanNBT} is returned directly;
   * numeric tags map non-zero to {@code true}.
   *
   * @param key      the key
   * @param fallback the value to return if not found or type mismatch
   * @return the boolean value, or fallback
   */
  public boolean getBoolean(String key, boolean fallback) {
    NBT v = get(key);
    if (v instanceof BooleanNBT b) {
      return b.get();
    }
    if (v instanceof NumericNBT n) {
      return n.asLong() != 0;
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
    NBT v = get(key);
    return v instanceof StringNBT s ? s.get() : fallback;
  }

  /**
   * Retrieves a byte array value.
   *
   * @param key the key
   * @return the byte array, or null if not found
   */
  public byte @Nullable [] getBytes(String key) {
    NBT v = get(key);
    return v instanceof ByteArrayNBT b ? b.get() : null;
  }

  /**
   * Retrieves a nested CompoundTag.
   *
   * @param key the key
   * @return the nested compound, or null if not found
   */
  public @Nullable CompoundNBT getCompound(String key) {
    NBT v = get(key);
    return v instanceof CompoundNBT c ? c : null;
  }

  /**
   * Retrieves a nested ListTag.
   *
   * @param key the key
   * @return the nested list, or null if not found
   */
  public @Nullable ListNBT getList(String key) {
    NBT v = get(key);
    return v instanceof ListNBT l ? l : null;
  }

  @Override
  public DataType dataType() {
    return DataType.COMPOUND;
  }

  @Override
  public Object asObject() {
    return this;
  }

  /**
   * Creates a deep copy of this compound.
   *
   * @return a new CompoundTag with the same data
   */
  public CompoundNBT copy() {
    CompoundNBT copy = new CompoundNBT();

    for (Map.Entry<String, NBT> entry : map.entrySet()) {
      String key = entry.getKey();
      NBT value = entry.getValue();
      copy.map.put(key, value.copy());
    }
    return copy;
  }

  @Override
  public Iterator<Map.Entry<String, NBT>> iterator() {
    return map.entrySet().iterator();
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
    if (!(obj instanceof CompoundNBT other)) {
      return false;
    }
    return map.equals(other.map);
  }

  @Override
  public String toString() {
    return map.toString();
  }

  private @Nullable CompoundNBT seekParent(String path, boolean shouldCreate) {
    if (!path.startsWith(String.valueOf(PATH_PREFIX))) {
      return this;
    }

    String[] parts = path.substring(1).split("\\.");
    if (parts.length <= 1) {
      return this;
    }

    CompoundNBT current = this;
    for (int i = 0; i < parts.length - 1; i++) {
      String part = parts[i];
      NBT next = current.map.get(part);

      if (next == null) { // null lookup result == key absent (invariant above)
        if (shouldCreate) {
          next = new CompoundNBT();
          current.map.put(part, next);
        } else {
          return null;
        }
      }

      if (!(next instanceof CompoundNBT compound)) {
        return null;
      }
      current = compound;
    }
    return current;
  }

  private @Nullable NBT seekValue(String path) {
    if (!isPather(path)) {
      return map.get(path);
    }

    String[] parts = path.substring(1).split("\\.");
    if (parts.length == 0) {
      return null;
    }

    NBT current = this;
    for (String part : parts) {
      if (current instanceof CompoundNBT compound) {
        current = compound.map.get(part);
      } else {
        return null;
      }
    }
    return current;
  }
}