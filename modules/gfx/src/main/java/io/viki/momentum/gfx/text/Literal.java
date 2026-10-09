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

import io.viki.momentum.gfx.text.raster.Raster;
import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.gfx.tint.Gradient;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * A single span of uniformly styled text.
 *
 * <p>A {@code Literal} is the leaf node of a text component tree. It carries
 * a fixed text string, a {@link TextFormat}, and optional {@link Meta}
 * attachments such as hyperlinks. Layout parameters are inherited from the
 * enclosing {@link MutableText}.
 *
 * <p>Instances are created via {@link #of(String)} for static text.
 * Style and metadata are set fluently with
 * {@link #with(TextFormat)} and {@link #with(Meta...)}.
 */
public final class Literal implements FormattableText<Literal> {
  private final String text;
  private FontInsta font = FallbackFont.acquire();
  private Gradient tint = Color.WHITE;
  private int fontStyle = Font.REGULAR;
  private @Nullable TextFormat fmt;
  private long version;
  private Meta @Nullable [] meta;

  /**
   * Creates a literal with optional format arguments.
   *
   * @param text the text
   */
  private Literal(String text) {
    this.text = text;
  }

  /**
   * Creates a literal with optional format arguments.
   *
   * @param text the text
   * @return a new literal
   */
  public static Literal of(String text) {
    return new Literal(text);
  }

  @Override
  public Literal font(FontInsta value) {
    font = value;
    changed();
    return this;
  }

  @Override
  public Literal tint(Gradient value) {
    tint = value;
    changed();
    return this;
  }

  @Override
  public Literal size(float value) {
    font = font.resized(value);
    changed();
    return this;
  }

  @Override
  public Literal style(int value) {
    fontStyle = value;
    changed();
    return this;
  }

  private void changed() {
    fmt = null;
    version++;
  }

  @Override
  public long version() {
    return version;
  }

  /**
   * Returns a copy of this literal with the given inline metadata.
   *
   * @param meta the metadata attachments
   * @return this literal
   */
  public Literal with(Meta @Nullable ... meta) {
    this.meta = meta;
    version++;
    return this;
  }

  @Override
  public String text() {
    return text;
  }

  @Override
  public Literal cut(int begin, int length) {
    Objects.checkFromIndexSize(begin, length, text.length());
    Literal result = Literal.of(text.substring(begin, begin + length)).with(format());
    result.meta = meta == null ? null : meta.clone();
    return result;
  }

  @Override
  public Meta @Nullable [] getMeta(int index) {
    return index >= 0 && index < text.length() ? meta : null;
  }

  @Override
  public MutableText append(Text component) {
    return new MutableText().append(this).append(component);
  }

  @Override
  public Raster raster() {
    return new MutableText().append(this).raster();
  }

  @Override
  public TextFormat forwadingStyle() {
    return format();
  }

  /**
   * Returns the text style of this literal.
   *
   * @return the text style
   */
  public TextFormat format() {
    if (fmt == null) {
      fmt = new TextFormat(font, tint, fontStyle);
    }
    return fmt;
  }

  /**
   * Returns the inline metadata attached to this literal.
   *
   * @return the metadata array, or {@code null} if none
   */
  public Meta @Nullable [] meta() {
    return meta;
  }

  @Override
  public int hashCode() {
    return Objects.hash(text, format(), Arrays.hashCode(meta));
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != this.getClass()) {
      return false;
    }
    Literal that = (Literal) obj;
    return Objects.equals(this.text, that.text) &&
        Objects.equals(this.format(), that.format()) &&
        Arrays.equals(this.meta, that.meta);
  }

  @Override
  public String toString() {
    return "Literal[" +
        "text=" + text + ", " +
        "fmt=" + format() + ", " +
        "meta=" + Arrays.toString(meta) + ']';
  }
}
