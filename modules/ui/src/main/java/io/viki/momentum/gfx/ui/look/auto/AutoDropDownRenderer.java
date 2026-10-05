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

import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.gfx.ui.present.ElementRenderer;
import io.viki.momentum.gfx.ui.element.DropDown;
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

/**
 * Draws the selectable header of a {@link DropDown}, including its current option and indicator.
 *
 * <p>Popup rows are rendered separately by {@link AutoDropDownOptionRenderer}; this renderer only
 * owns the header surface and its interaction-state outline.
 */
public final class AutoDropDownRenderer implements ElementRenderer {
  /** Shared stateless renderer used by default drop-down instances. */
  public static final AutoDropDownRenderer INSTANCE = new AutoDropDownRenderer();

  private AutoDropDownRenderer() {
  }

  private static void drawSurface(Graphics graphics, DropDown dropDown, Rectangle area) {
    if (dropDown.expanded() || dropDown.hovered() || dropDown.focused()) {
      AutoRendererSupport.fill(graphics, area,
          dropDown.expanded() ? AutoRendererSupport.STATE_PRESSED : AutoRendererSupport.STATE_HOVER);
    }
  }

  private static void drawBorder(Graphics graphics, DropDown dropDown, Rectangle area) {
    var outline = !dropDown.enabled() ? new Color(
        AutoRendererSupport.OUTLINE, 0.35F)
        : dropDown.focused() || dropDown.expanded() ? AutoRendererSupport.OUTLINE_FOCUS
        : dropDown.hovered() ? AutoRendererSupport.OUTLINE_HOVER : AutoRendererSupport.OUTLINE;
    AutoRendererSupport.drawBoundary(graphics, area, outline);
  }

  private static void drawSelectedOption(Graphics graphics, DropDown dropDown, Rectangle area) {
    AutoRendererSupport.drawText(graphics, dropDown.selectedOption(),
        area.minX() + AutoRendererSupport.PADDING, area.centralY(), AutoRendererSupport.LEFT_CENTER);
  }

  private static void drawCollapseIndicator(Graphics graphics, DropDown dropDown, Rectangle area) {
    float halfSize = Math.min(3.0F, area.height() * 0.2F);
    float centerX = area.maxX() - AutoRendererSupport.PADDING - halfSize;
    float centerY = area.centralY();
    graphics.setTint(AutoRendererSupport.FOREGROUND);
    if (dropDown.expanded()) {
      graphics.drawTriangle(centerX - halfSize, centerY + halfSize,
          centerX + halfSize, centerY + halfSize, centerX, centerY - halfSize);
    } else {
      graphics.drawTriangle(centerX - halfSize, centerY - halfSize,
          centerX + halfSize, centerY - halfSize, centerX, centerY + halfSize);
    }
    graphics.setTint(Color.WHITE);
  }

  /**
   * Draws a drop-down header using the state encoded by the supplied widget.
   *
   * @param graphics graphics context receiving the header
   * @param raw      drop-down element to render
   * @param area     absolute header bounds
   */
  @Override
  public void render(Graphics graphics, Element raw, Rectangle area) {
    DropDown dropDown = (DropDown) raw;
    // Draw the expanded, hovered, or idle header surface.
    drawSurface(graphics, dropDown, area);
    // Draw the header border with focus and disabled emphasis.
    drawBorder(graphics, dropDown, area);
    // Draw the currently selected option text.
    drawSelectedOption(graphics, dropDown, area);
    // Draw the collapse/expand triangle indicator.
    drawCollapseIndicator(graphics, dropDown, area);
  }
}
