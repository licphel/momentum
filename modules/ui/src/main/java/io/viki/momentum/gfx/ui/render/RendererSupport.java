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
import io.viki.momentum.gfx.text.TextFormat;
import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.gfx.util.Alignment;
import io.viki.momentum.gfx.util.VertexBuilder2D;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.util.InternalApi;

/** Shared constants and low-level drawing helpers for the built-in element renderers. */
@InternalApi
final class RendererSupport {
  static final float BORDER_WIDTH = 1.0F;
  static final float PADDING = 4.0F;
  static final float SCROLLBAR_THUMB_THICKNESS = 3.0F;
  static final float SLIDER_THUMB_MIN_HEIGHT = 3.0F;
  static final float SLIDER_THUMB_VERTICAL_INSET = 4.0F;
  static final float SLIDER_THUMB_RADIUS = 2.0F;
  static final Alignment LEFT_CENTER = new Alignment(-1, 0);
  static final TextFormat TEXT_FORMAT = TextFormat.of().size(8.0F);
  static final Color CONTROL_SURFACE = Color.EMPTY;
  static final Color GLASS_SURFACE = new Color(0.08F, 0.10F, 0.14F, 0.28F);
  static final Color GLASS_TITLE_SURFACE = new Color(0.92F, 0.95F, 1.0F, 0.055F);
  static final Color POPUP_SURFACE = new Color(0.035F, 0.040F, 0.050F, 0.91F);
  static final Color STATE_HOVER = new Color(1.0F, 1.0F, 1.0F, 0.055F);
  static final Color STATE_PRESSED = new Color(1.0F, 1.0F, 1.0F, 0.12F);
  static final Color STATE_DISABLED = new Color(0.0F, 0.0F, 0.0F, 0.17F);
  static final Color TRACK = new Color(1.0F, 1.0F, 1.0F, 0.16F);
  static final Color TRACK_FILL = new Color(0.90F, 0.92F, 0.96F, 0.66F);
  static final Color THUMB = new Color(0.96F, 0.97F, 1.0F, 0.92F);
  static final Color FOREGROUND = new Color(0.93F, 0.94F, 0.97F, 0.96F);
  static final Color MUTED = new Color(0.70F, 0.73F, 0.78F, 0.70F);
  static final Color OUTLINE = new Color(0.90F, 0.93F, 1.0F, 0.20F);
  static final Color OUTLINE_HOVER = new Color(0.94F, 0.96F, 1.0F, 0.38F);
  static final Color OUTLINE_FOCUS = new Color(0.96F, 0.98F, 1.0F, 0.72F);
  static final Color HIGHLIGHT = new Color(1.0F, 1.0F, 1.0F, 0.11F);
  static final Color SHADOW = new Color(0.0F, 0.0F, 0.0F, 0.15F);
  static final Color CLOSE_HOVER = new Color(1.0F, 1.0F, 1.0F, 0.10F);
  static final Color CLOSE_FOREGROUND = new Color(0.95F, 0.96F, 0.99F, 0.88F);
  static final Color CHECKED_SURFACE = new Color(1.0F, 1.0F, 1.0F, 0.07F);
  static final Color SELECTION = new Color(0.2F, 0.45F, 0.85F, 0.55F);

  private RendererSupport() {
  }

  static void drawText(Graphics graphics, String value, float x, float y, Alignment alignment, Color color) {
    if (value.isEmpty()) {
      return;
    }
    Text text = Literal.of(value).with(TEXT_FORMAT.tint(color));
    graphics.drawText(text, x, y, alignment);
  }

  static void fill(Graphics graphics, Rectangle area, Color color) {
    if (color.alpha() <= 0.0F) {
      return;
    }
    graphics.setTint(color);
    graphics.drawRectangle(area);
    graphics.setTint(Color.WHITE);
  }

  static void drawBoundary(Graphics graphics, Rectangle area, Color color) {
    if (color.alpha() <= 0.0F) {
      return;
    }
    float previousWidth = graphics.strokeWidth();
    graphics.setTint(color);
    try {
      graphics.setStrokeWidth(VertexBuilder2D.SYSTEM_WIDTH);
      graphics.drawRectangleFrame(area);
    } finally {
      graphics.setStrokeWidth(previousWidth);
      graphics.setTint(Color.WHITE);
    }
  }

  static void drawTextBoxBackground(Graphics graphics, Rectangle area, boolean enabled, boolean focused) {
    // Draw the textbox surface and its focused overlay.
    drawTextBoxSurface(graphics, area, enabled, focused);
    // Draw the textbox border after the surface.
    drawTextBoxBorder(graphics, area, focused);
  }

  static void drawScrollPaneBackground(Graphics graphics, Rectangle area) {
    // Keep the viewport decoration independent from the moving content element.
    fill(graphics, area, new Color(1.0F, 1.0F, 1.0F, 0.035F));
    drawBoundary(graphics, area, OUTLINE);
  }

  private static void drawTextBoxSurface(Graphics graphics, Rectangle area, boolean enabled, boolean focused) {
    Color fill = enabled ? new Color(1.0F, 1.0F, 1.0F, 0.035F) : STATE_DISABLED;
    fill(graphics, area, fill);
    if (enabled && focused) {
      fill(graphics, area, STATE_HOVER);
    }
  }

  private static void drawTextBoxBorder(Graphics graphics, Rectangle area, boolean focused) {
    drawBoundary(graphics, area, focused ? OUTLINE_FOCUS : OUTLINE);
  }

  static void drawWindowBackground(Graphics graphics, Rectangle area) {
    // Draw the window drop shadow behind the glass surface.
    graphics.setTint(SHADOW);
    graphics.drawRectangle(area);
    // Draw the captured/blurred backdrop when the UI dispatcher provides it.
    // Draw the translucent glass surface over the backdrop.
    fill(graphics, area, GLASS_SURFACE);
    // Draw the outer frame around the complete window.
    drawBoundary(graphics, area, OUTLINE);
  }
}
