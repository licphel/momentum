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
import net.fmhi.codec.tag.JsonUtil;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A self-contained configuration that combines schema definition, data storage, and JSON persistence.
 *
 * <p>Properties are defined via {@link #define(String, Object, Validator, String)}.
 * The returned {@link Config} handle provides typed read and write access directly
 * to the backing data.
 *
 * <p>Use {@link #load(Path)} to load an existing JSON file, or {@link #load()} for
 * an empty spec. Call {@link #save()} to persist changes back to disk.
 *
 * @see Config
 * @see Validator
 */
public final class ConfigSpec {
  private final CompoundTag data = new CompoundTag();
  private final Map<String, Config<?>> values = new LinkedHashMap<>();
  private @Nullable Path filePath;

  private ConfigSpec() {
  }

  /**
   * Creates an empty spec with no file path.
   *
   * @return a new empty spec
   */
  public static ConfigSpec load() {
    return new ConfigSpec();
  }

  /**
   * Creates a spec by loading values from a JSON file.
   *
   * <p>Define properties after loading to obtain typed handles via
   * {@link #define(String, Object, Validator, String)}.
   *
   * @param path the JSON file to load
   * @return a new spec with values loaded from the file
   * @throws ConfigException if the file cannot be read or parsed
   */
  public static ConfigSpec load(Path path) {
    ConfigSpec spec = new ConfigSpec();
    spec.filePath = path;
    spec.reload(path);
    return spec;
  }

  /**
   * Creates a spec by parsing values from a JSON string.
   *
   * <p>Define properties after loading to obtain typed handles via
   * {@link #define(String, Object, Validator, String)}.
   *
   * @param json the JSON text to parse
   * @return a new spec with values parsed from the string
   * @throws ConfigException if the JSON is malformed
   */
  public static ConfigSpec load(String json) {
    ConfigSpec spec = new ConfigSpec();
    spec.reload(json);
    return spec;
  }

  /**
   * Defines a property and returns its live typed handle.
   *
   * <p>The default value is applied immediately. If the backing data already
   * contains a value for this path (e.g., from a loaded JSON file), the loaded
   * value takes precedence.
   *
   * @param path      the key path for this property
   * @param def       the default value
   * @param validator the validator, or {@code null} for no validation
   * @param comment   the comment, or {@code null} for no comment
   * @param <T>       the value type
   * @return the typed property handle
   */
  public <T> Config<T> define(String path, T def,
                              @Nullable Validator<T> validator,
                              @Nullable String comment) {
    Config<T> cv = new Config<>(path, def, validator, comment);
    cv.bind(data);
    values.put(path, cv);
    if (!data.contains(path)) {
      data.put(path, def);
    }
    return cv;
  }

  /**
   * Returns a previously defined property handle.
   *
   * @param path the property path
   * @param <T>  the property type
   * @return the config value handle
   * @throws IllegalArgumentException if the path has not been defined
   */
  @SuppressWarnings("unchecked")
  public <T> Config<T> get(String path) {
    Config<?> value = values.get(path);
    if (value == null) {
      throw new IllegalArgumentException("Unknown config key: " + path);
    }
    return (Config<T>) value;
  }

  /**
   * Returns all defined property handles in definition order.
   *
   * @return an unmodifiable view of the property map
   */
  public Map<String, Config<?>> values() {
    return values;
  }

  /**
   * Reloads from the original file, resetting all values to defaults and then overlaying the file contents.
   *
   * @throws ConfigException       if the file cannot be read or parsed
   * @throws IllegalStateException if no file path has been set
   */
  public void reload() {
    if (filePath == null) {
      throw new IllegalStateException("No file path set");
    }
    reload(filePath);
  }

  /**
   * Reloads from the given file, resetting all values to defaults and then overlaying the file contents.
   *
   * @param path the JSON file to load
   * @throws ConfigException if the file cannot be read or parsed
   */
  public void reload(Path path) {
    try {
      reload(Files.readString(path));
    } catch (IOException e) {
      throw new ConfigException("Failed to read config file: " + path, e);
    }
  }

  /**
   * Reloads from a JSON string, resetting to defaults and then overlaying the parsed values.
   *
   * @param json the JSON text
   * @throws ConfigException if the JSON is malformed
   */
  public void reload(String json) {
    for (Config<?> cv : values.values()) {
      data.put(cv.path(), cv.defaultValue());
    }

    if (json.isBlank()) {
      return;
    }
    CompoundTag parsed;
    try {
      parsed = JsonUtil.parse(json);
    } catch (Exception e) {
      throw new ConfigException("Failed to parse JSON", e);
    }
    for (Map.Entry<String, @Nullable Object> entry : parsed.entrySet()) {
      data.put(entry.getKey(), entry.getValue());
    }
  }

  /**
   * Saves the current values to the original file.
   *
   * @throws IllegalStateException if no file path has been set
   * @throws ConfigException       if the file cannot be written
   */
  public void save() {
    if (filePath == null) {
      throw new IllegalStateException(
          "No file path — use save(Path) or construct with ConfigSpec.load(path)");
    }
    save(filePath);
  }

  /**
   * Saves the current values to the given file.
   *
   * @param path the file to write
   * @throws ConfigException if the file cannot be written
   */
  public void save(Path path) {
    try {
      Files.writeString(path, dump());
      this.filePath = path;
    } catch (IOException e) {
      throw new ConfigException("Failed to write config file: " + path, e);
    }
  }

  /**
   * Serializes the current values to a pretty-printed JSON string.
   *
   * @return the JSON representation
   */
  public String dump() {
    return JsonUtil.dumpPrettily(data);
  }

  /**
   * Returns the raw backing data store.
   *
   * @return the backing compound tag
   */
  public CompoundTag data() {
    return data;
  }
}
