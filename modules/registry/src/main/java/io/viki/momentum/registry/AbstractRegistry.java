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

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Shared state and lookup behavior for {@link Registry} implementations.
 *
 * <p>Concrete subclasses decide when each entry's supplier is evaluated:
 * {@link DirectRegistry} evaluates it at registration time,
 * {@link IndirectRegistry} when the registry is frozen.
 *
 * @param <T> registry type
 */
public abstract class AbstractRegistry<T extends RegistryEntry> implements Registry<T> {
  private final Identifier key;
  private final Map<Identifier, T> byId = new LinkedHashMap<>();
  private final Map<T, Identifier> byValue = new IdentityHashMap<>();
  private final AtomicInteger nextId = new AtomicInteger();
  private boolean frozen;

  /**
   * Creates a new unfrozen registry.
   *
   * @param key the identifier of this registry
   */
  protected AbstractRegistry(Identifier key) {
    this.key = key;
  }

  /**
   * Registers an already-created value, assigning its registry metadata.
   *
   * @param id    the identifier to register the value under
   * @param value the entry value
   * @return the registered value
   * @throws IllegalStateException    if this registry is frozen
   * @throws IllegalArgumentException if an entry is already registered under the identifier
   */
  protected T registerValue(Identifier id, T value) {
    if (frozen) {
      throw new IllegalStateException("Registry '" + key + "' is frozen");
    }
    if (byId.containsKey(id)) {
      throw new IllegalArgumentException("Duplicate registry entry: " + id);
    }
    byId.put(id, value);
    byValue.put(value, id);
    RegistryContext ctx = value.getRegistryContext();
    ctx.putId(id);
    ctx.putIndex(nextId.getAndIncrement());
    ctx.putRegistry(this);
    return value;
  }

  @Override
  public Identifier key() {
    return key;
  }

  @Override
  public @Nullable T get(Identifier id) {
    return byId.get(id);
  }

  @Override
  public @Nullable Identifier getId(T value) {
    return byValue.get(value);
  }

  @Override
  public boolean contains(Identifier id) {
    return byId.containsKey(id);
  }

  @Override
  public Set<Identifier> keys() {
    return Collections.unmodifiableSet(byId.keySet());
  }

  @Override
  public Stream<T> stream() {
    return byId.values().stream();
  }

  @Override
  public int size() {
    return byId.size();
  }

  @Override
  public void freeze() {
    frozen = true;
  }

  @Override
  public boolean isFrozen() {
    return frozen;
  }

  @Override
  public Iterator<T> iterator() {
    return byId.values().iterator();
  }

  @Override
  public String toString() {
    return "Registry[" + key + ", entries=" + byId.size() + "]";
  }
}
