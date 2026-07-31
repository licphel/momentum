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

import net.fmhi.math.Color;

/**
 * A glyph positioned by the layout pass, ready for rendering.
 *
 * <p>The {@code x} and {@code w} fields are pure pen-cursor values, excluding
 * the HarfBuzz x-offset. Offsets are kept separate for use during rendering only. The {@code start} and {@code end}
 * indices are relative to the owning {@link LayoutRun}{@code .text()}.
 *
 * @param start      the inclusive character index of the cluster start, relative to the run's text
 * @param end        the exclusive character index of the cluster end, relative to the run's text
 * @param x          the pen cursor X position, excluding x-offset
 * @param y          the pen cursor Y position at the baseline
 * @param w          the advance width, in pixels
 * @param xOffset    the horizontal offset for rendering, in pixels
 * @param yOffset    the vertical offset for rendering, in pixels
 * @param fontSize   the font size, in pixels
 * @param scale      the rendering scale factor
 * @param glyphId    the glyph index used to look up the bitmap
 * @param color      the glyph color
 * @param ownerIndex the index of the owning literal in the source list
 */
public record LayoutGlyph(int start, int end, float x, float y, float w, float xOffset, float yOffset, float fontSize,
                          float scale, int glyphId, Color color, int ownerIndex) {
}
