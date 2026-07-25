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

package net.fmhi.gfx.text.raster;

import com.ibm.icu.text.BreakIterator;
import net.fmhi.gfx.text.Meta;
import net.fmhi.math.Box2D;
import net.fmhi.math.Color;
import net.fmhi.math.Vector2;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * The rasterized output of a text layout pass, ready for rendering.
 *
 * <p>Contains per-glyph render entries, decoration strokes, bounding box, and
 * hit-test data for cursor positioning. All coordinates are in visual space with the origin at the top-left of the
 * rendered area.
 *
 * @param entries       the per-glyph render entries
 * @param strokes       the decoration strokes (underline and strikethrough)
 * @param bounds        the bounding box in visual space
 * @param lastLineWidth the width of the last line, in pixels
 * @param flipY         whether the Y-axis is flipped
 * @param runs          the layout runs used for hit-testing
 * @param mergedText    the concatenated source text
 */
public record Raster(Entry[] entries, Stroke[] strokes, Box2D bounds, float lastLineWidth, boolean flipY,
                     LayoutRun[] runs, String mergedText) {
  private static int hitRun(LayoutRun run, float x) {
    LayoutGlyph[] glyphs = run.glyphs();
    if (glyphs.length == 0) {
      // Empty line: return textStart (e.g. the '\n' at the end of a line).
      return run.textStart();
    }

    // LTR: if x is left of the first glyph, place cursor at glyph[0].start.
    if (x < 0.0F) {
      return run.textStart() + glyphs[0].start();
    }

    for (LayoutGlyph g : glyphs) {
      if (x >= g.x() && x <= g.x() + g.w()) {
        // Subdivide cluster by grapheme.
        int clusterStart = g.start();
        int clusterEnd = g.end();
        clusterStart = Math.max(0, Math.min(clusterStart, run.text().length()));
        clusterEnd = Math.max(clusterStart, Math.min(clusterEnd, run.text().length()));
        String cluster = run.text().substring(clusterStart, clusterEnd);

        int totalGraphemes = countGraphemes(cluster);
        if (totalGraphemes == 0) {
          // Degenerate: return start of this glyph's cluster.
          return run.textStart() + clusterStart;
        }

        float egcW = g.w() / totalGraphemes;
        float egcX = g.x();

        BreakIterator charBreaker = BreakIterator.getCharacterInstance(Locale.ROOT);
        charBreaker.setText(cluster);
        int prev = 0;
        for (int next = charBreaker.next(); next != BreakIterator.DONE; next = charBreaker.next()) {
          if (x >= egcX && x <= egcX + egcW) {
            return run.textStart() + clusterStart + prev;
          }
          egcX += egcW;
          prev = next;
        }
        // Fell past all graphemes -> end of cluster.
        return run.textStart() + clusterEnd;
      }
    }

    // x is past all glyphs: place cursor at logical end of this visual line.
    // Use max glyph.end across all glyphs (cosmic-text: run_end = run.glyphs.iter().map(|g| g.end).max()).
    int runEnd = 0;
    for (LayoutGlyph g : glyphs) {
      runEnd = Math.max(runEnd, g.end());
    }
    return run.textStart() + runEnd - 1;
  }

  private static int countGraphemes(String s) {
    if (s.isEmpty()) {
      return 0;
    }
    BreakIterator it = BreakIterator.getCharacterInstance(Locale.ROOT);
    it.setText(s);
    int count = 0;
    while (it.next() != BreakIterator.DONE) {
      count++;
    }
    return count;
  }

  /**
   * Returns the character index closest to the given visual-space coordinate.
   *
   * <p>The lookup selects the layout run whose vertical band contains {@code y}, then
   * finds the glyph whose horizontal cell contains {@code x}. Within multi-character clusters, the coordinate is
   * further subdivided by grapheme boundaries.
   *
   * @param x the X coordinate in visual space
   * @param y the Y coordinate in visual space
   * @return the absolute character index in the merged source text, or {@code -1} if there are no runs
   */
  public int hitTest(float x, float y) {
    if (runs.length == 0) {
      return -1;
    }

    LayoutRun matchedRun = null;

    // Find the run whose vertical band contains y.
    // If y is above all runs, use the first. If below all runs, use the last.
    for (int ri = 0; ri < runs.length; ri++) {
      LayoutRun run = runs[ri];
      float bandLo = run.lineTop();
      float bandHi = run.lineTop() + run.lineHeight();

      if (flipY) {
        // Y-down: bandLo = visual top (smaller Y), bandHi = visual bottom (larger Y)
        if (ri == 0 && y < bandLo) {
          matchedRun = run;
          break;
        }
        if (y >= bandLo && y < bandHi) {
          matchedRun = run;
          break;
        }
      } else {
        // Y-up: bandLo = visual bottom (smaller Y), bandHi = visual top (larger Y)
        if (ri == 0 && y > bandHi) {
          matchedRun = run;
          break;
        }
        if (y > bandLo && y <= bandHi) {
          matchedRun = run;
          break;
        }
      }

      if (ri == runs.length - 1) {
        matchedRun = run;
      }
    }
    if (matchedRun == null) {
      return -1;
    }

    return hitRun(matchedRun, x);
  }

  /**
   * Returns the character index closest to the given point.
   *
   * @param point the coordinates in visual space
   * @return the absolute character index in the merged source text, or {@code -1} if there are no runs
   */
  public int hitTest(Vector2 point) {
    return hitTest(point.x(), point.y());
  }

  /**
   * A render entry for a single glyph.
   *
   * @param glyph     the rasterized glyph bitmap, or {@code null} if the glyph has no visual representation
   * @param color     the glyph color
   * @param bounds    the bearing-adjusted bounding rectangle in visual space
   * @param scale     the rendering scale factor
   * @param metaInfo  optional metadata, or {@code null} if none
   * @param charIndex the absolute character index in the merged source text
   */
  public record Entry(@Nullable Glyph glyph, Color color, Box2D bounds, float scale, Meta @Nullable [] metaInfo,
                      int charIndex) {
  }

  /**
   * A decoration line (underline or strikethrough) in visual space.
   *
   * @param bounds the bounding rectangle of the stroke
   * @param color  the stroke color
   */
  public record Stroke(Box2D bounds, Color color) {
  }
}
