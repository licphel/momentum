package net.fmhi.fml.tag;

import net.fmhi.fml.Identifier;
import net.fmhi.util.InternalApi;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

@InternalApi
class SimpleTag<T> implements Tag<T> {
  final Identifier key;
  final Set<Identifier> idsWithoutRefs;
  final Set<Identifier> tagRefs;
  final Set<T> resolvedEntries = new HashSet<>();

  SimpleTag(Identifier key, Set<Identifier> idsWithoutRefs, Set<Identifier> tagRefs) {
    this.key = key;
    this.idsWithoutRefs = idsWithoutRefs;
    this.tagRefs = tagRefs;
  }

  @Override
  public Identifier key() {
    return key;
  }

  @Override
  public boolean contains(T value) {
    return resolvedEntries.contains(value);
  }

  @Override
  public Collection<T> values() {
    return resolvedEntries;
  }

  @Override
  public Stream<T> stream() {
    return resolvedEntries.stream();
  }

  @Override
  public String toString() {
    return "Tag[" + key + ", entries=" + resolvedEntries.size() + "]";
  }
}
