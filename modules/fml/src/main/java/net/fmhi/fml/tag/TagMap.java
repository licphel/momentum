package net.fmhi.fml.tag;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * A tag that maps each entry to an associated value.
 *
 * <p>Unlike a plain {@link Tag}, which simply groups entries, a
 * {@code TagMap} associates a value of type {@code V} with each member.
 *
 * @param <T> the registry entry type
 * @param <V> the mapped value type
 */
public interface TagMap<T, V> extends Tag<T> {
  /**
   * Returns the value mapped to the given entry.
   *
   * @param value the entry to look up
   * @return the mapped value, or {@code null} if the entry is not in this mapping
   */
  @Nullable V map(T value);

  /**
   * Returns all resolved mappings from entries to values.
   *
   * @return an unmodifiable map of entries to values
   */
  Map<T, V> mappings();
}
