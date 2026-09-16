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

package io.viki.momentum.gfx.text.raster;

import com.ibm.icu.text.BreakIterator;
import io.viki.momentum.gfx.text.Font;
import io.viki.momentum.gfx.text.FontMetrics;
import io.viki.momentum.gfx.text.Literal;
import io.viki.momentum.gfx.text.harfbuzz.HarfbuzzShaper;
import io.viki.momentum.gfx.tint.Gradient;
import io.viki.momentum.math.shape.Rectangle;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Rasterizes a list of text literals into a {@link Raster} for rendering.
 *
 * <p>The rasterization pipeline has three stages:
 * <ol>
 *   <li><strong>Shaping</strong> — text is shaped into glyph IDs with advances and offsets.</li>
 *   <li><strong>Layout</strong> — line breaking assigns each glyph a pen-space position.</li>
 *   <li><strong>Rendering</strong> — glyph bitmaps are rasterized and assembled into a {@link Raster}.</li>
 * </ol>
 *
 * <p>Layout parameters are set as public fields before calling {@link #render(List)}.
 * Changes to any parameter invalidate cached results.
 */
public final class Rasterizer {
  /** Maximum line width in pixels; lines exceeding this are broken. */
  public float maxWidth = Float.MAX_VALUE;
  /** Whether the Y-axis is flipped during rasterization. */
  public boolean flipY = true;
  /** Whether text is justified to fill the full line width. */
  public boolean justify = false;
  /** Line spacing multiplier; {@code 1.0} uses the font's default line height. */
  public float lineSpacing = 1.0F;
  /** Maximum number of visible lines; text beyond this is clipped. */
  public int maxLines = Integer.MAX_VALUE;

  private static final class GlyphBound {
    private static final ConcurrentLinkedQueue<GlyphBound> GB_POOL = new ConcurrentLinkedQueue<>();

    public float gx;
    public float gy;
    public float gw;
    public float gh;

    public void set(float gx, float gy, float gw, float gh) {
      this.gx = gx;
      this.gy = gy;
      this.gw = gw;
      this.gh = gh;
    }

    public static GlyphBound of(float gx, float gy, float gw, float gh) {
      GlyphBound bound = GB_POOL.poll();
      if (bound == null) {
        bound = new GlyphBound();
      }
      bound.set(gx, gy, gw, gh);
      return bound;
    }

    public void recycle() {
      GB_POOL.offer(this);
    }
  }

  private static void computeEnds(ShapeGlyph[] glyphs, int litEnd) {
    for (int i = glyphs.length - 1; i >= 1; i--) {
      ShapeGlyph cur = glyphs[i];
      ShapeGlyph prev = glyphs[i - 1];
      int newEnd = (prev.start() == cur.start()) ? cur.end() : cur.start();
      glyphs[i - 1] = new ShapeGlyph(prev.start(), newEnd, prev.glyphId(), prev.xAdvance(), prev.xOffset(),
          prev.yOffset(), prev.ownerIndex());
    }
  }

  private static float[] buildCharAdvances(int textLen, ShapeGlyph[] glyphs, List<Literal> literals) {
    float[] ca = new float[textLen];
    for (ShapeGlyph sg : glyphs) {
      float fs = literals.get(sg.ownerIndex()).format().fontSize();
      float pixAdv = sg.xAdvance() * fs;
      int start = sg.start();
      int end = Math.min(sg.end(), textLen);
      if (end <= start) {
        if (start < textLen) {
          ca[start] += pixAdv;
        }
        continue;
      }
      float adv = pixAdv / (end - start);
      for (int ci = start; ci < end; ci++) {
        ca[ci] += adv;
      }
    }
    return ca;
  }

  private static float computeLastLineWidth(List<LayoutRun> runs) {
    if (runs.isEmpty()) {
      return 0;
    }
    LayoutGlyph[] glyphs = runs.getLast().glyphs();
    if (glyphs.length == 0) {
      return 0;
    }
    LayoutGlyph last = glyphs[glyphs.length - 1];
    return last.x() + last.w();
  }

  private static List<BreakPoint> collectBreakPoints(String text, float[] charAdvances, float widthLimit) {
    int textLen = text.length();
    List<BreakPoint> result = new ArrayList<>();
    BreakIterator breaker = BreakIterator.getLineInstance(Locale.US);
    breaker.setText(text);

    int nextBoundary = breaker.following(0);
    if (nextBoundary == BreakIterator.DONE) {
      nextBoundary = textLen;
    }

    float lineWidth = 0;
    int prevBreakChar = -1;
    float lineWidthAtPrevBreak = 0;

    for (int ci = 0; ci < textLen; ci++) {
      if (text.charAt(ci) == '\n') {
        result.add(new BreakPoint(ci, lineWidth, true));
        lineWidth = 0;
        prevBreakChar = -1;
        lineWidthAtPrevBreak = 0;
        while (nextBoundary <= ci + 1) {
          int nb = breaker.next();
          if (nb == BreakIterator.DONE) {
            nextBoundary = textLen;
            break;
          }
          nextBoundary = nb;
        }
        continue;
      }
      lineWidth += charAdvances[ci];
      if (ci + 1 == nextBoundary) {
        while (lineWidth > widthLimit) {
          if (prevBreakChar >= 0) {
            result.add(new BreakPoint(prevBreakChar, lineWidthAtPrevBreak, false));
            lineWidth -= lineWidthAtPrevBreak;
            prevBreakChar = -1;
            lineWidthAtPrevBreak = 0;
          } else {
            forceBreak(result, charAdvances, ci + 1, widthLimit, lineWidth, result.isEmpty() ? 0 :
                result.getLast().charOffset());
            lineWidth = 0;
            break;
          }
        }
        prevBreakChar = ci + 1;
        lineWidthAtPrevBreak = lineWidth;
        nextBoundary = breaker.next();
        if (nextBoundary == BreakIterator.DONE) {
          nextBoundary = textLen;
        }
      }
    }
    if (result.isEmpty() || result.getLast().charOffset() < textLen) {
      result.add(new BreakPoint(textLen, lineWidth, false));
    }
    return result;
  }

  private static void forceBreak(List<BreakPoint> result, float[] charAdvances, int end, float widthLimit,
                                 float lineWidth, int lastBreak) {
    float w = 0;
    for (int ci = lastBreak; ci < end; ci++) {
      float adv = charAdvances[ci];
      if (adv == 0) {
        continue;
      }
      if (w + adv > widthLimit) {
        result.add(new BreakPoint(Math.max(lastBreak + 1, ci), w, false));
        return;
      }
      w += adv;
    }
    result.add(new BreakPoint(Math.max(lastBreak + 1, end), lineWidth, false));
  }

  private static String buildMergedText(List<Literal> literals) {
    StringBuilder sb = new StringBuilder();
    for (Literal lit : literals) {
      sb.append(lit.text());
    }
    return sb.toString();
  }

  private static int[] buildCharOffsets(List<Literal> literals) {
    int[] offsets = new int[literals.size()];
    int off = 0;
    for (int i = 0; i < literals.size(); i++) {
      offsets[i] = off;
      off += literals.get(i).text().length();
    }
    return offsets;
  }

  private static List<Raster.Stroke> buildStrokes(List<LayoutRun> runs, List<Literal> literals, boolean flipY) {
    List<Raster.Stroke> result = new ArrayList<>();

    boolean inRun = false;
    float runX = 0;
    float runY = 0;
    float runW = 0;
    int runFlags = 0;
    Gradient runGrad = null;
    float runThicknessU = 0;
    float runOffsetU = 0;
    float runThicknessS = 0;
    float runOffsetS = 0;

    for (LayoutRun layoutRun : runs) {
      for (LayoutGlyph lg : layoutRun.glyphs()) {
        // Zero-advance glyphs are control chars. Always break the run.
        if (lg.w() <= 0) {
          if (inRun) {
            flushStroke(result, runFlags, runGrad, runX, runY, runW, runThicknessU, runOffsetU, runThicknessS,
                runOffsetS, flipY);
            inRun = false;
          }
          continue;
        }

        Literal lit = literals.get(lg.ownerIndex());
        int flags = lit.format().fontStyle();
        boolean wantsDecoration = (flags & Font.UNDERLINE) != 0 || (flags & Font.STRIKETHROUGH) != 0;

        if (!wantsDecoration) {
          if (inRun) {
            flushStroke(result, runFlags, runGrad, runX, runY, runW, runThicknessU, runOffsetU, runThicknessS,
                runOffsetS, flipY);
            inRun = false;
          }
          continue;
        }

        float baseline = lg.y();
        Gradient gradient = lit.format().tint();
        float fs = lit.format().fontSize();
        FontMetrics m = lit.format().font().metrics();
        float thicknessU = Math.abs(m.underlineThickness()) * fs;
        float offsetU = m.underlinePos() * fs;
        @SuppressWarnings("UnnecessaryLocalVariable")
        float thicknessS = thicknessU;
        float offsetS = fs * 0.33F;

        boolean sameRun = inRun && flags == runFlags && gradient.equals(runGrad) && Math.abs(baseline - runY) < 0.5F;

        if (sameRun) {
          runW += lg.w();
        } else {
          if (inRun) {
            flushStroke(result, runFlags, runGrad, runX, runY, runW, runThicknessU, runOffsetU, runThicknessS,
                runOffsetS, flipY);
          }
          inRun = true;
          runX = lg.x();
          runY = baseline;
          runW = lg.w();
          runFlags = flags;
          runGrad = gradient;
          runThicknessU = thicknessU;
          runOffsetU = offsetU;
          runThicknessS = thicknessS;
          runOffsetS = offsetS;
        }
      }
    }

    if (inRun) {
      flushStroke(result, runFlags, runGrad, runX, runY, runW, runThicknessU, runOffsetU, runThicknessS, runOffsetS,
          flipY);
    }

    return result;
  }

  private static void flushStroke(List<Raster.Stroke> out, int flags, Gradient gradient, float x, float baseline,
                                  float width, float thicknessU, float offsetU, float thicknessS, float offsetS,
                                  boolean flipY) {
    if ((flags & Font.UNDERLINE) != 0) {
      float uy = flipY ? baseline - offsetU : baseline + offsetU;
      out.add(new Raster.Stroke(Rectangle.of(x, uy, width, thicknessU), gradient));
    }
    if ((flags & Font.STRIKETHROUGH) != 0) {
      float sy = flipY ? baseline - offsetS : baseline + offsetS;
      out.add(new Raster.Stroke(Rectangle.of(x, sy, width, thicknessS), gradient));
    }
  }

  /**
   * Rasterizes the given text literals into a {@link Raster}.
   *
   * @param literals the text literals to render
   * @return the rasterized output
   */
  public Raster render(List<Literal> literals) {
    if (literals.isEmpty()) {
      return empty();
    }

    HarfbuzzShaper shaper = new HarfbuzzShaper();
    String mergedText = buildMergedText(literals);
    int[] litCharOffset = buildCharOffsets(literals);
    int textLen = mergedText.length();

    // Shape each literal -> ShapeGlyph[] with start/end absolute in mergedText.
    List<ShapeGlyph> allList = new ArrayList<>();
    for (int si = 0; si < literals.size(); si++) {
      Literal lit = literals.get(si);
      if (lit.text().isEmpty()) {
        continue;
      }
      int offset = litCharOffset[si];
      int litLen = lit.text().length();
      float fs = lit.format().fontSize();

      ShapeResult sr = shaper.shape(lit.format().font(), lit.text(), fs, flipY, lit.format().fontStyle());
      int gc = sr.glyphs().length;
      if (gc == 0) {
        continue;
      }

      ShapeGlyph[] litGlyphs = new ShapeGlyph[gc];
      for (int i = 0; i < gc; i++) {
        int start = sr.charIndices()[i] + offset;
        // advances/offsets are in pixels at fontSize; convert to em units
        float xAdv = fs > 0 ? sr.advances()[i] / fs : 0f;
        float xOff = fs > 0 ? sr.offsets()[i * 2] / fs : 0f;
        float yOff = fs > 0 ? sr.offsets()[i * 2 + 1] / fs : 0f;
        litGlyphs[i] = new ShapeGlyph(start, offset + litLen, sr.glyphs()[i], xAdv, xOff, yOff, si);
      }
      computeEnds(litGlyphs, offset + litLen);
      Collections.addAll(allList, litGlyphs);
    }

    shaper.close();

    if (allList.isEmpty()) {
      return empty();
    }

    ShapeGlyph[] shapeGlyphs = allList.toArray(new ShapeGlyph[0]);
    int totalGlyphs = shapeGlyphs.length;

    // Zero advance for control chars so they never contribute to line width.
    for (int i = 0; i < totalGlyphs; i++) {
      ShapeGlyph sg = shapeGlyphs[i];
      if (sg.start() < textLen && mergedText.charAt(sg.start()) < 0x20) {
        shapeGlyphs[i] = new ShapeGlyph(sg.start(), sg.end(), sg.glyphId(), 0f, sg.xOffset(), sg.yOffset(),
            sg.ownerIndex());
      }
    }

    float[] charAdvances = buildCharAdvances(textLen, shapeGlyphs, literals);

    List<LayoutRun> layoutRuns = buildLayoutRuns(mergedText, shapeGlyphs, charAdvances, literals);
    if (layoutRuns.isEmpty()) {
      return empty();
    }

    // Compute bearing-adjusted pen-space bounds for each glyph.
    // All coordinates are in pen space (x from 0 per line, y from 0 top).
    // Entry.bounds, Raster.bounds, and hitTest all live in this same space.
    float bMinX = Float.MAX_VALUE;
    float bMinY = Float.MAX_VALUE;
    float bMaxX = -Float.MAX_VALUE;
    float bMaxY = -Float.MAX_VALUE;

    List<GlyphBound> layoutBounds = new ArrayList<>();

    for (LayoutRun run : layoutRuns) {
      for (LayoutGlyph lg : run.glyphs()) {
        Literal lit = literals.get(lg.ownerIndex());
        float scale = lg.scale();
        Glyph g = lit.format().font().rasterizeGlyph(lg.glyphId(), lit.format().fontStyle());
        float penX = lg.x() + lg.xOffset();
        float penY = lg.y() + lg.yOffset();
        float gx;
        float gy;
        float gw;
        float gh;
        if (g != null) {
          gx = penX + g.bearingX() * scale;
          gw = g.texPart().width() * scale;
          gh = g.texPart().height() * scale;
          if (flipY) {
            // Y-down: visual top = baseline - bearingY (bearingY is positive = above baseline)
            gy = penY - g.bearingY() * scale;
          } else {
            // Y-up: visual bottom = baseline + bearingY - texHeight
            gy = penY + (g.bearingY() - g.texPart().height()) * scale;
          }
        } else {
          // No bitmap (space, control char, etc.) — zero-size bound, excluded from visual extents.
          gx = penX;
          gy = penY;
          gw = 0;
          gh = 0;
        }
        if (gw > 0 && gh > 0) {
          bMinX = Math.min(bMinX, gx);
          bMinY = Math.min(bMinY, gy);
          bMaxX = Math.max(bMaxX, gx + gw);
          bMaxY = Math.max(bMaxY, gy + gh);
        }
        layoutBounds.add(GlyphBound.of(gx, gy, gw, gh));
      }
    }

    Rectangle bounds;
    if (bMinX == Float.MAX_VALUE) {
      float totalH = 0;
      for (LayoutRun run : layoutRuns) {
        if (flipY) {
          totalH = Math.max(totalH, run.lineTop() + run.lineHeight());
        } else {
          totalH = Math.max(totalH, Math.abs(run.lineTop()));
        }
      }
      bounds = Rectangle.of(0, 0, maxWidth == Float.MAX_VALUE ? 1 : maxWidth, Math.max(1, totalH));
    } else {
      bounds = Rectangle.of(bMinX, bMinY, bMaxX - bMinX, Math.max(1, bMaxY - bMinY));
    }

    // Entry bounds stay in pen/layout space — no global shift applied.
    // Caller (Graphics.drawText) adds the draw origin when rendering.
    List<Raster.Entry> entries = new ArrayList<>();
    int gbIdx = 0;
    for (LayoutRun run : layoutRuns) {
      for (LayoutGlyph lg : run.glyphs()) {
        Literal lit = literals.get(lg.ownerIndex());
        Glyph g = lit.format().font().rasterizeGlyph(lg.glyphId(), lit.format().fontStyle());
        GlyphBound gb = layoutBounds.get(gbIdx++);
        Rectangle vb = Rectangle.of(gb.gx, gb.gy, gb.gw, gb.gh);
        // charIndex: absolute index in mergedText
        int absCharIndex = run.textStart() + lg.start();
        entries.add(new Raster.Entry(g, lit.format().tint(), vb, lg.scale(), lit.meta(), absCharIndex));
      }
    }

    // LayoutRun[] for hitTest: pen-space coordinates (no x shift, no y shift needed
    // since pen-space already starts at 0). Convert to array directly.
    LayoutRun[] runArr = layoutRuns.toArray(new LayoutRun[0]);

    float lastLineWidth = computeLastLineWidth(layoutRuns);

    List<Raster.Stroke> strokes = buildStrokes(layoutRuns, literals, flipY);

    return new Raster(entries.toArray(new Raster.Entry[0]), strokes.toArray(new Raster.Stroke[0]), bounds,
        lastLineWidth, flipY, runArr, mergedText);
  }

  private List<LayoutRun> buildLayoutRuns(String mergedText, ShapeGlyph[] glyphs, float[] charAdvances,
                                          List<Literal> literals) {
    int textLen = mergedText.length();
    int totalGlyphs = glyphs.length;
    List<LayoutRun> runs = new ArrayList<>();
    if (textLen == 0 || totalGlyphs == 0) {
      return runs;
    }

    List<BreakPoint> breakPoints = collectBreakPoints(mergedText, charAdvances, maxWidth);
    int lineLimit = Math.min(breakPoints.size(), maxLines);

    float lineY = 0;
    int glyphCursor = 0;
    int lineCharStart = 0;

    for (int li = 0; li < lineLimit; li++) {
      int breakChar = Math.min(breakPoints.get(li).charOffset(), textLen);
      boolean isHardBreak = breakPoints.get(li).hard();

      int lineGlyphStart = glyphCursor;
      while (glyphCursor < totalGlyphs && glyphs[glyphCursor].start() < breakChar) {
        glyphCursor++;
      }
      int lineGlyphEnd = glyphCursor;

      float lineHeight = 0;
      float maxAscender = 0;
      for (int g = lineGlyphStart; g < lineGlyphEnd; g++) {
        Literal lit = literals.get(glyphs[g].ownerIndex());
        FontMetrics m = lit.format().font().metrics();
        float fs = lit.format().fontSize();
        float lh = m.lineHeight() * fs * lineSpacing;
        float asc = m.ascender() * fs;
        if (lh > lineHeight) {
          lineHeight = lh;
        }
        if (asc > maxAscender) {
          maxAscender = asc;
        }
      }
      if (lineHeight <= 0 && lineGlyphEnd > lineGlyphStart) {
        Literal lit = literals.get(glyphs[lineGlyphStart].ownerIndex());
        lineHeight = lit.format().fontSize() * lineSpacing;
        maxAscender = lit.format().font().metrics().ascender() * lit.format().fontSize();
      }
      if (lineHeight <= 0) {
        lineHeight = 16 * lineSpacing;
      }

      float lineTop = flipY ? lineY : lineY - lineHeight;
      float baseline = flipY ? lineY + maxAscender : lineY - maxAscender;

      if (flipY) {
        lineY += lineHeight;
      } else {
        lineY -= lineHeight;
      }

      float extra = 0;
      if (justify && li < lineLimit - 1 && !isHardBreak && maxWidth < Float.MAX_VALUE) {
        int spaces = 0;
        float lineWidthSum = 0;
        for (int g = lineGlyphStart; g < lineGlyphEnd; g++) {
          ShapeGlyph sg = glyphs[g];
          lineWidthSum += sg.xAdvance() * literals.get(sg.ownerIndex()).format().fontSize();
          if (sg.start() < textLen && mergedText.charAt(sg.start()) == ' ') {
            spaces++;
          }
        }
        if (spaces > 0) {
          extra = (maxWidth - lineWidthSum) / spaces;
        }
      }

      int sliceEnd = Math.min(breakChar, textLen);
      if (sliceEnd < textLen && mergedText.charAt(sliceEnd) == '\n') {
        sliceEnd++;
      }
      sliceEnd = Math.min(sliceEnd, textLen);
      String lineText = mergedText.substring(lineCharStart, sliceEnd);

      LayoutGlyph[] lineGlyphs = new LayoutGlyph[lineGlyphEnd - lineGlyphStart];
      int lineGlyphCount = 0;
      float lineCursor = 0;
      for (int g = lineGlyphStart; g < lineGlyphEnd; g++) {
        ShapeGlyph sg = glyphs[g];
        // Skip the '\n' glyph itself — it belongs to the previous line's break, not this line.
        if (sg.start() < textLen && mergedText.charAt(sg.start()) == '\n') {
          continue;
        }

        Literal lit = literals.get(sg.ownerIndex());
        float fs = lit.format().fontSize();
        float scale = fs / lit.format().font().resolution();
        float adv = sg.xAdvance() * fs;
        if (sg.start() < textLen && mergedText.charAt(sg.start()) == ' ') {
          adv += extra;
        }

        // start/end relative to lineText (for hitTest substring)
        int relStart = sg.start() - lineCharStart;
        int relEnd = sg.end() - lineCharStart;
        relStart = Math.max(0, Math.min(relStart, lineText.length()));
        relEnd = Math.max(relStart, Math.min(relEnd, lineText.length()));

        lineGlyphs[lineGlyphCount++] = new LayoutGlyph(relStart, relEnd, lineCursor,
            baseline,          // y = pen-space baseline
            adv,               // w = pixel advance
            sg.xOffset() * fs, // xOffset pixels (render only)
            sg.yOffset() * fs, // yOffset pixels (render only)
            fs, scale, sg.glyphId(), lit.format().tint(), sg.ownerIndex());
        lineCursor += adv;
      }
      if (lineGlyphCount < lineGlyphs.length) {
        lineGlyphs = Arrays.copyOf(lineGlyphs, lineGlyphCount);
      }

      runs.add(new LayoutRun(lineText, lineCharStart, lineGlyphs, lineTop, lineHeight, baseline));

      lineCharStart = breakChar;
      if (lineCharStart < textLen && mergedText.charAt(lineCharStart) == '\n') {
        lineCharStart++;
      }
    }

    return runs;
  }

  private Raster empty() {
    return new Raster(new Raster.Entry[0], new Raster.Stroke[0], Rectangle.ZERO, 0, flipY, new LayoutRun[0], "");
  }

  private record BreakPoint(int charOffset, float lineWidth, boolean hard) {
  }
}
