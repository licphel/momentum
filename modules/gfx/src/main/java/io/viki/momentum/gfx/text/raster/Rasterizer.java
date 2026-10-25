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
import io.viki.momentum.gfx.text.*;
import io.viki.momentum.gfx.text.harfbuzz.HarfbuzzShaper;
import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.util.Pool;
import org.jspecify.annotations.Nullable;

import java.util.*;

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

  private static List<Literal> resolveFonts(List<Literal> literals) {
    boolean combined = false;
    for (Literal literal : literals) {
      if (literal.format().font().isCombined()) {
        combined = true;
        break;
      }
    }
    if (!combined) {
      return literals;
    }
    List<Literal> result = new ArrayList<>();
    for (Literal literal : literals) {
      FontInsta font = literal.format().font();
      if (!font.isCombined() || literal.text().isEmpty()) {
        result.add(literal);
        continue;
      }
      int start = 0;
      FontInsta selected = font.resolve(literal.text().codePointAt(0));
      for (int offset = 0; offset < literal.text().length(); ) {
        int codepoint = literal.text().codePointAt(offset);
        FontInsta next = font.resolve(codepoint);
        if (selected != next) {
          result.add(literal.cut(start, offset - start).font(selected));
          start = offset;
          selected = next;
        }
        offset += Character.charCount(codepoint);
      }
      result.add(literal.cut(start, literal.text().length() - start).font(selected));
    }
    return result;
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
      float fs = literals.get(sg.ownerIndex()).format().font().size();
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
    boolean[] softBreaks = new boolean[textLen + 1];
    for (int boundary = breaker.first(); boundary != BreakIterator.DONE;
         boundary = breaker.next()) {
      softBreaks[boundary] = true;
    }

    int lineStart = 0;
    float lineWidth = 0;
    int previousSoftBreak = -1;
    float widthAtPreviousSoftBreak = 0;
    int ci = 0;
    while (ci < textLen) {
      if (text.charAt(ci) == '\n') {
        result.add(new BreakPoint(ci, lineWidth, true));
        ci++;
        lineStart = ci;
        lineWidth = 0;
        previousSoftBreak = -1;
        widthAtPreviousSoftBreak = 0;
        continue;
      }

      float nextWidth = lineWidth + charAdvances[ci];
      if (nextWidth > widthLimit && ci > lineStart) {
        int breakOffset = previousSoftBreak > lineStart ? previousSoftBreak : ci;
        float breakWidth = previousSoftBreak > lineStart
            ? widthAtPreviousSoftBreak : lineWidth;
        result.add(new BreakPoint(breakOffset, breakWidth, false));
        lineStart = breakOffset;
        ci = lineStart;
        lineWidth = 0;
        previousSoftBreak = -1;
        widthAtPreviousSoftBreak = 0;
        continue;
      }

      lineWidth = nextWidth;
      ci++;
      if (softBreaks[ci]) {
        previousSoftBreak = ci;
        widthAtPreviousSoftBreak = lineWidth;
      }
    }
    if (result.isEmpty() || result.getLast().charOffset() < textLen) {
      result.add(new BreakPoint(textLen, lineWidth, false));
    }
    return result;
  }

  private static String buildMergedText(List<Literal> literals) {
    StringBuilder sb = new StringBuilder();
    for (Literal lit : literals) {
      sb.append(layoutText(lit));
    }
    return sb.toString();
  }

  private static int[] buildCharOffsets(List<Literal> literals) {
    int[] offsets = new int[literals.size()];
    int off = 0;
    for (int i = 0; i < literals.size(); i++) {
      offsets[i] = off;
      off += layoutText(literals.get(i)).length();
    }
    return offsets;
  }

  @SuppressWarnings("all")
  private static String layoutText(Literal literal) {
    return literal.text().isEmpty() && Icon.find(literal.meta()) != null ? "\uFFFC" : literal.text();
  }

  private static List<Raster.Stroke> buildStrokes(List<LayoutRun> runs, List<Literal> literals, boolean flipY) {
    List<Raster.Stroke> result = new ArrayList<>();
    StrokeRun stroke = new StrokeRun();
    for (LayoutRun layoutRun : runs) {
      for (LayoutGlyph glyph : layoutRun.glyphs()) {
        stroke.append(glyph, literals.get(glyph.ownerIndex()).format(), result, flipY);
      }
      stroke.flush(result, flipY);
    }
    return result;
  }

  private static final class StrokeRun {
    private @Nullable TextFormat format;
    private float x;
    private float y;
    private float width;

    private void append(LayoutGlyph glyph, TextFormat next, List<Raster.Stroke> out, boolean flipY) {
      if (glyph.w() <= 0 || glyph.glyphId() < 0
          || (next.fontStyle() & (Font.UNDERLINE | Font.STRIKETHROUGH)) == 0) {
        flush(out, flipY);
      } else if (next.equals(format) && Math.abs(glyph.y() - y) < 0.5F) {
        width += glyph.w();
      } else {
        flush(out, flipY);
        format = next;
        x = glyph.x();
        y = glyph.y();
        width = glyph.w();
      }
    }

    private void flush(List<Raster.Stroke> out, boolean flipY) {
      if (format == null) return;
      float size = format.font().size();
      FontMetrics metrics = format.font().font().metrics();
      float thickness = Math.abs(metrics.underlineThickness()) * size;
      if ((format.fontStyle() & Font.UNDERLINE) != 0) {
        float offset = metrics.underlinePos() * size;
        out.add(new Raster.Stroke(Rectangle.of(x, flipY ? y - offset : y + offset, width, thickness), format));
      }
      if ((format.fontStyle() & Font.STRIKETHROUGH) != 0) {
        float offset = size * 0.33F;
        out.add(new Raster.Stroke(Rectangle.of(x, flipY ? y - offset : y + offset, width, thickness), format));
      }
      format = null;
    }
  }

  /**
   * Rasterizes the given text literals into a {@link Raster}.
   *
   * @param literals the text literals to render
   * @return the rasterized output
   */
  public Raster render(List<Literal> literals) {
    literals = resolveFonts(literals);
    if (literals.isEmpty()) {
      return empty();
    }

    HarfbuzzShaper shaper = new HarfbuzzShaper();
    String mergedText = buildMergedText(literals);
    int[] litCharOffset = buildCharOffsets(literals);
    int textLen = mergedText.length();
    boolean hasDrawable = false;

    // Shape each literal -> ShapeGlyph[] with start/end absolute in mergedText.
    List<ShapeGlyph> list = new ArrayList<>(mergedText.length());
    for (int si = 0; si < literals.size(); si++) {
      Literal lit = literals.get(si);
      Icon drawable = Icon.find(lit.meta());
      if (drawable != null) {
        hasDrawable = true;
        int offset = litCharOffset[si];
        list.add(new ShapeGlyph(offset, offset + 1, -1,
            drawable.layoutWidth(maxWidth) / lit.format().font().size(), 0, 0, si));
        continue;
      }
      if (lit.text().isEmpty()) {
        continue;
      }
      int offset = litCharOffset[si];
      int litLen = lit.text().length();
      float fs = lit.format().font().size();

      ShapeResult sr = shaper.shape(lit.format().font(), lit.text(), flipY, lit.format().fontStyle());
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
      Collections.addAll(list, litGlyphs);
    }

    shaper.close();

    if (list.isEmpty()) {
      return empty();
    }
    SourceIndices source = hasDrawable ? SourceIndices.of(literals, textLen) : null;

    ShapeGlyph[] shapeGlyphs = list.toArray(new ShapeGlyph[0]);
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
    float bMaxX = -Float.MAX_VALUE;

    List<GlyphBound> layoutBounds = new ArrayList<>();

    for (LayoutRun run : layoutRuns) {
      for (LayoutGlyph lg : run.glyphs()) {
        Literal lit = literals.get(lg.ownerIndex());
        float scale = lg.scale();
        Icon drawable = lg.glyphId() < 0 ? Icon.find(lit.meta()) : null;
        Glyph g = drawable == null
            ? lit.format().font().font().rasterizeGlyph(lg.glyphId(), lit.format().fontStyle()) : null;
        float penX = lg.x() + lg.xOffset();
        float penY = lg.y() + lg.yOffset();
        float gx;
        float gy;
        float gw;
        float gh;
        if (drawable != null) {
          gx = penX;
          gy = flipY ? penY - drawable.baseline() : penY + drawable.baseline() - drawable.height();
          gw = lg.w();
          gh = drawable.height();
        } else if (g != null) {
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
          bMaxX = Math.max(bMaxX, gx + gw);
        }
        layoutBounds.add(GlyphBound.of(gx, gy, gw, gh));
      }
    }

    // Vertical bounds are the line boxes, not the ink: e, el and eg share a baseline.
    // Do not shift entries or runs; hitTest and caret placement use these same pen coordinates.
    float lineMinY = Float.MAX_VALUE;
    float lineMaxY = -Float.MAX_VALUE;
    for (LayoutRun run : layoutRuns) {
      lineMinY = Math.min(lineMinY, run.lineTop());
      lineMaxY = Math.max(lineMaxY, run.lineTop() + run.lineHeight());
    }
    Rectangle bounds = bMinX == Float.MAX_VALUE
        ? Rectangle.of(0, lineMinY, maxWidth == Float.MAX_VALUE ? 1 : maxWidth, Math.max(1, lineMaxY - lineMinY))
        : Rectangle.of(bMinX, lineMinY, bMaxX - bMinX, Math.max(1, lineMaxY - lineMinY));

    // Entry bounds stay in pen/layout space — no global shift applied.
    // Caller (Graphics.drawText) adds the draw origin when rendering.
    List<Raster.Entry> entries = new ArrayList<>();
    int gbIdx = 0;
    for (LayoutRun run : layoutRuns) {
      for (LayoutGlyph lg : run.glyphs()) {
        Literal lit = literals.get(lg.ownerIndex());
        Glyph g = lg.glyphId() < 0 ? null
            : lit.format().font().font().rasterizeGlyph(lg.glyphId(), lit.format().fontStyle());
        GlyphBound gb = layoutBounds.get(gbIdx++);
        Rectangle vb = Rectangle.of(gb.gx, gb.gy, gb.gw, gb.gh);
        // charIndex: absolute index in mergedText
        int absCharIndex = run.textStart() + lg.start();
        if (source != null) {
          absCharIndex = source.offsets()[absCharIndex];
        }
        entries.add(new Raster.Entry(g, lit.format(), vb, lg.scale(), lit.meta(), absCharIndex));
      }
    }

    // LayoutRun[] for hitTest: pen-space coordinates (no x shift, no y shift needed
    // since pen-space already starts at 0). Convert to array directly.
    LayoutRun[] runArr = source == null ? layoutRuns.toArray(new LayoutRun[0])
        : layoutRuns.stream().map(source::restore).toArray(LayoutRun[]::new);

    float lastLineWidth = computeLastLineWidth(layoutRuns);

    List<Raster.Stroke> strokes = buildStrokes(layoutRuns, literals, flipY);

    return new Raster(entries.toArray(new Raster.Entry[0]), strokes.toArray(new Raster.Stroke[0]), bounds,
        lastLineWidth, flipY, runArr, source == null ? mergedText : source.text());
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
      float maxDescender = 0;
      for (int g = lineGlyphStart; g < lineGlyphEnd; g++) {
        Literal lit = literals.get(glyphs[g].ownerIndex());
        Icon drawable = glyphs[g].glyphId() < 0 ? Icon.find(lit.meta()) : null;
        float lh, asc;
        if (drawable != null) {
          lh = drawable.height() * lineSpacing;
          asc = drawable.baseline();
        } else {
          FontMetrics m = lit.format().font().font().metrics();
          float fs = lit.format().font().size();
          lh = m.lineHeight() * fs * lineSpacing;
          asc = m.ascender() * fs;
        }
        if (lh > lineHeight) {
          lineHeight = lh;
        }
        if (asc > maxAscender) {
          maxAscender = asc;
        }
        maxDescender = Math.max(maxDescender, lh - asc);
      }
      lineHeight = Math.max(lineHeight, maxAscender + maxDescender);
      if (lineHeight <= 0 && lineGlyphEnd > lineGlyphStart) {
        Literal lit = literals.get(glyphs[lineGlyphStart].ownerIndex());
        lineHeight = lit.format().font().size() * lineSpacing;
        maxAscender = lit.format().font().font().metrics().ascender() * lit.format().font().size();
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
          lineWidthSum += sg.xAdvance() * literals.get(sg.ownerIndex()).format().font().size();
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
        float fs = lit.format().font().size();
        float scale = sg.glyphId() < 0 ? 1 : fs / lit.format().font().font().resolution();
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

  /** Maps internal inline-object slots back to the unchanged UTF-16 source text. */
  private record SourceIndices(String text, int[] offsets) {
    static SourceIndices of(List<Literal> literals, int layoutLength) {
      int[] offsets = new int[layoutLength + 1];
      StringBuilder text = new StringBuilder();
      int cursor = 0;
      for (Literal literal : literals) {
        for (int i = 0; i < layoutText(literal).length(); i++) {
          offsets[cursor++] = text.length() + (literal.text().isEmpty() ? 0 : i);
        }
        text.append(literal.text());
      }
      offsets[cursor] = text.length();
      return new SourceIndices(text.toString(), offsets);
    }

    LayoutRun restore(LayoutRun run) {
      int start = offsets[run.textStart()];
      int end = offsets[run.textStart() + run.text().length()];
      LayoutGlyph[] glyphs = new LayoutGlyph[run.glyphs().length];
      for (int i = 0; i < glyphs.length; i++) {
        LayoutGlyph glyph = run.glyphs()[i];
        glyphs[i] = new LayoutGlyph(offsets[run.textStart() + glyph.start()] - start,
            offsets[run.textStart() + glyph.end()] - start, glyph.x(), glyph.y(), glyph.w(),
            glyph.xOffset(), glyph.yOffset(), glyph.fontSize(), glyph.scale(), glyph.glyphId(),
            glyph.gradient(), glyph.ownerIndex());
      }
      return new LayoutRun(text.substring(start, end), start, glyphs, run.lineTop(), run.lineHeight(), run.lineY());
    }
  }

  private static final class GlyphBound {
    private static final Pool<GlyphBound> GB_POOL = new Pool<>();

    public float gx;
    public float gy;
    public float gw;
    public float gh;

    public static GlyphBound of(float gx, float gy, float gw, float gh) {
      GlyphBound bound = GB_POOL.poll();
      if (bound == null) {
        bound = new GlyphBound();
      }
      bound.set(gx, gy, gw, gh);
      return bound;
    }

    public void set(float gx, float gy, float gw, float gh) {
      this.gx = gx;
      this.gy = gy;
      this.gw = gw;
      this.gh = gh;
    }

    public void recycle() {
      GB_POOL.release(this);
    }
  }

  private record BreakPoint(int charOffset, float lineWidth, boolean hard) {
  }
}
