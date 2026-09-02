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

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A derived asset that recomputes when any of its dependencies change.
 *
 * <p>{@code Reloadable} wraps a computation that depends on one or more
 * {@link Ref}s. When any dependency is updated via {@link Assets#set},
 * the {@code Reloadable} marks itself dirty and recomputes on the next
 * call to {@link #get()}.
 *
 * <p>This is useful for assets that are assembled from other assets —
 * for example, a {@code TexturePart} derived from a {@code Texture} ref,
 * or a material derived from multiple texture refs.
 *
 * @param <T> the derived asset type
 */
public final class Reloadable<T> {
  private final Supplier<T> calculator;
  private final List<Consumer<@Nullable T>> listeners = new CopyOnWriteArrayList<>();
  private volatile @Nullable T value;
  private volatile boolean dirty = true;

  private Reloadable(Supplier<T> calculator, Ref<?>[] deps) {
    this.calculator = calculator;
    for (Ref<?> ref : deps) {
      ref.addChangeListener((oldVal, newVal) -> markDirty());
    }
  }

  /**
   * Creates a reloadable that depends on the given refs.
   *
   * <p>The calculator is invoked immediately to produce the initial value.
   * Thereafter, whenever any dependency notifies a change, the value is
   * recomputed on the next call to {@link #get()}.
   *
   * @param calculator the computation that produces the derived value
   * @param deps       the refs this computation depends on
   * @param <T>        the derived type
   * @return a new reloadable instance
   */
  public static <T> Reloadable<T> of(Supplier<T> calculator, Ref<?>... deps) {
    Reloadable<T> r = new Reloadable<>(calculator, deps);
    r.reload();
    return r;
  }

  /**
   * Returns the current derived value, recomputing if dirty.
   *
   * @return the current value
   * @throws RuntimeException if the calculator throws
   */
  public synchronized T get() {
    if (dirty) {
      reload();
    }
    return Objects.requireNonNull(value);
  }

  /**
   * Forces a recomputation and notifies listeners if the value changed.
   *
   * <p>Normally called automatically when dependencies change. Exposed for
   * manual invalidation.
   */
  public synchronized void reload() {
    T oldVal = value;
    T newVal = calculator.get();
    value = newVal;
    dirty = false;
    if (oldVal != newVal) {
      for (Consumer<@Nullable T> listener : listeners) {
        listener.accept(newVal);
      }
    }
  }

  /**
   * Registers a listener that is invoked whenever the value changes.
   *
   * <p>The listener is not invoked for the initial value at registration
   * time; call {@link #get()} first if you need the initial value.
   *
   * @param listener a consumer receiving the new value
   */
  public void listen(Consumer<@Nullable T> listener) {
    listeners.add(listener);
  }

  /**
   * Returns the current value without triggering a recomputation.
   *
   * @return the current value, or {@code null} if never computed
   */
  public @Nullable T getUnsafe() {
    return value;
  }

  /**
   * Returns whether the value is dirty and will be recomputed on the next
   * call to {@link #get()}.
   *
   * @return {@code true} if the value is dirty
   */
  public boolean isDirty() {
    return dirty;
  }

  private void markDirty() {
    dirty = true;
  }
}