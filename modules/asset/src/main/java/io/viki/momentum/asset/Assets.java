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

import io.viki.momentum.util.Identifier;
import io.viki.momentum.util.Namespace;
import io.viki.momentum.logging.Log;
import io.viki.momentum.logging.Logger;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global, thread-safe asset store keyed by {@link Identifier}.
 *
 * <p>Assets are stored in {@link Ref} containers that support hot-reloading.
 * When {@link #set(Identifier, Object)} is called with a new value for an
 * existing identifier, all {@code Ref} instances for that identifier
 * automatically reflect the update. Code that needs to observe asset
 * changes should call {@link #ref(Identifier, Class)} and hold the
 * returned {@code Ref}, reading from it each time the value is needed.
 *
 * <p>Values that implement {@link AutoCloseable} are closed when removed
 * via {@link #remove(Identifier)} or when the store is cleared.
 *
 * <p>Assets are loaded by {@link Loader} instances and consumed
 * anywhere in the engine.
 */
public final class Assets {
  private static final Logger LOGGER = Log.getLogger();

  private static final ConcurrentHashMap<Identifier, Ref<?>> STORE = new ConcurrentHashMap<>();

  private Assets() {
  }

  /**
   * Stores an asset under the given identifier.
   *
   * <p>If a {@link Ref} already exists for the identifier, its value is
   * updated in place and all registered listeners are notified — this is
   * the mechanism that enables hot-reloading. The value is verified against
   * the type of the previous value; a mismatch throws
   * {@link IllegalArgumentException} and leaves the store unchanged.
   *
   * @param id    the asset identifier
   * @param value the asset value
   */
  @SuppressWarnings("unchecked")
  public static void set(Identifier id, Object value) {
    @Nullable Ref<?>[] existing = new Ref<?>[1];
    STORE.compute(id, (k, ref) -> {
      if (ref == null) {
        return new Ref<>(id, value);
      }
      existing[0] = ref;
      return ref;
    });
    if (existing[0] != null) {
      ((Ref<Object>) existing[0]).set(value);
    }
  }

  /**
   * Retrieves the {@link Ref} for the given identifier.
   *
   * <p>The returned {@code Ref} is a live view — if the asset is later
   * updated via {@link #set(Identifier, Object)}, the ref's value changes
   * accordingly.
   *
   * @param id   the asset identifier
   * @param type the expected asset type
   * @param <T>  the expected asset type
   * @return the ref
   */
  @SuppressWarnings("unchecked")
  public static <T> Ref<T> ref(Identifier id, Class<T> type) {
    return (Ref<T>) STORE.computeIfAbsent(id, _ -> new Ref<>(id, type));
  }

  /**
   * Retrieves a typed asset by identifier.
   *
   * <p>This is a convenience method that extracts the current value from
   * the underlying {@link Ref}. For hot-reload support, prefer
   * {@link #ref(Identifier, Class)} and hold the returned {@code Ref}.
   *
   * <p>If no compatible asset is registered, a fallback registered via
   * {@link AssetFallback#register(Class, Object)} for the requested type is
   * returned, or {@code null} if none exists.
   *
   * @param id   the asset identifier
   * @param type the expected type
   * @param <T>  the expected type
   * @return the asset, or a type fallback, or {@code null}
   */
  @SuppressWarnings("unchecked")
  public static <T> @Nullable T getOrDefault(Identifier id, Class<T> type) {
    Ref<?> ref = STORE.get(id);
    if (ref != null) {
      Object value = ref.get();
      if (type.isInstance(value)) {
        return (T) value;
      }
    }
    T fallback = AssetFallback.get(type);
    return type.isInstance(fallback) ? fallback : null;
  }

  /**
   * Returns whether an asset is registered under the identifier.
   *
   * @param id the asset identifier
   * @return {@code true} if an asset is registered
   */
  public static boolean contains(Identifier id) {
    return STORE.containsKey(id);
  }

  /**
   * Returns the identifiers of all registered assets.
   *
   * <p>The returned set is a live view backed by the store.
   *
   * @return the registered identifiers
   */
  public static Set<Identifier> keys() {
    return STORE.keySet();
  }

  /**
   * Returns a snapshot of all current asset values keyed by identifier.
   *
   * @return a snapshot map of the store
   */
  public static Map<Identifier, Object> entries() {
    ConcurrentHashMap<Identifier, Object> snapshot = new ConcurrentHashMap<>();
    STORE.forEach((id, ref) -> {
      Object value = ref.get();
      if (value != null) {
        snapshot.put(id, value);
      }
    });
    return snapshot;
  }

  /**
   * Returns the number of registered assets.
   *
   * @return the asset count
   */
  public static int size() {
    return STORE.size();
  }

  /**
   * Removes an asset from the store, closing it if it implements
   * {@link AutoCloseable}.
   *
   * @param id the asset identifier
   */
  public static void remove(Identifier id) {
    Ref<?> ref = STORE.remove(id);
    if (ref != null) {
      dispose(ref.get());
    }
  }

  /**
   * Clears all assets for the given namespace, closing each one if it
   * implements {@link AutoCloseable}.
   *
   * @param namespace the namespace to clear
   */
  public static void clearNamespace(Namespace namespace) {
    for (Identifier id : STORE.keySet().toArray(new Identifier[0])) {
      if (id.namespace().equals(namespace)) {
        remove(id);
      }
    }
  }

  /**
   * Clears all assets from every namespace, closing each one if it
   * implements {@link AutoCloseable}.
   */
  public static void clear() {
    STORE.forEach((id, ref) -> dispose(ref.get()));
    STORE.clear();
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
}
