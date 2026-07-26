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

package net.fmhi.fml.config;

import net.fmhi.codec.tag.CompoundTag;
import org.jspecify.annotations.Nullable;

/**
 * A live, typed handle to a single configuration property.
 *
 * <p>Created during spec definition and bound to a backing data store
 * when a {@link Config} is loaded. Reads and writes go directly to the
 * in-memory store; changes are persisted on {@link Config#save()}.
 *
 * @param <T> the value type
 * @see ConfigSpec
 * @see Config
 */
public final class ConfigValue<T> {
  private final String path;
  private final T defaultValue;
  private final @Nullable ConfigValidator<T> validator;
  private final @Nullable String comment;
  private @Nullable CompoundTag data;

  ConfigValue(String path, T defaultValue,
              @Nullable ConfigValidator<T> validator,
              @Nullable String comment) {
    this.path = path;
    this.defaultValue = defaultValue;
    this.validator = validator;
    this.comment = comment;
  }

  void bind(CompoundTag data) {
    this.data = data;
  }

  /**
   * Returns the key path.
   *
   * @return the key path
   */
  public String path() {
    return path;
  }

  /**
   * Returns the default value.
   *
   * @return the default value
   */
  public T defaultValue() {
    return defaultValue;
  }

  /**
   * Returns the current value, or the default if the backing store
   * contains no entry for this property.
   *
   * @return the current value; may be {@code null} if the default is
   *         {@code null}
   */
  public @Nullable T get() {
    if (data == null) {
      return defaultValue;
    }
    return data.get(path);
  }

  /**
   * Sets the current value after running validation.
   *
   * <p>The change is immediately visible and will be persisted on the
   * next {@link Config#save()}.
   *
   * @param value the new value
   * @throws ConfigException       if the validator rejects the value
   * @throws IllegalStateException if not bound to a config
   */
  public void set(T value) {
    if (data == null) {
      throw new IllegalStateException("ConfigValue not bound — load a Config first");
    }
    if (validator != null) {
      String err = validator.validate(value);
      if (err != null) {
        throw new ConfigException(path + ": " + err);
      }
    }
    data.put(path, value);
  }

  /**
   * Returns the validator.
   *
   * @return the validator, or {@code null} if none
   */
  public @Nullable ConfigValidator<T> validator() {
    return validator;
  }

  /**
   * Returns the comment.
   *
   * @return the comment, or {@code null} if none was set
   */
  public @Nullable String comment() {
    return comment;
  }
}
