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

import net.fmhi.gfx.texture.TexturePart;

/**
 * A rasterized glyph with its texture region and positioning data.
 *
 * <p>All position values are in pixels in a Y-up coordinate system. The texture
 * region is {@code null} for glyphs with no visual representation, such as spaces.
 *
 * @param texPart  the region in the texture atlas, or {@code null} if the glyph has no visual representation
 * @param bearingX the horizontal offset from the pen position to the left edge of the glyph, in pixels
 * @param bearingY the vertical offset from the baseline to the top edge of the glyph, in pixels
 * @param advance  the horizontal distance to advance the pen after rendering this glyph, in pixels
 */
public record Glyph(TexturePart texPart, int bearingX, int bearingY, float advance) {
}
