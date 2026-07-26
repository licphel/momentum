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

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable schema describing every property in a configuration.
 *
 * <p>Define properties with the {@link Builder}, then obtain typed
 * handles via {@link #get(String)}.
 *
 * @see ConfigValue
 * @see Config
 */
public final class ConfigSpec {
  private final Map<String, ConfigValue<?>> values;

  private ConfigSpec(Map<String, ConfigValue<?>> values) {
    this.values = Map.copyOf(values);
  }

  /**
   * Creates a new {@link Builder}.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the typed property handle for the given path.
   *
   * @param path the property path
   * @param <T>  the property type
   * @return the config value handle
   * @throws IllegalArgumentException if the path is not defined
   */
  @SuppressWarnings("unchecked")
  public <T> ConfigValue<T> get(String path) {
    ConfigValue<?> value = values.get(path);
    if (value == null) {
      throw new IllegalArgumentException("Unknown config key: " + path);
    }
    return (ConfigValue<T>) value;
  }

  /**
   * Returns all property handles, in the order they were defined.
   *
   * @return an unmodifiable map of paths to config values
   */
  public Map<String, ConfigValue<?>> values() {
    return values;
  }

  /**
   * Mutable builder for constructing a {@link ConfigSpec}.
   *
   * <p>Define properties with the {@code define} method, then call
   * {@link #build()} to produce the immutable spec.
   */
  public static final class Builder {
    private final Map<String, ConfigValue<?>> values = new LinkedHashMap<>();

    private Builder() {
    }

    /**
     * Defines a property and returns its typed handle.
     *
     * @param path      the key path
     * @param def       the default value
     * @param validator the validator, or {@code null} for no validation
     * @param comment   the comment, or {@code null} for no comment
     * @param <T>       the value type
     * @return the typed property handle
     */
    public <T> ConfigValue<T> define(String path, T def,
                                     @Nullable ConfigValidator<T> validator,
                                     @Nullable String comment) {
      ConfigValue<T> cv = new ConfigValue<>(path, def, validator, comment);
      values.put(path, cv);
      return cv;
    }

    /**
     * Produces an immutable {@link ConfigSpec} from the properties
     * defined in this builder.
     *
     * @return the new spec
     */
    public ConfigSpec build() {
      return new ConfigSpec(values);
    }
  }
}
