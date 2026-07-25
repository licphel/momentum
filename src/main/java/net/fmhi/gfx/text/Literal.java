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

import net.fmhi.gfx.text.raster.Raster;
import net.fmhi.util.i18n.Language;
import org.jspecify.annotations.Nullable;

import java.text.MessageFormat;
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
 * <p>Instances are created via {@link #of(String, Object...)} for static text
 * or {@link #translated(String, Object...)} for localized text resolved through
 * {@link Language#current()}. Style and metadata are set fluently with
 * {@link #with(TextFormat)} and {@link #with(Meta...)}.
 */
public final class Literal implements Text {
  private final String text;
  private TextFormat fmt = TextFormat.of();
  private Meta @Nullable [] meta;

  /**
   * Creates a literal with optional format arguments.
   *
   * @param text the text, possibly containing {@link MessageFormat} placeholders
   * @param args the format arguments
   */
  private Literal(String text, Object... args) {
    if (args.length > 0) {
      try {
        text = MessageFormat.format(text, args);
      } catch (IllegalArgumentException e) {
        // Ignored
      }
    }
    this.text = text;
  }

  /**
   * Creates a literal with optional format arguments.
   *
   * @param text the text, possibly containing {@link MessageFormat} placeholders
   * @param args the format arguments
   * @return a new literal
   */
  public static Literal of(String text, Object... args) {
    return new Literal(text, args);
  }

  /**
   * Creates a literal whose text is resolved from the current
   * {@link Language}.
   *
   * @param key  the translation key
   * @param args optional format arguments
   * @return a new literal with translated text
   */
  public static Literal translated(String key, Object... args) {
    return new Literal(Language.current().translate(key), args);
  }

  /**
   * Returns a copy of this literal with the given text style.
   *
   * @param fmt the text style
   * @return this literal
   */
  public Literal with(TextFormat fmt) {
    this.fmt = fmt;
    return this;
  }

  /**
   * Returns a copy of this literal with the given inline metadata.
   *
   * @param meta the metadata attachments
   * @return this literal
   */
  public Literal with(Meta @Nullable ... meta) {
    this.meta = meta;
    return this;
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
    return fmt;
  }

  @Override
  public String text() {
    return text;
  }

  /**
   * Returns the text style of this literal.
   *
   * @return the text style
   */
  public TextFormat format() {
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
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != this.getClass()) {
      return false;
    }
    Literal that = (Literal) obj;
    return Objects.equals(this.text, that.text) &&
        Objects.equals(this.fmt, that.fmt) &&
        Arrays.equals(this.meta, that.meta);
  }

  @Override
  public int hashCode() {
    return Objects.hash(text, fmt, Arrays.hashCode(meta));
  }

  @Override
  public String toString() {
    return "Literal[" +
        "text=" + text + ", " +
        "fmt=" + fmt + ", " +
        "meta=" + Arrays.toString(meta) + ']';
  }
}
