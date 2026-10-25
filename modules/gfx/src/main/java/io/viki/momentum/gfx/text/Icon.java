package io.viki.momentum.gfx.text;

import io.viki.momentum.gfx.texture.Drawable2D;
import org.jspecify.annotations.Nullable;

/**
 * Represents an inline drawable embedded in rich text.
 *
 * <p>Dimensions and baseline use logical text units. A positive infinite width fills the available
 * line width and contributes zero intrinsic width when the line is unconstrained. The drawable is
 * borrowed rather than owned, and is used on the text renderer's thread. The record is immutable;
 * thread safety of the drawable follows its own contract.
 *
 * @param width    logical advance width, or positive infinity to fill the available line width
 * @param height   logical drawable height
 * @param baseline baseline position measured from the drawable's top edge
 * @param drawable borrowed drawable rendered for this inline icon
 */
public record Icon(float width, float height, float baseline, Drawable2D drawable) implements Meta {
  /**
   * Creates an inline icon with validated dimensions and baseline.
   *
   * @param width    logical advance width, or positive infinity to fill the available line width
   * @param height   logical drawable height
   * @param baseline baseline position measured from the drawable's top edge
   * @param drawable borrowed drawable rendered for this inline icon
   * @throws IllegalArgumentException if a dimension is negative or the baseline exceeds the height
   */
  public Icon {
    if (width < 0 || height < 0 || baseline < 0 || baseline > height) {
      throw new IllegalArgumentException("Drawable dimensions must be nonnegative and baseline within its height");
    }
  }

  /**
   * Creates an icon whose baseline is at the drawable's bottom edge.
   *
   * @param drawable borrowed drawable rendered for this inline icon
   * @param width    logical advance width, or positive infinity to fill the available line width
   * @param height   logical drawable height and baseline position
   * @return the configured inline icon
   * @throws IllegalArgumentException if a dimension is negative
   */
  public static Icon of(Drawable2D drawable, float width, float height) {
    return new Icon(width, height, height, drawable);
  }

  /**
   * Creates an empty literal carrying a bottom-aligned inline icon.
   *
   * @param drawable borrowed drawable rendered for this inline icon
   * @param width    logical advance width, or positive infinity to fill the available line width
   * @param height   logical drawable height and baseline position
   * @return an empty literal with the icon attached as metadata
   * @throws IllegalArgumentException if a dimension is negative
   */
  public static Literal make(Drawable2D drawable, float width, float height) {
    return make(drawable, width, height, height);
  }

  /**
   * Creates an empty literal carrying an inline icon with an explicit baseline.
   *
   * @param drawable borrowed drawable rendered for this inline icon
   * @param width    logical advance width, or positive infinity to fill the available line width
   * @param height   logical drawable height
   * @param baseline baseline position measured from the drawable's top edge
   * @return an empty literal with the icon attached as metadata
   * @throws IllegalArgumentException if a dimension is negative or {@code baseline} exceeds the height
   */
  public static Literal make(Drawable2D drawable, float width, float height, float baseline) {
    return Literal.of("").with(new Icon(width, height, baseline, drawable));
  }

  /**
   * Finds the first inline icon in a metadata array.
   *
   * @param values metadata attachments, or {@code null}
   * @return the first icon attachment, or {@code null} if none is present
   */
  public static @Nullable Icon find(Meta @Nullable [] values) {
    if (values != null) {
      for (Meta value : values) {
        if (value instanceof Icon drawable) {
          return drawable;
        }
      }
    }
    return null;
  }

  /**
   * Resolves the effective advance width for a line.
   *
   * <p>An unconstrained line is represented by {@link Float#MAX_VALUE}; an infinite icon width
   * contributes zero width in that case.
   *
   * @param availableWidth available line width, or {@link Float#MAX_VALUE} when unconstrained
   * @return this icon's width, the available width, or zero for an unconstrained infinite-width icon
   */
  public float layoutWidth(float availableWidth) {
    return width == Float.POSITIVE_INFINITY
        ? availableWidth == Float.MAX_VALUE ? 0 : availableWidth : width;
  }
}
