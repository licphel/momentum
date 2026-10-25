package io.viki.momentum.gfx.text;

/**
 * Immutable line layout policy that can be shared by builders and completed text values.
 *
 * @param maxWidth maximum width of each line in text layout units;
 *                 {@link Float#MAX_VALUE} means unbounded
 * @param flipY whether text uses a downward-oriented Y axis
 * @param justify whether eligible lines are expanded to the maximum width
 * @param lineSpacing multiplier applied to the font's normal line spacing
 * @param maxLines maximum number of visible lines
 */
public record TextLayout(float maxWidth, boolean flipY, boolean justify, float lineSpacing, int maxLines) {
  /** Default policy with unbounded width, downward-facing text, normal spacing, and no line limit. */
  public static final TextLayout DEFAULT = new TextLayout(Float.MAX_VALUE, true, false, 1F, Integer.MAX_VALUE);

  /**
   * Creates a text layout policy.
   *
   * @param maxWidth maximum width of each line in text layout units;
   *                 {@link Float#MAX_VALUE} means unbounded
   * @param flipY whether text uses a downward-oriented Y axis
   * @param justify whether eligible lines are expanded to the maximum width
   * @param lineSpacing multiplier applied to the font's normal line spacing
   * @param maxLines maximum number of visible lines
   * @throws IllegalArgumentException if {@code maxWidth} is less than zero, {@code lineSpacing} is less
   *         than or equal to zero, or {@code maxLines} is negative
   */
  public TextLayout {
    if (maxWidth < 0 || lineSpacing <= 0 || maxLines < 0) {
      throw new IllegalArgumentException("Invalid text layout: width=" + maxWidth + ", lineSpacing=" + lineSpacing + ", maxLines=" + maxLines);
    }
  }
}
