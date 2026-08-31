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

package net.momentum.asset;

import net.momentum.util.Identifier;
import net.momentum.util.Namespace;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Global, thread-safe asset store keyed by {@link Identifier}.
 *
 * <p>Assets are stored in {@link Ref} containers that support hot-reloading.
 * When {@link #set(Identifier, Object)} is called with a new value for an
 * existing identifier, all {@code Ref} instances for that identifier
 * automatically reflect the update. Code that needs to observe asset
 * changes should call {@link #getRef(Identifier, Class)} and hold the
 * returned {@code Ref}, reading from it each time the value is needed.
 *
 * <p>Assets are loaded by {@link AssetLoader} instances and consumed
 * anywhere in the engine.
 */
public final class Assets {
  private static final ConcurrentHashMap<Identifier, Ref<?>> STORE = new ConcurrentHashMap<>();

  private Assets() {
  }

  /**
   * Stores an asset under the given identifier.
   *
   * <p>If a {@link Ref} already exists for the identifier, its value is
   * updated in place and all registered listeners are notified — this is
   * the mechanism that enables hot-reloading.
   *
   * @param id    the asset identifier
   * @param value the asset value
   */
  @SuppressWarnings("unchecked")
  public static void set(Identifier id, Object value) {
    Ref<Object> ref = (Ref<Object>) STORE.computeIfAbsent(id,
        k -> new Ref<>(id, value));
    ref.set(value);
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
   * @return the ref, or {@code null} if the identifier is not registered
   */
  @SuppressWarnings("unchecked")
  public static <T> @Nullable Ref<T> getRef(Identifier id, Class<T> type) {
    return (Ref<T>) STORE.get(id);
  }

  /**
   * Retrieves a typed asset by identifier.
   *
   * <p>This is a convenience method that extracts the current value from
   * the underlying {@link Ref}. For hot-reload support, prefer
   * {@link #getRef(Identifier, Class)} and hold the returned {@code Ref}.
   *
   * @param id   the asset identifier
   * @param type the expected type
   * @param <T>  the expected type
   * @return the asset, or {@code null} if not found or of a different type
   */
  @SuppressWarnings("unchecked")
  public static <T> @Nullable T get(Identifier id, Class<T> type) {
    Ref<?> ref = STORE.get(id);
    if (ref == null) {
      return null;
    }
    Object value = ref.get();
    if (type.isInstance(value)) {
      return (T) value;
    }
    return null;
  }

  /**
   * Removes an asset from the store.
   *
   * @param id the asset identifier
   */
  public static void remove(Identifier id) {
    STORE.remove(id);
  }

  /**
   * Clears all assets for the given namespace.
   *
   * @param namespace the namespace to clear
   */
  public static void clearNamespace(Namespace namespace) {
    STORE.keySet().removeIf(id -> id.namespace().equals(namespace));
  }

  /**
   * Clears all assets from every namespace.
   */
  public static void clear() {
    STORE.clear();
  }
}
