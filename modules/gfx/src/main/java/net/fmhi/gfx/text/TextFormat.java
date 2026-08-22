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

package net.fmhi.gfx.text;

import net.fmhi.gfx.util.fast2d.tint.Gradient;
import net.fmhi.gfx.util.fast2d.tint.SimpleGradient;
import net.fmhi.math.Color;

/**
 * Immutable text styling attributes for a span of text.
 *
 * <p>A {@code TextFormat} combines a {@link Font}, a text gradient, font style
 * flags, and a font size. Each wither method returns a new instance, leaving
 * the original unchanged.
 *
 * <p>Use {@link #of()} for a default style, or {@link Builder} for fluent
 * construction.
 *
 * @param font      the font used to render the text
 * @param gradient  the text gradient
 * @param fontStyle a bitmask of style flags from {@link Font}
 * @param fontSize  the font size in pixels
 * @see Font#REGULAR
 * @see Font#BOLD
 * @see Font#ITALIC
 */
public record TextFormat(Font font, Gradient gradient, int fontStyle, float fontSize) {
  /**
   * Returns a default style using the built-in font, white gradient, regular
   * weight, and the default font size.
   *
   * @return a default text style
   */
  public static TextFormat of() {
    return new TextFormat(
        FallbackFont.acquire(),
        new SimpleGradient(),
        Font.REGULAR,
        Font.DEFAULT_SIZE
    );
  }

  /**
   * Returns a copy of this style with the given font.
   *
   * @param font the replacement font
   * @return a new style with the updated font
   */
  public TextFormat font(Font font) {
    return new TextFormat(font, gradient, fontStyle, fontSize);
  }

  /**
   * Returns a copy of this style with the given text gradient.
   *
   * @param color the replacement gradient
   * @return a new style with the updated gradient
   */
  public TextFormat color(Color color) {
    return gradient(new SimpleGradient(color));
  }

  /**
   * Returns a copy of this style with the given text gradient.
   *
   * @param gradient the replacement gradient
   * @return a new style with the updated gradient
   */
  public TextFormat gradient(Gradient gradient) {
    return new TextFormat(font, gradient, fontStyle, fontSize);
  }

  /**
   * Returns a copy of this style with the given font size.
   *
   * @param fontSize the replacement font size in pixels
   * @return a new style with the updated font size
   */
  public TextFormat size(float fontSize) {
    return new TextFormat(font, gradient, fontStyle, fontSize);
  }

  /**
   * Returns a copy of this style with the given font.
   *
   * @param font the replacement font
   * @return a new style with the updated font
   */
  public TextFormat style(Font font) {
    return new TextFormat(font, gradient, fontStyle, fontSize);
  }

  /**
   * Fluent builder for {@link TextFormat} instances.
   *
   * <p>Defaults: gradient is {@link Color#WHITE}, style is
   * {@link Font#REGULAR}, font size is {@link Font#DEFAULT_SIZE}.
   */
  public static final class Builder {
    private final Font font;
    private Gradient gradient = new SimpleGradient();
    private int fontStyle = Font.REGULAR;
    private float fontSize = Font.DEFAULT_SIZE;

    /**
     * Creates a builder for the given font.
     *
     * @param font the font for the style being built
     */
    public Builder(Font font) {
      this.font = font;
    }

    /**
     * Sets the text gradient.
     *
     * @param color the text gradient
     * @return this builder, for chaining
     */
    public Builder color(Color color) {
      this.gradient = new SimpleGradient(color);
      return this;
    }

    /**
     * Sets the text gradient.
     *
     * @param gradient the text gradient
     * @return this builder, for chaining
     */
    public Builder color(Gradient gradient) {
      this.gradient = gradient;
      return this;
    }

    /**
     * Adds style flags to the bitmask.
     *
     * <p>Multiple calls combine flags via bitwise OR, so
     * {@code style(BOLD).style(ITALIC)} produces bold-italic.
     *
     * @param fontStyle the style flags to add, from {@link Font}
     * @return this builder, for chaining
     */
    public Builder style(int fontStyle) {
      this.fontStyle |= fontStyle;
      return this;
    }

    /**
     * Sets the font size.
     *
     * @param fontSize the font size in pixels
     * @return this builder, for chaining
     */
    public Builder size(float fontSize) {
      this.fontSize = fontSize;
      return this;
    }

    /**
     * Builds the {@link TextFormat}.
     *
     * @return a new style with the configured attributes
     */
    public TextFormat build() {
      return new TextFormat(this.font, this.gradient, this.fontStyle, this.fontSize);
    }
  }
}
