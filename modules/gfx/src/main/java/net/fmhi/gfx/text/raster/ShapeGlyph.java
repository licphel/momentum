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
 * A shaped glyph produced by the text shaping pass.
 *
 * <p>The {@code start} and {@code end} fields are character indices in the source string.
 * Advances and offsets are in em units, normalized by the font's units-per-em.
 *
 * @param start      the inclusive character index of the cluster start
 * @param end        the exclusive character index of the cluster end
 * @param glyphId    the glyph index within the font
 * @param xAdvance   the horizontal advance, in em units
 * @param xOffset    the horizontal offset, in em units
 * @param yOffset    the vertical offset, in em units
 * @param ownerIndex the index of the owning literal in the source list
 */
public record ShapeGlyph(int start,       // inclusive char index of cluster start in the literal's text
                         int end,
                         // exclusive char index of cluster end (set by computeEnds, initially = textLen)
                         int glyphId, float xAdvance,  // em units
                         float xOffset,   // em units (added only during rendering)
                         float yOffset,   // em units (added only during rendering, y-direction depends on flipY)
                         int ownerIndex   // index into the literals list
) {
}
