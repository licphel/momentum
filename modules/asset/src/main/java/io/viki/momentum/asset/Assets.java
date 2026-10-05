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

package io.viki.momentum.asset;

import io.viki.momentum.logging.Log;
import io.viki.momentum.logging.Logger;
import io.viki.momentum.util.Identifier;
import io.viki.momentum.util.Namespace;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global, thread-safe asset store indexed by identifier and resource type.
 *
 * <p>Each identifier can hold several independently reloadable resources. Resource values use
 * exact class keys; register interface types explicitly with {@link #set(Identifier, Class, Object)}.
 * References read the shared per-identifier resource map and listeners run on the updating thread.
 * Removal closes every stored resource that implements {@link AutoCloseable}.
 */
public final class Assets {
  private static final Logger LOGGER = Log.getLogger();
  private static final ConcurrentHashMap<Identifier, Entry> STORE = new ConcurrentHashMap<>();

  private Assets() {
  }

  /**
   * Stores an asset under its runtime class.
   *
   * @param id asset identifier
   * @param value asset value
   */
  @SuppressWarnings("unchecked")
  public static void set(Identifier id, Object value) {
    set(id, (Class<Object>) value.getClass(), value);
  }

  /**
   * Stores an asset under an explicit class key, updating only references for that type.
   *
   * @param id asset identifier
   * @param type resource type used for storage and lookup
   * @param value asset value
   * @param <T> resource type
   */
  public static <T> void set(Identifier id, Class<T> type, T value) {
    ref(id, type).set(value);
  }

  /**
   * Returns the live reference for an identifier and exact type.
   *
   * @param id asset identifier
   * @param type resource type
   * @param <T> resource type
   * @return shared reference updated when this identifier and type are set
   */
  @SuppressWarnings("unchecked")
  public static <T> Ref<T> ref(Identifier id, Class<T> type) {
    Entry entry = STORE.computeIfAbsent(id, k -> new Entry());
    return (Ref<T>) entry.references.computeIfAbsent(type, k -> new Ref<>(id, type, entry.values));
  }

  /**
   * Returns a resource, its registered type fallback, or null when neither exists.
   *
   * @param id asset identifier
   * @param type resource type
   * @param <T> resource type
   * @return stored asset, fallback, or null
   */
  @SuppressWarnings("all")
  public static <T> @Nullable T getOrDefault(Identifier id, Class<T> type) {
    Entry entry = STORE.get(id);
    if (entry != null) {
      T value = type.cast(entry.values.getOrDefault(type, null));
      if (value != null) {
        return value;
      }
    }
    return AssetFallback.contains(type) ? AssetFallback.get(type) : null;
  }

  /**
   * Reports whether the identifier has a resource entry or a requested reference.
   *
   * @param id asset identifier
   * @return whether the identifier exists in the store
   */
  public static boolean contains(Identifier id) {
    return STORE.containsKey(id);
  }

  /**
   * Reports whether a value is stored for the exact type.
   *
   * @param id asset identifier
   * @param type resource type
   * @return whether a value is registered for this identifier and type
   */
  public static boolean contains(Identifier id, Class<?> type) {
    Entry entry = STORE.get(id);
    return entry != null && entry.values.containsKey(type);
  }

  /**
   * Returns the live view of registered identifiers.
   *
   * @return identifiers backed by the store
   */
  public static Set<Identifier> keys() {
    return STORE.keySet();
  }

  /**
   * Returns snapshots of the resource values grouped by identifier and exact class.
   *
   * @return resource maps independent of subsequent store changes
   */
  public static Map<Identifier, Map<Class<?>, Object>> entries() {
    Map<Identifier, Map<Class<?>, Object>> snapshot = new HashMap<>();
    STORE.forEach((id, entry) -> {
      Map<Class<?>, Object> values = new HashMap<>(entry.values);
      if (!values.isEmpty()) {
        snapshot.put(id, values);
      }
    });
    return snapshot;
  }

  /**
   * Returns the number of registered identifiers.
   *
   * @return identifier count
   */
  public static int size() {
    return STORE.size();
  }

  /**
   * Removes and closes one resource type while preserving the identifier's other types.
   *
   * @param id asset identifier
   * @param type resource type to remove
   */
  public static void remove(Identifier id, Class<?> type) {
    Entry entry = STORE.get(id);
    if (entry != null) {
      dispose(entry.values.remove(type));
    }
  }

  /**
   * Removes the identifier and closes all resource types registered under it.
   *
   * @param id asset identifier
   */
  public static void remove(Identifier id) {
    Entry entry = STORE.remove(id);
    if (entry != null) {
      Map<Class<?>, Object> values = new HashMap<>(entry.values);
      entry.values.clear();
      values.values().forEach(Assets::dispose);
    }
  }

  /**
   * Removes and closes every resource belonging to a namespace.
   *
   * @param namespace namespace to clear
   */
  public static void clearNamespace(Namespace namespace) {
    for (Identifier id : STORE.keySet().toArray(new Identifier[0])) {
      if (id.namespace().equals(namespace)) {
        remove(id);
      }
    }
  }

  /**
   * Removes every identifier and closes all stored resources.
   */
  public static void clear() {
    for (Identifier id : STORE.keySet().toArray(new Identifier[0])) {
      remove(id);
    }
  }

  private static void dispose(@Nullable Object value) {
    if (value instanceof AutoCloseable closeable) {
      try {
        closeable.close();
      } catch (Exception e) {
        LOGGER.warn("Failed to dispose asset", e);
      }
    }
  }

  private static final class Entry {
    private final Map<Class<?>, @Nullable Object> values = new ConcurrentHashMap<>();
    private final Map<Class<?>, Ref<?>> references = new ConcurrentHashMap<>();
  }
}
