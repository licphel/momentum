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

package io.viki.momentum.registry;

import io.viki.momentum.util.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Mutable registry metadata attached to a registered object, recording the
 * {@link Identifier} and registration index assigned by its registry.
 *
 * <p>The values are filled in at registration time and remain unset until
 * then.
 */
public final class RegistryContext {
  private @Nullable Identifier id;
  private @Nullable Integer index;
  private @Nullable Registry<?> registry;

  /**
   * Stores the identifier assigned to this object by the registry.
   *
   * @param id the identifier to store
   */
  public void putId(Identifier id) {
    this.id = id;
  }

  /**
   * Stores the position at which this object was registered.
   *
   * @param index the registration index to store
   */
  public void putIndex(int index) {
    this.index = index;
  }

  /**
   * Stores the parent registry.
   *
   * @param registry the parent registry to store
   */
  public void putRegistry(Registry<?> registry) {
    this.registry = registry;
  }

  /**
   * Returns the identifier assigned at registration.
   *
   * @return the identifier, or {@code null} if not registered yet
   */
  public @Nullable Identifier id() {
    return id;
  }

  /**
   * Returns the registration index assigned at registration.
   *
   * @return the index, or {@code null} if not registered yet
   */
  public @Nullable Integer index() {
    return index;
  }

  /**
   * Returns the parent registry.
   *
   * @return the parent registry
   */
  public @Nullable Registry<?> registry() {
    return registry;
  }
}
