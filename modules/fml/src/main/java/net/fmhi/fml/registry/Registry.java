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

import java.util.Set;
import java.util.stream.Stream;

/**
 * A namespace-keyed collection of registered entries.
 *
 * <p>Each registry is itself identified by an {@link Identifier}. Entries
 * are registered under their own identifiers and can be looked up by
 * identity (entry → id) and by id (id → entry).
 *
 * <p>Registries are frozen after the registration phase; once frozen,
 * no further entries may be added.
 *
 * @param <T> the type of entries in this registry
 * @see DeferredRegister
 */
public interface Registry<T> extends Iterable<T> {
  /**
   * Returns this registry's own identifier within the root registry.
   *
   * @return the registry key
   */
  Identifier key();

  /**
   * Registers an entry under the given identifier.
   *
   * @param id    the entry identifier
   * @param value the entry value
   * @return the registered value, for chaining
   * @throws IllegalStateException    if the registry is frozen
   * @throws IllegalArgumentException if the identifier is already registered
   */
  T register(Identifier id, T value);

  /**
   * Looks up an entry by its identifier.
   *
   * @param id the entry identifier
   * @return the registered value, or {@code null} if not found
   */
  @Nullable T get(Identifier id);

  /**
   * Looks up the identifier for a registered entry.
   *
   * @param value the entry
   * @return the identifier, or {@code null} if the entry is not in this registry
   */
  @Nullable Identifier getId(T value);

  /**
   * Returns whether this registry contains the given identifier.
   *
   * @param id the entry identifier
   * @return {@code true} if registered
   */
  boolean contains(Identifier id);

  /**
   * Returns the set of all registered identifiers.
   *
   * @return an unmodifiable view of the key set
   */
  Set<Identifier> keys();

  /**
   * Returns a stream of all registered entries.
   *
   * @return a sequential stream
   */
  Stream<T> stream();

  /**
   * Returns the number of entries in this registry.
   *
   * @return the entry count
   */
  int size();

  /**
   * Freezes this registry, preventing further registration.
   */
  void freeze();

  /**
   * Returns whether this registry is frozen.
   *
   * @return {@code true} if frozen
   */
  boolean isFrozen();
}
