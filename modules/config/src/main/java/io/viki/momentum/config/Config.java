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

package io.viki.momentum.config;

import io.viki.momentum.codec.nbt.CompoundNBT;
import io.viki.momentum.codec.nbt.NBT;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A live, typed handle to a single configuration property.
 *
 * <p>Created via {@link ConfigSpec#define(String, Object, Validator, String)} and bound
 * directly to the spec's backing data. Reads and writes are reflected immediately;
 * changes are persisted on {@link ConfigSpec#save()}.
 *
 * <p>When the backing store contains no entry for this property, {@link #get()}
 * returns the default value. Validation is performed on every write; a failed
 * validation leaves the previous value unchanged.
 *
 * @param <T> the value type of this property
 * @see ConfigSpec
 * @see Validator
 */
public final class Config<T> {
  private final String path;
  private final T defaultValue;
  private final @Nullable Validator<T> validator;
  private final @Nullable String comment;
  private @Nullable CompoundNBT data;
  private final List<Consumer<T>> changeListeners = new ArrayList<>();

  Config(String path, T defaultValue,
         @Nullable Validator<T> validator,
         @Nullable String comment) {
    this.path = path;
    this.defaultValue = defaultValue;
    this.validator = validator;
    this.comment = comment;
  }

  void bind(CompoundNBT data) {
    this.data = data;
  }

  /**
   * Adds a change listener to this config.
   *
   * @param listener the listener
   * @return self for chaining
   */
  public Config<T> addChangeListener(Consumer<T> listener) {
    changeListeners.add(listener);
    return this;
  }

  /**
   * Returns the key path of this property.
   *
   * @return the key path
   */
  public String path() {
    return path;
  }

  /**
   * Returns the default value for this property.
   *
   * @return the default value
   */
  public T defaultValue() {
    return defaultValue;
  }

  /**
   * Returns the current value, or the default if the backing store contains no entry for this property.
   *
   * @return the current value; may be {@code null} if the default is {@code null}
   */
  @SuppressWarnings("unchecked")
  public @Nullable T get() {
    if (data == null) {
      return defaultValue;
    }
    NBT v = data.get(path);
    return v == null ? defaultValue : (T) v.asObject();
  }

  /**
   * Sets the current value after running validation.
   *
   * <p>If validation fails, the previous value is kept silently. The change is
   * persisted on {@link ConfigSpec#save()}.
   *
   * @param value the new value
   * @throws IllegalStateException if this config has not been bound to a spec
   */
  public void set(T value) {
    if (data == null) {
      throw new IllegalStateException("Config not bound — call ConfigSpec.define first");
    }
    if (validator != null) {
      String err = validator.validate(value);
      if (err != null) {
        return;
      }
    }
    T old = get();
    if (!Objects.equals(old, value)) {
      data.put(path, NBT.wrap(value));
      changeListeners.forEach(l -> l.accept(value));
    }
  }

  /**
   * Returns the validator for this property.
   *
   * @return the validator, or {@code null} if no validation is configured
   */
  public @Nullable Validator<T> validator() {
    return validator;
  }

  /**
   * Returns the comment for this property.
   *
   * @return the comment, or {@code null} if none was set
   */
  public @Nullable String comment() {
    return comment;
  }
}
