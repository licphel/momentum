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
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * A typed, mutable reference to an asset that supports hot-reloading.
 *
 * <p>When an asset is updated via {@link Assets#set(Identifier, Object)},
 * all existing {@code Ref} instances for that identifier and type automatically
 * reflect the new value. Listeners registered via
 * {@link #addChangeListener(BiConsumer)} are notified of every update.
 *
 * <p>{@code Ref} reads and updates a concurrent resource map. Listeners are called synchronously
 * on the updating thread and may be invoked concurrently by concurrent updates.
 *
 * @param <T> the asset type
 * @see Assets
 */
public final class Ref<T> implements Supplier<T> {
  private final Identifier id;
  private final List<BiConsumer<@Nullable T, @Nullable T>> listeners = new CopyOnWriteArrayList<>();
  private final Map<Class<?>, @Nullable Object> values;
  private final Class<T> type;

  Ref(Identifier id, Class<T> type, Map<Class<?>, Object> values) {
    this.id = id;
    this.type = type;
    this.values = values;
  }

  /**
   * Returns the asset identifier.
   *
   * @return the identifier
   */
  public Identifier id() {
    return id;
  }

  /**
   * Returns the current asset value.
   *
   * @return the current value, or {@code null} if none
   */
  @SuppressWarnings("all")
  public @Nullable T get() {
    return type.cast(values.getOrDefault(type, null));
  }

  /**
   * Returns the current asset value or default fallback value.
   *
   * @return the current value, or {@code null} if none
   */
  @SuppressWarnings("all")
  public @Nullable T getOrDefault() {
    T value = get();
    return value == null ? AssetFallback.get(type) : value;
  }

  /**
   * Returns the current asset value.
   *
   * @return the current optional value
   */
  public Optional<@Nullable T> optional() {
    return Optional.ofNullable(get());
  }

  /**
   * Updates the asset value and notifies all listeners.
   *
   * <p>If the new value equals the current value (by identity), no
   * notification is sent.
   *
   * @param newValue the new value
   */
  void set(@Nullable T newValue) {
    T old = type.cast(newValue == null ? values.remove(type) : values.put(type, newValue));
    if (old == newValue) {
      return;
    }
    for (BiConsumer<@Nullable T, @Nullable T> listener : listeners) {
      listener.accept(old, newValue);
    }
  }

  /**
   * Registers a change listener that is invoked whenever the value is updated.
   *
   * <p>The listener receives the old value followed by the new value.
   * Either may be {@code null}.
   *
   * @param listener a consumer receiving the old and new values
   */
  public void addChangeListener(BiConsumer<@Nullable T, @Nullable T> listener) {
    listeners.add(listener);
  }

  /**
   * Removes a change listener.
   *
   * @param listener a consumer receiving the old and new values
   */
  public void removeChangeListener(BiConsumer<@Nullable T, @Nullable T> listener) {
    listeners.remove(listener);
  }
}
