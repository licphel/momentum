package io.viki.momentum.gfx.text;

import java.util.List;
import java.util.function.IntPredicate;

/**
 * Immutable font and logical-size combination, optionally combining ordered fallback fonts.
 *
 * <p>The underlying fonts retain their own threading and resource rules.
 * This value does not own or close its fonts.
 */
public final class FontInsta {
  private final Font font;
  private final float size;
  private final IntPredicate characters;
  private final List<FontInsta> combinations;

  private FontInsta(Font font, float size, IntPredicate characters, List<FontInsta> combinations) {
    if (size <= 0) {
      throw new IllegalArgumentException("Font size must be positive: " + size);
    }
    this.font = font;
    this.size = size;
    this.characters = characters;
    this.combinations = combinations;
  }

  /**
   * Creates a font entry that accepts every Unicode code point.
   *
   * @param font the font used by the entry
   * @param size the logical font size, which must be positive
   * @return a font entry without fallback combinations
   */
  public static FontInsta of(Font font, float size) {
    return of(font, size, codepoint -> true);
  }

  /**
   * Creates a font entry restricted to the supplied Unicode code points.
   *
   * @param font the font used by the entry
   * @param size the logical font size, which must be positive
   * @param characters predicate identifying code points this entry may serve
   * @return a font entry without fallback combinations
   */
  public static FontInsta of(Font font, float size, IntPredicate characters) {
    return new FontInsta(font, size, characters, List.of());
  }

  /**
   * Combines font entries in priority order, using the last entry as the fallback.
   *
   * @param combinations font entries ordered from highest to lowest priority
   * @return a combined font value
   * @throws IllegalArgumentException if no font entries are supplied
   */
  public static FontInsta of(FontInsta... combinations) {
    if (combinations.length == 0) {
      throw new IllegalArgumentException("At least one font combination is required");
    }
    if (combinations.length == 1) {
      return combinations[0];
    }
    FontInsta first = combinations[0];
    return new FontInsta(first.font, first.size, codepoint -> true, List.of(combinations));
  }

  /**
   * Returns the font associated with this entry.
   *
   * @return the underlying font
   */
  public Font font() {
    return font;
  }

  /**
   * Returns the logical size associated with this entry.
   *
   * @return the logical font size
   */
  public float size() {
    return size;
  }

  /**
   * Returns whether this value contains ordered fallback entries.
   *
   * @return {@code true} when this value combines multiple entries
   */
  public boolean isCombined() {
    return !combinations.isEmpty();
  }

  /**
   * Resolves the entry that should provide a glyph for a Unicode code point.
   *
   * <p>Combined values are searched in priority order. If no entry reports coverage, the last
   * entry is returned as the missing-glyph fallback. Resolution runs in O(n) time for {@code n}
   * fallback entries.
   *
   * @param codepoint the Unicode code point to resolve
   * @return the entry selected for the code point
   */
  public FontInsta resolve(int codepoint) {
    if (!isCombined()) {
      return this;
    }
    for (FontInsta combination : combinations) {
      FontInsta candidate = combination.resolve(codepoint);
      if (candidate.characters.test(codepoint) && candidate.font.hasGlyph(codepoint)) {
        return candidate;
      }
    }
    return combinations.getLast().resolve(codepoint);
  }

  /**
   * Returns a resized value while preserving fallback order and character filters.
   *
   * @param size the replacement logical font size, which must be positive
   * @return a resized value; this value remains unchanged
   * @throws IllegalArgumentException if {@code size} is not positive
   */
  public FontInsta resized(float size) {
    if (!isCombined()) {
      return of(font, size, characters);
    }
    return of(combinations.stream().map(value -> value.resized(size)).toArray(FontInsta[]::new));
  }

  /**
   * Returns the maximum ascender across this entry and all fallback entries.
   *
   * @return the maximum ascender in logical drawing units
   */
  public float ascender() {
    float result = font.metrics().ascender() * size;
    for (FontInsta combination : combinations) {
      result = Math.max(result, combination.ascender());
    }
    return result;
  }

  /**
   * Returns the minimum descender across this entry and all fallback entries.
   *
   * @return the minimum descender in logical drawing units
   */
  public float descender() {
    float result = font.metrics().descender() * size;
    for (FontInsta combination : combinations) {
      result = Math.min(result, combination.descender());
    }
    return result;
  }
}
