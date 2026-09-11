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

import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A namespace-keyed collection of typed entries, supporting lookup by
 * identifier and reverse lookup by entry identity.
 *
 * <p>Each registry has its own {@link Identifier} and stores entries
 * registered under theirs. A registry accepts new entries during the
 * registration phase only; once frozen, further registration is rejected.
 *
 * @param <T> the type of entries stored in this registry
 */
public interface Registry<T extends RegistryEntry> extends Iterable<T> {
  /**
   * Returns the identifier under which this registry itself is registered.
   *
   * @return the registry key
   */
  Identifier key();

  /**
   * Registers an entry under the given identifier, creating its value
   * through the supplier.
   *
   * <p>The supplier is evaluated once at registration time; the returned
   * holder refers to the created value and remains valid afterward.
   *
   * @param id       the identifier to register the entry under
   * @param supplier creates the entry value
   * @return a holder for the registered value
   * @throws IllegalStateException    if this registry is frozen
   * @throws IllegalArgumentException if an entry is already registered under the identifier
   */
  Holder<T> register(Identifier id, Supplier<? extends T> supplier);

  /**
   * Looks up the entry registered under the given identifier.
   *
   * @param id the identifier to look up
   * @return the registered value, or {@code null} if none is registered under the identifier
   */
  @Nullable T get(Identifier id);

  /**
   * Looks up the identifier under which the given entry is registered.
   *
   * @param value the entry to look up
   * @return the identifier, or {@code null} if the entry is not in this registry
   */
  @Nullable Identifier getId(T value);

  /**
   * Returns whether an entry is registered under the given identifier.
   *
   * @param id the identifier to test
   * @return {@code true} if an entry is registered under it
   */
  boolean contains(Identifier id);

  /**
   * Returns all identifiers registered in this registry.
   *
   * @return an unmodifiable view of the registered identifiers
   */
  Set<Identifier> keys();

  /**
   * Returns a stream over all registered entries.
   *
   * @return a sequential stream of the entries
   */
  Stream<T> stream();

  /**
   * Returns the number of registered entries.
   *
   * @return the entry count
   */
  int size();

  /**
   * Prevents any further registration.
   */
  void freeze();

  /**
   * Returns whether further registration is prevented.
   *
   * @return {@code true} if this registry is frozen
   */
  boolean isFrozen();
}
