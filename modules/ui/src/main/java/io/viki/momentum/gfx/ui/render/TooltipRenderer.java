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

package io.viki.momentum.gfx.ui.render;

import io.viki.momentum.gfx.text.Literal;
import io.viki.momentum.gfx.text.Text;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws transient tooltip overlays after the regular element tree.
 *
 * <p>Tooltip geometry is computed from the rasterized text at draw time, keeping the overlay
 * independent from the element layout tree and ensuring it remains above every control.
 */
public final class TooltipRenderer {
  private static final float PADDING = 4.0F;
  private static final float LINE_GAP = 2.0F;

  private TooltipRenderer() {
  }

  /**
   * Renders a padded tooltip surface and its text at a canvas position.
   *
   * @param graphics graphics context receiving the tooltip
   * @param value tooltip text to rasterize
   * @param x left coordinate of the tooltip surface
   * @param y top coordinate of the tooltip surface
   */
  public static void render(Graphics graphics, String value, float x, float y) {
    Text text = Literal.of(value).with(RendererSupport.TEXT_FORMAT.tint(RendererSupport.FOREGROUND));
    render(graphics, List.of(text), x, y);
  }

  /**
   * Renders a multi-line tooltip assembled from rich text entries.
   *
   * <p>Each entry is drawn on its own line in list order. Text styles are preserved while the
   * tooltip surface is sized to the widest entry and the sum of all entry heights.
   *
   * @param graphics graphics context receiving the tooltip
   * @param values rich text entries to draw
   * @param x left coordinate of the tooltip surface
   * @param y top coordinate of the tooltip surface
   */
  public static void render(Graphics graphics, List<Text> values, float x, float y) {
    if (values.isEmpty()) {
      return;
    }
    List<Rectangle> bounds = new ArrayList<>(values.size());
    float width = 0.0F;
    float height = 0.0F;
    for (Text value : values) {
      Rectangle textBounds = value.raster().bounds();
      bounds.add(textBounds);
      width = Math.max(width, textBounds.width());
      height += textBounds.height();
    }
    height += LINE_GAP * Math.max(0, values.size() - 1);
    Rectangle area = Rectangle.of(x, y, width + PADDING * 2.0F,
        height + PADDING * 2.0F);
    // Draw the tooltip popup surface.
    RendererSupport.fill(graphics, area, RendererSupport.POPUP_SURFACE);
    // Draw the tooltip popup border.
    RendererSupport.drawBoundary(graphics, area, RendererSupport.OUTLINE);
    // Draw each rich text entry on its own tooltip line.
    float offsetY = y + PADDING;
    for (int i = 0; i < values.size(); i++) {
      graphics.drawText(values.get(i), x + PADDING, offsetY);
      offsetY += bounds.get(i).height() + LINE_GAP;
    }
  }
}
