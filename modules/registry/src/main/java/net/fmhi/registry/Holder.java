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

package net.fmhi.registry;

import net.fmhi.util.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A stable reference to a registered value, also serving as a
 * {@link Supplier} of that value.
 *
 * <p>Direct registries ({@link DirectRegistry}) create the holder with the
 * value already resolved; deferred registries ({@link IndirectRegistry})
 * resolve it when the registry is frozen. Reading an unresolved holder
 * fails, so callers must not read a deferred holder before its registry is
 * frozen. Safe for concurrent use once the value is resolved.
 *
 * @param <T> the type of the registered value
 */
public class Holder<T> implements Supplier<T> {
  private final Identifier id;
  private volatile @Nullable T value;

  /**
   * Creates a holder with the value already resolved.
   *
   * @param id    the identifier the value is registered under
   * @param value the registered value
   */

  public Holder(Identifier id, @Nullable T value) {
    this.id = id;
    this.value = value;
  }

  /**
   * Creates an unresolved holder; {@link #resolve(Object)} must be called
   * before the value is read.
   *
   * @param id the identifier the value is registered under
   */

  public Holder(Identifier id) {
    this.id = id;
  }

  /**
   * Makes the given value available to readers of this holder.
   *
   * @param value the value to store
   */
  public void resolve(T value) {
    this.value = value;
  }

  /**
   * Returns the identifier this holder's value is registered under.
   *
   * @return the identifier
   */
  public Identifier id() {
    return id;
  }

  /**
   * Returns the registered value.
   *
   * @return the value
   * @throws NullPointerException if the value has not been resolved yet
   */
  @Override
  public T get() {
    return Objects.requireNonNull(value);
  }

  @Override
  public String toString() {
    return "Holder[" + id + "]";
  }
}
