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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A {@link Registry} that queues entry suppliers during the declaration
 * phase and evaluates them all at once when frozen.
 *
 * <p>Unlike {@link DirectRegistry}, which evaluates each supplier at
 * registration time, entries declared through a deferred registry become
 * available only after {@link #freeze()} runs. This supports the
 * registration phase: values may reference other entries that are not yet
 * created, so their factories are held back until every registry has been
 * declared.
 *
 * <p>Holders returned by {@link #register} are unresolved until
 * {@link #freeze()}; reading one earlier fails.
 *
 * @param <T> registry type
 */
public class IndirectRegistry<T extends RegistryEntry> extends AbstractRegistry<T> {
  private final List<Entry<T>> pending = new ArrayList<>();

  /**
   * Creates a new unfrozen deferred registry.
   *
   * @param key the identifier of this registry
   */
  public IndirectRegistry(Identifier key) {
    super(key);
  }

  @Override
  public Holder<T> register(Identifier id, Supplier<? extends T> supplier) {
    Holder<T> holder = new Holder<>(id);
    pending.add(new Entry<>(id, supplier, holder));
    return holder;
  }

  /**
   * Evaluates all queued suppliers in declaration order, registers their
   * values, resolves the returned holders, and prevents further
   * registration.
   *
   * <p>Idempotent: subsequent calls have no effect.
   */
  @Override
  public void freeze() {
    if (isFrozen()) {
      return;
    }
    for (Entry<T> entry : pending) {
      T value = registerValue(entry.id, entry.supplier.get());
      entry.holder.resolve(value);
    }
    pending.clear();
    super.freeze();
  }

  private record Entry<T>(Identifier id, Supplier<? extends T> supplier, Holder<T> holder) {
  }
}
