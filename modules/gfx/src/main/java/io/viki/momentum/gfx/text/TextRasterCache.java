package io.viki.momentum.gfx.text;

import io.viki.momentum.gfx.GraphicsMetrics;
import io.viki.momentum.gfx.text.raster.Raster;
import io.viki.momentum.gfx.text.raster.Rasterizer;
import io.viki.momentum.util.InternalApi;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded raster sharing between immutable text values. Render on the fonts' owning thread. */
@InternalApi
final class TextRasterCache {
  /** Maximum number of shared text layouts retained between draws. */
  static final int CAPACITY = 2048;
  private static final Map<Text, Raster> CACHE = new LinkedHashMap<>(CAPACITY, 0.75F, true);
  private static volatile long generation;

  private TextRasterCache() {
  }

  static long generation() {
    return generation;
  }

  static Raster get(Text text) {
    long currentGeneration;
    synchronized (CACHE) {
      GraphicsMetrics.TextCacheQueries.increment();
      Raster cached = CACHE.get(text);
      if (cached != null) {
        GraphicsMetrics.TextCacheHits.increment();
        return cached;
      }
      currentGeneration = generation;
    }
    TextLayout layout = text.layout();
    Rasterizer rasterizer = new Rasterizer();
    rasterizer.maxWidth = layout.maxWidth();
    rasterizer.flipY = layout.flipY();
    rasterizer.justify = layout.justify();
    rasterizer.lineSpacing = layout.lineSpacing();
    rasterizer.maxLines = layout.maxLines();
    Raster raster = rasterizer.render(text.spans());
    synchronized (CACHE) {
      if (generation == currentGeneration) {
        CACHE.put(text, raster);
        if (CACHE.size() > CAPACITY) {
          Iterator<Text> oldest = CACHE.keySet().iterator();
          oldest.next();
          oldest.remove();
        }
      }
    }
    return raster;
  }

  static void clear() {
    synchronized (CACHE) {
      CACHE.clear();
      generation++;
    }
  }
}
