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

import io.viki.momentum.gfx.GraphicsMetrics;
import io.viki.momentum.gfx.text.raster.Raster;
import io.viki.momentum.gfx.texture.Drawable2D;
import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.gfx.tint.Gradient;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Immutable styled span. Formatting methods return new values; metadata arrays are copied.
 * Content is shareable; raster memoization follows the fonts' rendering-thread requirements.
 */
public final class Literal implements FormattableText<Literal> {
  private final String text;
  private final TextFormat format;
  private final Meta @Nullable [] meta;
  private int hash;
  private volatile boolean hashed;
  private @Nullable Raster cached;
  private long cachedGeneration = -1;

  private Literal(String text, TextFormat format, Meta @Nullable [] meta) {
    this.text = text;
    this.format = format;
    this.meta = meta;
  }

  /**
   * Creates a literal.
   *
   * @param text the raw text
   * @return a new literal instance
   */
  public static Literal of(String text) {
    return new Literal(text, TextFormat.of(), null);
  }

  // Arbitrary gradients and metadata may be stateful: their borrowed identities are stable keys.
  private static int gradientHash(Gradient value) {
    return value instanceof Color ? value.hashCode() : System.identityHashCode(value);
  }

  private static boolean sameFormat(TextFormat a, TextFormat b) {
    return a.font().equals(b.font()) && a.fontStyle() == b.fontStyle() && a.shadow().equals(b.shadow())
           && (a.tint() == b.tint() || a.tint() instanceof Color && a.tint().equals(b.tint()));
  }

  private static int metadataHash(Meta value) {
    if (value instanceof Href) {
      return value.hashCode();
    }
    if (value instanceof Icon(
        float width, float height, float baseline, Drawable2D drawable
    )) {
      return 31 * (31 * (31 * Float.hashCode(width) + Float.hashCode(height))
                   + Float.hashCode(baseline)) + System.identityHashCode(drawable);
    }
    return System.identityHashCode(value);
  }

  private static boolean sameMetadata(Meta @Nullable [] a, Meta @Nullable [] b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null || a.length != b.length) {
      return false;
    }
    for (int i = 0; i < a.length; i++) {
      if (a[i] == b[i]) {
        continue;
      }
      if (a[i] instanceof Href href && href.equals(b[i])) {
        continue;
      }
      if (a[i] instanceof Icon(
          float width1, float height1, float baseline1, Drawable2D drawable1
      ) && b[i] instanceof Icon(
          float width, float height, float baseline, Drawable2D drawable
      )
          && Float.compare(width1, width) == 0 && Float.compare(height1, height) == 0
          && Float.compare(baseline1, baseline) == 0 && drawable1 == drawable) {
        continue;
      }
      return false;
    }
    return true;
  }

  @Override
  public Literal font(FontInsta value) {
    return with(format.font(value));
  }

  @Override
  public Literal tint(Gradient value) {
    return with(format.tint(value));
  }

  @Override
  public Literal size(float value) {
    return with(format.size(value));
  }

  @Override
  public Literal style(int value) {
    return with(format.style(value));
  }

  @Override
  public Literal shadow(TextShadow value) {
    return with(format.shadow(value));
  }

  @Override
  public Literal with(TextFormat value) {
    return sameFormat(format, value) ? this : new Literal(text, value, meta);
  }

  @Override
  public TextFormat format() {
    return format;
  }

  @Override
  public Literal with(Meta @Nullable ... values) {
    if (!text.isEmpty() && Icon.find(values) != null) {
      throw new IllegalArgumentException("Drawable metadata requires an empty literal");
    }
    return new Literal(text, format, values == null || values.length == 0 ? null : values.clone());
  }

  /**
   * Returns the metas contained in this literal text.
   *
   * @return the meta array
   */
  public Meta @Nullable [] meta() {
    return meta == null ? null : meta.clone();
  }

  @Override
  public String text() {
    return text;
  }

  @Override
  public List<Literal> spans() {
    return List.of(this);
  }

  @Override
  public Literal cut(int begin, int length) {
    Objects.checkFromIndexSize(begin, length, text.length());
    return new Literal(text.substring(begin, begin + length), format, meta);
  }

  @Override
  public Meta @Nullable [] getMeta(int index) {
    return index >= 0 && (index < text.length() || index == 0 && Icon.find(meta) != null) ? meta() : null;
  }

  @Override
  public Raster raster() {
    long generation = TextRasterCache.generation();
    if (cached != null && cachedGeneration == generation) {
      GraphicsMetrics.TextInstanceCacheHits.increment();
      return cached;
    }
    cached = TextRasterCache.get(this);
    cachedGeneration = generation;
    return cached;
  }

  @Override
  public TextFormat forwadingStyle() {
    return format;
  }

  @Override
  public int hashCode() {
    if (!hashed) {
      int styleHash = 31 * (31 * format.font().hashCode() + format.fontStyle())
                      + gradientHash(format.tint());
      styleHash = 31 * styleHash + format.shadow().hashCode();
      int metadataHash = 0;
      if (meta != null) {
        metadataHash = 1;
        for (Meta value : meta) {
          metadataHash = 31 * metadataHash + metadataHash(value);
        }
      }
      hash = 31 * (31 * text.hashCode() + styleHash) + metadataHash;
      hashed = true;
    }
    return hash;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return this == other || other instanceof Literal that && hashCode() == that.hashCode()
                            && text.equals(that.text) && sameFormat(format, that.format) && sameMetadata(meta, that.meta);
  }

  @Override
  public String toString() {
    return "Literal[text=" + text + ", format=" + format + ", meta=" + Arrays.toString(meta) + ']';
  }
}
