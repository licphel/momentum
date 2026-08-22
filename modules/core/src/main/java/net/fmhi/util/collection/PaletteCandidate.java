package net.fmhi.util.collection;

/**
 * A type whose values map to dense integer identifiers for compact storage.
 *
 * <p>Implementations assign each distinct value a unique integer, enabling
 * palette-based encoding in a {@link MappingArray}. The mapping is stable
 * across the lifetime of the value set.
 */
public interface PaletteCandidate {
  /**
   * Returns the unique integer identity of this value.
   *
   * @return the integer identity
   */
  int identity();
}
