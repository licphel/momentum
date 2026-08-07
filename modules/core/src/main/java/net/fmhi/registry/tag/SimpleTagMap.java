package net.fmhi.registry.tag;

import net.fmhi.registry.Identifier;
import net.fmhi.util.InternalApi;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@InternalApi
final class SimpleTagMap<T, V> extends SimpleTag<T> implements TagMap<T, V> {
  final Map<Identifier, V> rawMappings;
  final Map<Identifier, V> tagRefMappings;
  final Map<T, V> resolvedMappings = new LinkedHashMap<>();

  SimpleTagMap(Identifier key,
               Map<Identifier, V> rawMappings,
               Map<Identifier, V> tagRefMappings) {
    super(key, Set.copyOf(rawMappings.keySet()), Set.copyOf(tagRefMappings.keySet()));
    this.rawMappings = rawMappings;
    this.tagRefMappings = tagRefMappings;
  }

  @Override
  public @Nullable V map(T value) {
    return resolvedMappings.get(value);
  }

  @Override
  public Map<T, V> mappings() {
    return Collections.unmodifiableMap(resolvedMappings);
  }
}
