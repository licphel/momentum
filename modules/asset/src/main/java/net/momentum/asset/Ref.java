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
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * A typed, mutable reference to an asset that supports hot-reloading.
 *
 * <p>When an asset is updated via {@link Assets#set(Identifier, Object)},
 * all existing {@code Ref} instances for that identifier automatically
 * reflect the new value. Listeners registered via
 * {@link #fireChanged(BiConsumer)} are notified of every update.
 *
 * <p>{@code Ref} is thread-safe: reads are volatile and writes are
 * guarded by the owning {@link Assets} store.
 *
 * @param <T> the asset type
 * @see Assets
 */
public final class Ref<T> {
  private final Identifier id;
  private final List<BiConsumer<@Nullable T, @Nullable T>> listeners = new CopyOnWriteArrayList<>();
  private volatile @Nullable T value;

  Ref(Identifier id, @Nullable T initialValue) {
    this.id = id;
    this.value = initialValue;
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
  public @Nullable T get() {
    return value;
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
    T old = value;
    if (old == newValue) {
      return;
    }
    value = newValue;
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
  public void fireChanged(BiConsumer<@Nullable T, @Nullable T> listener) {
    listeners.add(listener);
  }

  /**
   * Returns the value if present, otherwise the given fallback.
   *
   * @param fallback the fallback value
   * @return the current value, or {@code fallback} if the value is {@code null}
   */
  public T orElse(T fallback) {
    T v = value;
    return v != null ? v : fallback;
  }

  /**
   * Returns the value if present, otherwise the value supplied by the fallback.
   *
   * @param supplier the fallback supplier
   * @return the current value, or the supplier's result if the value is {@code null}
   */
  public T orElseGet(Supplier<? extends T> supplier) {
    T v = value;
    return v != null ? v : supplier.get();
  }

  /**
   * Returns whether a non-null value is present.
   *
   * @return {@code true} if the value is non-null
   */
  public boolean isPresent() {
    return value != null;
  }
}
