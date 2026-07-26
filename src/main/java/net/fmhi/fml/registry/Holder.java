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

package net.fmhi.fml.registry;

import net.fmhi.fml.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A holder for a lazily-registered value.
 *
 * <p>Created by {@link DeferredRegister#register(String, Supplier)} during
 * mod construction. The value is not available until the registration phase
 * completes. Calling {@link #get()} before registration throws
 * {@link IllegalStateException}.
 *
 * <p>Registry objects are typed suppliers: they can be used anywhere a
 * {@link Supplier} is expected.
 *
 * @param <T> the type of the registered value
 */
public class Holder<T> implements Supplier<T> {
  private final Identifier id;
  private @Nullable T value;
  private boolean present;

  /**
   * Creates a holder reference.
   *
   * @param id holder id
   */
  public Holder(Identifier id) {
    this.id = Objects.requireNonNull(id);
  }

  /**
   * Resolves the held value.
   *
   * @param value value to set
   */
  public void resolve(T value) {
    this.value = value;
    this.present = true;
  }

  /**
   * Returns the identifier this object will be (or was) registered under.
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
   * @throws IllegalStateException if registration has not completed
   */
  @Override
  public T get() {
    if (!present || value == null) {
      throw new IllegalStateException("Holder not yet registered: " + id);
    }
    return value;
  }

  /**
   * Returns whether the value has been registered.
   *
   * @return {@code true} if the value is available
   */
  public boolean isPresent() {
    return present;
  }

  @Override
  public String toString() {
    return "Holder[" + id + "]";
  }
}
