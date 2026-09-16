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

package io.viki.momentum.util;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A lazy value holder that evaluates its value on the first call to {@link #get()}
 * and caches the result.
 *
 * @param <T> the type of the held value
 */
public class Lazy<T> {
  protected @Nullable Supplier<T> supplier;
  protected @Nullable T value;
  protected boolean initialized;

  private Lazy(Supplier<T> supplier) {
    this.supplier = supplier;
  }

  private Lazy() {
    this.supplier = null;
  }

  /**
   * Creates a lazy value.
   *
   * @param supplier the lazy supplier
   * @param <T> the supplier type
   * @return a lazy value
   */
  public static <T> Lazy<T> of(Supplier<T> supplier) {
    return new Lazy<>(supplier);
  }

  /**
   * Creates a lazy value whose supplier must be injected from the outside via
   * {@link #inject(Supplier)}. Until a supplier is injected, calling
   * {@link #get()} throws an {@link IllegalStateException}.
   *
   * @param <T> the value type
   * @return an injected lazy value
   */
  public static <T> Lazy<T> ofInjected() {
    return new InjectedLazy<>();
  }

  /**
   * Injects the lazy supplier. Can only be injected once.
   *
   * @param supplier the lazy supplier
   * @throws IllegalStateException if no supplier slot is available or a supplier
   *     has already been injected
   */
  public void inject(Supplier<T> supplier) {
  }

  /**
   * Returns the lazily evaluated value, triggering evaluation on the first call.
   *
   * @return the value, which may be {@code null} depending on the supplier
   */
  public @Nullable T get() {
    if (!initialized) {
      value = requireSupplier().get();
      initialized = true;
    }
    return value;
  }

  /**
   * Returns whether evaluation has already been performed.
   *
   * @return whether is present
   */
  public boolean isPresent() {
    return initialized;
  }

  private Supplier<T> requireSupplier() {
    Supplier<T> s = supplier;
    if (s == null) {
      throw new IllegalStateException("Supplier has not been injected");
    }
    return s;
  }

  /** An injected {@link Lazy} whose supplier is provided after creation. */
  private static final class InjectedLazy<T> extends Lazy<T> {
    private InjectedLazy() {
      super();
    }

    @Override
    public void inject(Supplier<T> supplier) {
      if (this.supplier != null) {
        throw new IllegalStateException("Supplier already injected");
      }
      this.supplier = supplier;
    }
  }
}