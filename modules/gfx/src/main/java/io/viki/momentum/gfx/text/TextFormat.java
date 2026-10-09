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

package io.viki.momentum.gfx.text;

import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.gfx.tint.Gradient;

/**
 * Immutable text styling attributes for a span of text.
 *
 * <p>A {@code TextFormat} combines a {@link FontInsta}, a text gradient and font style
 * flags. Each wither method returns a new instance, leaving
 * the original unchanged.
 *
 * <p>Use {@link #of()} for a default style, or {@link Builder} for fluent
 * construction.
 *
 * @param font      the font used to render the text
 * @param tint      the text gradient
 * @param fontStyle a bitmask of style flags from {@link Font}
 * @see Font#REGULAR
 * @see Font#BOLD
 * @see Font#ITALIC
 */
public record TextFormat(FontInsta font, Gradient tint, int fontStyle) {
  /**
   * Returns a default style using the built-in font, white gradient, regular
   * weight, and the default font size.
   *
   * @return a default text style
   */
  public static TextFormat of() {
    return new TextFormat(
        FallbackFont.acquire(),
        Color.WHITE,
        Font.REGULAR
    );
  }

  /**
   * Returns a copy of this style with the given font.
   *
   * @param font the replacement font
   * @return a new style with the updated font
   */
  public TextFormat font(FontInsta font) {
    return new TextFormat(font, tint, fontStyle);
  }

  /**
   * Returns a copy of this style with the given text gradient.
   *
   * @param gradient the replacement gradient
   * @return a new style with the updated gradient
   */
  public TextFormat tint(Gradient gradient) {
    return new TextFormat(font, gradient, fontStyle);
  }

  /**
   * Returns a copy of this style with the given font size.
   *
   * @param fontSize the replacement font size in pixels
   * @return a new style with the updated font size
   */
  public TextFormat size(float fontSize) {
    return new TextFormat(font.resized(fontSize), tint, fontStyle);
  }

  /**
   * Returns a copy with the replacement style flags.
   *
   * @param fontStyle the replacement style flags
   * @return a new style with the updated flags
   */
  public TextFormat style(int fontStyle) {
    return new TextFormat(font, tint, fontStyle);
  }

  /**
   * Fluent builder for {@link TextFormat} instances.
   *
   * <p>Defaults: gradient is {@link Color#WHITE}, style is
   * {@link Font#REGULAR}, font size is {@link Font#DEFAULT_SIZE}.
   */
  public static final class Builder {
    private FontInsta font;
    private Gradient gradient = Color.WHITE;
    private int fontStyle = Font.REGULAR;

    /**
     * Creates a builder for the given font.
     *
     * @param font the font for the style being built
     */
    public Builder(FontInsta font) {
      this.font = font;
    }

    /**
     * Sets the text gradient.
     *
     * @param gradient the text gradient
     * @return this builder, for chaining
     */
    public Builder tint(Gradient gradient) {
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
      this.font = font.resized(fontSize);
      return this;
    }

    /**
     * Builds the {@link TextFormat}.
     *
     * @return a new style with the configured attributes
     */
    public TextFormat build() {
      return new TextFormat(font, gradient, fontStyle);
    }
  }
}
