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

/**
 * A configuration backed by a {@link ConfigSpec} and a JSON source.
 *
 * <p>Binds every {@link ConfigValue} in the spec to a shared backing
 * store. Reads and writes go through the value handles directly;
 * changes are persisted on {@link #save()}.
 *
 * @see ConfigSpec
 * @see ConfigValue
 */
public final class Config {
  private final ConfigSpec spec;
  private final CompoundTag data = new CompoundTag();
  private @Nullable Path filePath;

  private Config(ConfigSpec spec) {
    this.spec = spec;
    bindAndApplyDefaults();
  }

  /**
   * Creates a new config populated with the default values from the
   * given spec.
   *
   * @param spec the configuration schema
   * @return a new config
   */
  public static Config of(ConfigSpec spec) {
    return new Config(spec);
  }

  /**
   * Creates a new config by loading values from a JSON file.
   *
   * @param spec     the configuration schema
   * @param filePath the path to a JSON file
   * @return a new config with values loaded from the file
   * @throws ConfigException if the file cannot be read or parsed
   */
  public static Config of(ConfigSpec spec, Path filePath) {
    Config cfg = new Config(spec);
    cfg.filePath = filePath;
    cfg.load(filePath);
    return cfg;
  }

  /**
   * Creates a new config by loading values from a JSON string.
   *
   * @param spec the configuration schema
   * @param json the JSON text
   * @return a new config with values parsed from the string
   * @throws ConfigException if the JSON is malformed
   */
  public static Config of(ConfigSpec spec, String json) {
    Config cfg = new Config(spec);
    cfg.load(json);
    return cfg;
  }

  /**
   * Reloads values from a JSON file into the backing store.
   *
   * <p>Existing {@link ConfigValue} handles remain valid after the reload.
   *
   * @param path the JSON file to read
   * @throws ConfigException if the file cannot be read or parsed
   */
  public void load(Path path) {
    try {
      load(Files.readString(path));
    } catch (IOException e) {
      throw new ConfigException("Failed to read config file: " + path, e);
    }
  }

  /**
   * Reloads values from a JSON string into the backing store.
   *
   * <p>Existing {@link ConfigValue} handles remain valid after the reload.
   *
   * @param json the JSON text
   * @throws ConfigException if the JSON is malformed
   */
  public void load(String json) {
    data.clear();
    bindAndApplyDefaults();

    if (json.isBlank()) {
      return;
    }
    try {
      CompoundTag parsed = JsonUtil.parse(json);
      for (var entry : parsed.entrySet()) {
        data.put(entry.getKey(), entry.getValue());
      }
    } catch (Exception e) {
      throw new ConfigException("Failed to parse JSON", e);
    }
  }

  /**
   * Saves the current configuration to the file it was loaded from.
   *
   * @throws IllegalStateException if no file path was set at creation time
   * @throws ConfigException       if writing fails
   */
  public void save() {
    if (filePath == null) {
      throw new IllegalStateException(
          "No file path — use save(Path) or construct with Config.of(spec, path)");
    }
    save(filePath);
  }

  /**
   * Saves the current configuration to the given JSON file.
   *
   * @param path the file to write
   * @throws ConfigException if writing fails
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
   * Serializes the current configuration to a pretty-printed JSON string.
   *
   * @return the JSON representation of the current state
   */
  public String dump() {
    return JsonUtil.dumpPrettily(data);
  }

  /**
   * Returns the raw backing data store.
   *
   * @return the backing data
   */
  public CompoundTag data() {
    return data;
  }

  private void bindAndApplyDefaults() {
    for (ConfigValue<?> cv : spec.values().values()) {
      cv.bind(data);
      data.put(cv.path(), cv.defaultValue());
    }
  }
}
