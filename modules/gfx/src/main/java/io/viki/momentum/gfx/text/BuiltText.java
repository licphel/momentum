package io.viki.momentum.gfx.text;

import io.viki.momentum.gfx.GraphicsMetrics;
import io.viki.momentum.gfx.text.raster.Raster;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable flattened spans and layout; raster memoization is confined to the rendering thread. */
final class BuiltText implements Text {
  private final List<Literal> spans;
  private final TextLayout layout;
  private final String text;
  private int hash;
  private volatile boolean hashed;
  private @Nullable Raster cached;
  private long cachedGeneration = -1;

  BuiltText(List<Literal> spans, TextLayout layout) {
    this.spans = List.copyOf(spans);
    this.layout = layout;
    StringBuilder text = new StringBuilder();
    for (Literal span : spans) {
      text.append(span.text());
    }
    this.text = text.toString();
  }

  @Override
  public String text() {
    return text;
  }

  @Override
  public List<Literal> spans() {
    return spans;
  }

  @Override
  public Text cut(int begin, int length) {
    Objects.checkFromIndexSize(begin, length, text.length());
    List<Literal> result = new ArrayList<>();
    int remaining = length;
    boolean includesEnd = (length > 0 || text.isEmpty()) && begin + length == text.length();
    for (Literal span : spans) {
      int size = span.text().length();
      if (size == 0) {
        if (begin == 0 && (remaining > 0 || includesEnd)) {
          result.add(span);
        }
        continue;
      }
      if (remaining == 0) {
        break;
      }
      if (begin >= size) {
        begin -= size;
        continue;
      }
      int take = Math.min(remaining, size - begin);
      result.add(span.cut(begin, take));
      remaining -= take;
      begin = 0;
    }
    return new BuiltText(result, layout);
  }

  @Override
  public Meta @Nullable [] getMeta(int index) {
    if (index < 0) {
      return null;
    }
    for (Literal span : spans) {
      int length = span.text().length();
      if (length == 0 && index == 0) {
        Meta[] metadata = span.getMeta(0);
        if (metadata != null) {
          return metadata;
        }
      }
      if (index < length) {
        return span.getMeta(index);
      }
      index -= length;
    }
    return null;
  }

  @Override
  public TextLayout layout() {
    return layout;
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
  public @Nullable TextFormat forwadingStyle() {
    return spans.isEmpty() ? null : spans.getLast().format();
  }

  @Override
  public int hashCode() {
    if (!hashed) {
      hash = 31 * spans.hashCode() + layout.hashCode();
      hashed = true;
    }
    return hash;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return this == other || other instanceof BuiltText that
                            && hashCode() == that.hashCode() && layout.equals(that.layout) && spans.equals(that.spans);
  }
}
