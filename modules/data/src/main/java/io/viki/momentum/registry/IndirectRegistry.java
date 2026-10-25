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
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A {@link Registry} that has declaration-ordered identities with lazy, dependency-driven construction.
 *
 * <p>Declaration and resolution are confined to one loading thread. Freeze resolves every declaration;
 * afterward holders and read-only lookups may be shared. Runtime indices follow declaration order,
 * never the order in which dependencies happen to be resolved.
 *
 * @param <T> registry type
 */
public class IndirectRegistry<T extends RegistryEntry> extends AbstractRegistry<T> {
  private static final ThreadLocal<ArrayDeque<String>> RESOLUTION_PATH = ThreadLocal.withInitial(ArrayDeque::new);

  private final Map<Identifier, Entry> entries = new LinkedHashMap<>();
  private final List<Entry> declared = new ArrayList<>();
  private boolean sealing;

  /**
   * Creates a new unfrozen registry.
   *
   * @param key the identifier of this registry
   */
  public IndirectRegistry(Identifier key) {
    super(key);
  }

  @Override
  public Holder<T> register(Identifier id, Supplier<? extends T> supplier) {
    if (isFrozen() || sealing) {
      throw new IllegalStateException("Registry is sealed: " + key());
    }
    Entry entry = entries.computeIfAbsent(id, Entry::new);
    if (entry.index >= 0) {
      throw new IllegalArgumentException("Duplicate registry entry: " + id);
    }
    entry.supplier = supplier;
    entry.index = declared.size();
    declared.add(entry);
    return entry.holder;
  }

  @Override
  public Holder<T> hold(Identifier id) {
    Entry entry = entries.get(id);
    if (entry != null) {
      return entry.holder;
    }
    if (isFrozen() || sealing) {
      throw new IllegalArgumentException("Unknown entry " + id + " in " + key());
    }
    entry = new Entry(id);
    entries.put(id, entry);
    return entry.holder;
  }

  @Override
  public @Nullable T get(Identifier id) {
    Entry entry = entries.get(id);
    return entry == null || entry.index < 0 ? null : entry.holder.get();
  }

  @Override
  public T get(int index) {
    return declared.get(index).holder.get();
  }

  @Override
  public List<T> values() {
    return stream().toList();
  }

  @Override
  public boolean contains(Identifier id) {
    Entry entry = entries.get(id);
    return entry != null && entry.index >= 0;
  }

  @Override
  public Set<Identifier> keys() {
    Set<Identifier> keys = new LinkedHashSet<>();
    for (Entry entry : declared) {
      keys.add(entry.id);
    }
    return Collections.unmodifiableSet(keys);
  }

  @Override
  public Stream<T> stream() {
    return declared.stream().map(entry -> entry.holder.get());
  }

  @Override
  public int size() {
    return declared.size();
  }

  @Override
  public void freeze() {
    if (isFrozen()) {
      return;
    }
    sealing = true;
    for (Entry entry : entries.values()) {
      if (entry.index < 0) {
        throw new IllegalStateException("Undeclared entry " + entry.id + " in " + key());
      }
    }
    for (Entry entry : declared) {
      entry.holder.get();
    }
    super.freeze();
  }

  @Override
  public Iterator<T> iterator() {
    return stream().iterator();
  }

  private final class Entry {
    final Identifier id;
    final Holder<T> holder;
    @Nullable Supplier<? extends T> supplier;
    int index = -1;
    boolean resolving;
    @Nullable RuntimeException failure;

    Entry(Identifier id) {
      this.id = id;
      holder = Holder.deferred(id, this::resolve);
    }

    T resolve() {
      if (failure != null) {
        throw failure;
      }
      Supplier<? extends T> factory = supplier;
      if (factory == null) {
        throw new IllegalStateException("Undeclared entry " + id + " in " + key());
      }
      ArrayDeque<String> path = RESOLUTION_PATH.get();
      String name = key() + "/" + id;
      if (resolving) {
        throw new IllegalStateException("Cyclic registry dependency: " + String.join(" -> ", path) + " -> " + name);
      }
      resolving = true;
      path.addLast(name);
      try {
        T value = registerValue(id, factory.get(), index);
        supplier = null;
        return value;
      } catch (RuntimeException exception) {
        failure = exception;
        throw exception;
      } finally {
        path.removeLast();
        resolving = false;
        if (path.isEmpty()) {
          RESOLUTION_PATH.remove();
        }
      }
    }
  }
}
