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

import io.viki.momentum.tag.Tag;
import io.viki.momentum.util.Identifier;

import java.util.Objects;

/**
 * An object that carries its own registry metadata, so it can know its
 * identifier and registration index without consulting the registry.
 */
public interface RegistryEntry {
  /**
   * Returns the metadata holding this object's assigned identifier and index.
   *
   * @return the registry metadata
   */
  RegistryContext getRegistryContext();

  /**
   * Returns the identifier this object is registered under.
   *
   * @return the identifier
   * @throws NullPointerException if this object has not been registered yet
   */
  default Identifier registryId() {
    return Objects.requireNonNull(getRegistryContext().id());
  }

  /**
   * Returns the index at which this object was registered.
   *
   * @return the registration index
   * @throws NullPointerException if this object has not been registered yet
   */
  default int registryIndex() {
    return Objects.requireNonNull(getRegistryContext().index());
  }

  /**
   * Tests whether this object belongs to the given tag.
   *
   * @param tag the tag to test membership in
   * @return {@code true} if the tag contains this object
   */
  default boolean is(Tag<?> tag) {
    return tag.contains(this);
  }

  /**
   * Tests whether this object is the given instance.
   *
   * @param t the instance to compare against
   * @return {@code true} if both refer to the same object
   */
  default boolean is(Object t) {
    return this == t;
  }
}
