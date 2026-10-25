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

package io.viki.momentum.gfx.ui.look.auto;

import io.viki.momentum.gfx.text.Text;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

import io.viki.momentum.gfx.ui.present.TooltipLayout;
import io.viki.momentum.math.Vector2;
import java.util.List;

/**
 * Draws transient tooltip overlays after the regular element tree.
 *
 * <p>Tooltip geometry is computed from the rasterized text at draw time, keeping the overlay
 * independent from the element layout tree and ensuring it remains above every control.
 */
public final class AutoTooltipRenderer {
  private static final float PADDING = 4.0F;
  private static final float LINE_GAP = 2.0F;
  private static final TooltipLayout.Settings SETTINGS = new TooltipLayout.Settings(
      PADDING, PADDING, LINE_GAP, TooltipLayout.DEFAULT_MAX_WIDTH);

  private AutoTooltipRenderer() {
  }

  /**
   * Renders a multi-line tooltip assembled from rich text entries.
   *
   * <p>Each entry is drawn on its own line in list order. Text styles are preserved while the
   * tooltip surface is sized to the widest entry and the sum of all entry heights.
   *
   * @param graphics graphics context receiving the tooltip
   * @param values   rich text entries to draw
   * @param x        left coordinate of the tooltip surface
   * @param y        top coordinate of the tooltip surface
   */
  public static void render(Graphics graphics, List<Text> values, float x, float y, Rectangle canvas) {
    if (values.isEmpty() || canvas.width() <= 0 || canvas.height() <= 0) return;
    TooltipLayout layout = TooltipLayout.create(values, new Vector2(x, y), canvas, SETTINGS);
    AutoRendererSupport.fill(graphics, layout.bounds(), AutoRendererSupport.POPUP_SURFACE);
    AutoRendererSupport.drawBoundary(graphics, layout.bounds(), AutoRendererSupport.OUTLINE);
    layout.drawContents(graphics);
  }
}
