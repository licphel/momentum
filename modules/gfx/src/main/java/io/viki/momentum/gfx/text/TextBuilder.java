package io.viki.momentum.gfx.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds immutable, flattened text values. Each build is independent of later builder changes.
 * Builders are mutable and confined to one thread; they are not renderable text.
 */
public final class TextBuilder {
  private final List<Literal> spans = new ArrayList<>();
  private float maxWidth = TextLayout.DEFAULT.maxWidth();
  private boolean flipY = TextLayout.DEFAULT.flipY();
  private boolean justify = TextLayout.DEFAULT.justify();
  private float lineSpacing = TextLayout.DEFAULT.lineSpacing();
  private int maxLines = TextLayout.DEFAULT.maxLines();

  /** Creates an empty builder with the default layout policy. */
  public TextBuilder() {
  }

  /**
   * Creates a builder containing the source content and layout policy.
   *
   * @param source the text to copy into the builder
   */
  public TextBuilder(Text source) {
    append(source);
    TextLayout layout = source.layout();
    maxWidth = layout.maxWidth();
    flipY = layout.flipY();
    justify = layout.justify();
    lineSpacing = layout.lineSpacing();
    maxLines = layout.maxLines();
  }

  /**
   * Appends the source spans without replacing this builder's layout policy.
   *
   * @param text the text whose spans are appended
   * @return this builder
   */
  public TextBuilder append(Text text) {
    if (text instanceof Literal literal) {
      spans.add(literal);
    } else {
      spans.addAll(text.spans());
    }
    return this;
  }

  /**
   * Appends a newline that continues the last span's style, or uses the default style if empty.
   *
   * @return this builder
   */
  public TextBuilder newLine() {
    Literal newline = spans.isEmpty() ? Literal.of("\n") : Literal.of("\n").with(spans.getLast().format());
    return append(newline);
  }

  /**
   * Returns the maximum width configured for each line.
   *
   * @return the maximum line width in text layout units
   */
  public float maxWidth() {
    return maxWidth;
  }

  /**
   * Sets the maximum width configured for each line.
   *
   * @param value maximum line width in text layout units; {@link Float#MAX_VALUE} means unbounded
   * @return this builder
   */
  public TextBuilder maxWidth(float value) {
    maxWidth = value;
    return this;
  }

  /**
   * Sets whether the text uses a downward-oriented Y axis.
   *
   * @param value {@code true} for a downward-oriented Y axis
   * @return this builder
   */
  public TextBuilder flipY(boolean value) {
    flipY = value;
    return this;
  }

  /**
   * Sets whether eligible lines are expanded to the configured maximum width.
   *
   * @param value {@code true} to justify eligible lines
   * @return this builder
   */
  public TextBuilder justify(boolean value) {
    justify = value;
    return this;
  }

  /**
   * Sets the multiplier applied to the font's normal line spacing.
   *
   * @param value line spacing multiplier, validated when {@link #build()} is called
   * @return this builder
   */
  public TextBuilder lineSpacing(float value) {
    lineSpacing = value;
    return this;
  }

  /**
   * Sets the maximum number of visible lines.
   *
   * @param value maximum line count; must not be negative when {@link #build()} is called
   * @return this builder
   */
  public TextBuilder maxLines(int value) {
    maxLines = value;
    return this;
  }

  /**
   * Builds an immutable text value from the current spans and layout policy.
   * Later changes to this builder do not affect the returned text.
   *
   * @return an immutable text value
   * @throws IllegalArgumentException if the configured width is less than zero, line spacing is less
   *         than or equal to zero, or the maximum line count is negative
   */
  public Text build() {
    TextLayout layout = new TextLayout(maxWidth, flipY, justify, lineSpacing, maxLines);
    if (spans.size() == 1 && layout.equals(TextLayout.DEFAULT)) {
      return spans.getFirst();
    }
    return new BuiltText(spans, layout);
  }
}
