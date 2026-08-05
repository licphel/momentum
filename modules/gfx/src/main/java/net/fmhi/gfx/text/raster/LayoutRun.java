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

/**
 * One visual line of laid-out glyphs produced by line breaking.
 *
 * <p>Glyph {@code start} and {@code end} indices are relative to the line's {@code text}.
 * Coordinates use pen space: X starts at 0 for each line, Y grows by line height.
 *
 * @param text       the slice of the source string covered by this line
 * @param textStart  the absolute character offset of {@code text[0]} in the merged source string
 * @param glyphs     the laid-out glyphs on this line
 * @param lineTop    the Y coordinate of the top of this line, in pen space
 * @param lineHeight the height of this line, in pixels
 * @param lineY      the Y coordinate of the baseline, in pen space
 */
public record LayoutRun(String text, int textStart,       // absolute char index of text[0] in mergedText
                        LayoutGlyph[] glyphs, float lineTop, float lineHeight, float lineY) {
}
