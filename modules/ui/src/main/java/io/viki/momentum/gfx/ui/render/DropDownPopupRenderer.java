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

import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.gfx.ui.element.DropDown;
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

/**
 * Draws the framed surface behind a drop-down's option rows.
 *
 * <p>The popup is a separate element part so its border and background remain above sibling
 * content while option rows are rendered in their own pass.
 */
public final class DropDownPopupRenderer implements ElementRenderer {
  /** Shared stateless renderer used by drop-down popup parts. */
  public static final DropDownPopupRenderer INSTANCE = new DropDownPopupRenderer();

  private DropDownPopupRenderer() {
  }

  /**
   * Draws the popup surface only while its owning drop-down is expanded.
   *
   * @param graphics graphics context receiving the popup
   * @param raw popup-part element to render
   * @param area absolute popup bounds
   */
  @Override
  public void render(Graphics graphics, Element raw, Rectangle area) {
    DropDown.Popup popup = (DropDown.Popup) raw;
    if (!popup.owner().expanded()) {
      return;
    }
    // Draw the popup surface behind all option rows.
    drawPopupSurface(graphics, area);
    // Draw the popup frame independent of the option rows.
    drawPopupBorder(graphics, area);
    // Draw the subtle top-edge highlight.
    drawTopHighlight(graphics, area);
  }

  private static void drawPopupSurface(Graphics graphics, Rectangle area) {
    RendererSupport.fill(graphics, area, RendererSupport.POPUP_SURFACE);
  }

  private static void drawPopupBorder(Graphics graphics, Rectangle area) {
    RendererSupport.drawBoundary(graphics, area, RendererSupport.OUTLINE);
  }

  private static void drawTopHighlight(Graphics graphics, Rectangle area) {
    graphics.setTint(RendererSupport.HIGHLIGHT);
    graphics.drawRectangle(area.minX() + RendererSupport.BORDER_WIDTH,
        area.minY() + RendererSupport.BORDER_WIDTH,
        Math.max(0.0F, area.width() - RendererSupport.BORDER_WIDTH * 2.0F),
        RendererSupport.BORDER_WIDTH);
    graphics.setTint(Color.WHITE);
  }
}
