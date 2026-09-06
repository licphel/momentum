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
package io.viki.momentum.gfx.ui;

import io.viki.momentum.asset.AssetFallback;
import io.viki.momentum.asset.Assets;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Externally populated typed style table; assets are resolved at use time and never owned.
 *
 * <p>The table is mutable and not thread-safe; register and resolve styles on the owning UI
 * thread.
 */
public final class Look implements Iterable<Map.Entry<StyleKey<?>, Supplier<?>>> {
  private final Map<StyleKey<?>, Supplier<?>> assets = new HashMap<>();

  public <T> void put(StyleKey<T> key, @Nullable T value) {
    Supplier<@Nullable Object> wrapped = () -> value;
    assets.put(key, wrapped);
  }

  public <T> void put(StyleKey<T> key, Supplier<? extends @Nullable T> value) {
    assets.put(key, value);
  }

  public <T> @Nullable T get(StyleKey<T> key) {
    Supplier<?> supplier = assets.get(key);
    if (supplier == null) {
      return null;
    }
    Object value = supplier.get();
    if (value == null) {
      return null;
    }
    if (!key.type().isInstance(value)) {
      throw new IllegalArgumentException("UI style key '" + key.name() + "' resolved " + value.getClass().getName() + ", expected " + key.type().getName());
    }
    return key.type().cast(value);
  }

  public <T> T getOrDefault(StyleKey<T> key) {
    T raw = get(key);
    if (raw == null) {
      return AssetFallback.get(key.type());
    }
    return raw;
  }

  @Override
  public Iterator<Map.Entry<StyleKey<?>, Supplier<?>>> iterator() {
    return assets.entrySet().iterator();
  }
}
