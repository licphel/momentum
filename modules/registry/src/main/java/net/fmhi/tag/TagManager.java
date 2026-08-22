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

package net.fmhi.tag;

import net.fmhi.codec.nbt.CompoundTag;
import net.fmhi.codec.nbt.ListTag;
import net.fmhi.registry.Registry;
import net.fmhi.registry.RegistryEntry;
import net.fmhi.util.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * Manages all tags for a specific registry.
 *
 * <p>Tags are loaded from data files in the
 * {@code data/<namespace>/tags/<registry_path>/<tag_name>.json} format.
 * Each tag file contains a {@code "values"} list of entry identifiers.
 * An entry prefixed with {@code #} references another tag.
 *
 * <p>Tag maps use a {@code "mappings"} object instead of {@code "values"},
 * associating each entry identifier with a value:
 * <pre>{@code {"mappings": {"fmhi:stone": 5, "#fmhi:natural_blocks": 10}}}</pre>
 *
 * <p>After all tags are loaded, call {@link #resolve()} to compute the
 * fully resolved entry sets (flattening tag references).
 *
 * @param <T> the registry entry type
 */
public final class TagManager<T extends RegistryEntry<T>> {
  private final Registry<T> registry;
  private final Map<Identifier, SimpleTag<T>> tags = new LinkedHashMap<>();
  private boolean resolved;

  /**
   * Creates a tag manager for the given registry.
   *
   * @param registry the registry to resolve entries against
   */
  public TagManager(Registry<T> registry) {
    this.registry = registry;
  }

  /**
   * Loads a tag from a compound tag parsed from a JSON data file.
   *
   * <p>The expected format is:
   * <pre>{@code {"values": ["fmhi:stone", "#fmhi:natural_blocks"]}}</pre>
   *
   * @param key the tag key (derived from the file path)
   * @param tag the compound tag containing the {@code "values"} list
   */
  public void load(Identifier key, CompoundTag tag) {
    if (resolved) {
      throw new IllegalStateException("Cannot load tags after resolution");
    }
    ListTag values = tag.getList("values");
    if (values == null) {
      tags.put(key, new SimpleTag<>(key, Set.of(), Set.of()));
      return;
    }
    Set<Identifier> directIds = new LinkedHashSet<>();
    Set<Identifier> tagRefs = new LinkedHashSet<>();
    for (int i = 0; i < values.size(); i++) {
      String raw = values.getString(i, "");
      if (raw.isBlank()) {
        continue;
      }
      if (raw.startsWith("#")) {
        tagRefs.add(Identifier.of(raw.substring(1)));
      } else {
        directIds.add(Identifier.of(raw));
      }
    }
    tags.put(key, new SimpleTag<>(key, Collections.unmodifiableSet(directIds),
        Collections.unmodifiableSet(tagRefs)));
  }

  /**
   * Loads a tag map from a compound tag parsed from a JSON data file.
   *
   * <p>The expected format is:
   * <pre>{@code {"mappings": {"fmhi:stone": 5, "#fmhi:natural_blocks": 10}}}</pre>
   *
   * <p>Each key in the {@code "mappings"} object is an entry identifier
   * (or tag reference prefixed with {@code #}). The value is converted
   * using the supplied {@code valueExtractor}.
   *
   * @param key            the tag key
   * @param tag            the compound tag containing {@code "mappings"}
   * @param valueExtractor converts a raw NBT value to type {@code V}
   * @param <V>            the mapped value type
   */
  public <V> void loadMap(Identifier key, CompoundTag tag,
                          Function<Object, V> valueExtractor) {
    if (resolved) {
      throw new IllegalStateException("Cannot load tags after resolution");
    }
    CompoundTag mappingsTag = tag.getCompound("mappings");
    if (mappingsTag == null) {
      tags.put(key, new SimpleTagMap<>(key, Map.of(), Map.of()));
      return;
    }
    Map<Identifier, V> rawMappings = new LinkedHashMap<>();
    Map<Identifier, V> tagRefMappings = new LinkedHashMap<>();
    for (Map.Entry<String, @Nullable Object> entry : mappingsTag.entrySet()) {
      String rawKey = entry.getKey();
      if (rawKey.isBlank()) {
        continue;
      }

      Object obj = entry.getValue();
      if (obj != null) {
        V value = valueExtractor.apply(obj);

        if (rawKey.startsWith("#")) {
          tagRefMappings.put(Identifier.of(rawKey.substring(1)), value);
        } else {
          rawMappings.put(Identifier.of(rawKey), value);
        }
      }
    }
    tags.put(key, new SimpleTagMap<>(key,
        Collections.unmodifiableMap(rawMappings),
        Collections.unmodifiableMap(tagRefMappings)));
  }

  /**
   * Resolves all loaded tags, computing the full entry sets including
   * transitive tag references.
   *
   * <p>After this call, no more tags may be loaded. Circular tag
   * references are detected and skipped. Tag map mappings are also
   * resolved against the registry.
   */
  public void resolve() {
    if (resolved) {
      return;
    }
    resolved = true;
    Set<Identifier> visiting = new HashSet<>();
    for (SimpleTag<T> tag : tags.values()) {
      resolveTag(tag, visiting);
    }
    for (SimpleTag<T> tag : tags.values()) {
      if (tag instanceof SimpleTagMap<T, ?> tagMap) {
        resolveMappings(tagMap);
      }
    }
  }

  private void resolveTag(SimpleTag<T> tag, Set<Identifier> visiting) {
    if (!visiting.add(tag.key)) {
      // Circular reference — skip
      return;
    }
    Set<T> entries = new LinkedHashSet<>();
    for (Identifier id : tag.idsWithoutRefs) {
      T value = registry.get(id);
      if (value != null) {
        entries.add(value);
      }
    }
    for (Identifier ref : tag.tagRefs) {
      SimpleTag<T> refTag = tags.get(ref);
      if (refTag != null) {
        resolveTag(refTag, visiting);
        entries.addAll(refTag.resolvedEntries);
      }
    }
    tag.resolvedEntries.addAll(entries);
    visiting.remove(tag.key);
  }

  private <V> void resolveMappings(SimpleTagMap<T, V> tagMap) {
    for (Map.Entry<Identifier, V> entry : tagMap.rawMappings.entrySet()) {
      T registryValue = registry.get(entry.getKey());
      if (registryValue != null) {
        tagMap.resolvedMappings.put(registryValue, entry.getValue());
      }
    }
    for (Map.Entry<Identifier, V> ref : tagMap.tagRefMappings.entrySet()) {
      SimpleTag<T> refTag = tags.get(ref.getKey());
      if (refTag != null) {
        for (T refEntry : refTag.resolvedEntries) {
          tagMap.resolvedMappings.put(refEntry, ref.getValue());
        }
      }
    }
  }

  /**
   * Returns the tag for the given key.
   *
   * @param key the tag key
   * @return the tag, or {@code null} if not loaded
   */
  public @Nullable Tag<T> get(Identifier key) {
    return tags.get(key);
  }

  /**
   * Returns the tag map for the given key.
   *
   * @param key the tag key
   * @param <V> mapped value type
   * @return the tag map, or {@code null} if not loaded or not a tag map
   */
  @SuppressWarnings("unchecked")
  public <V> @Nullable TagMap<T, V> getMap(Identifier key) {
    Tag<T> tag = tags.get(key);
    if (tag instanceof TagMap<?, ?> tagMap) {
      return (TagMap<T, V>) tagMap;
    }
    return null;
  }

  /**
   * Returns whether an entry belongs to the given tag.
   *
   * @param key   the tag key
   * @param value the entry to test
   * @return {@code true} if the entry is tagged
   */
  public boolean isIn(Identifier key, T value) {
    Tag<T> tag = get(key);
    return tag != null && tag.contains(value);
  }

  /**
   * Returns all loaded tag keys.
   *
   * @return an unmodifiable set of tag keys
   */
  public Set<Identifier> keys() {
    return Collections.unmodifiableSet(tags.keySet());
  }
}
