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

package io.viki.momentum.gfx.ui;

import io.viki.momentum.gfx.text.raster.LayoutGlyph;
import io.viki.momentum.gfx.text.raster.LayoutRun;
import io.viki.momentum.gfx.text.raster.Raster;
import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.math.shape.Rectangle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RasterHitTestTest {
  @Test
  void hitTestCanChooseInsertionPointAfterLastCharacter() {
    LayoutGlyph glyph = new LayoutGlyph(0, 1, 0.0F, 8.0F, 10.0F,
        0.0F, 0.0F, 8.0F, 1.0F, 1, Color.WHITE, 0);
    LayoutRun run = new LayoutRun("A", 0, new LayoutGlyph[]{glyph},
        0.0F, 10.0F, 8.0F);
    Raster raster = new Raster(new Raster.Entry[0], new Raster.Stroke[0],
        Rectangle.of(0.0F, 0.0F, 10.0F, 10.0F), 10.0F, true,
        new LayoutRun[]{run}, "A");

    assertEquals(0, raster.hitTest(2.0F, 5.0F));
    assertEquals(1, raster.hitTest(8.0F, 5.0F));
    assertEquals(1, raster.hitTest(20.0F, 5.0F));
  }
}
