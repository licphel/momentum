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
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.ui.element.Window;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

/**
 * Draws a simulated window, its translucent surface, title bar, and title-bar controls.
 *
 * <p>The renderer deliberately leaves child clipping and backdrop capture to the dispatcher;
 * it only paints the window's own visual layers and control affordances.
 */
public final class AutoWindowRenderer implements ElementRenderer {
  /** Shared stateless renderer used by windows. */
  public static final AutoWindowRenderer INSTANCE = new AutoWindowRenderer();

  private AutoWindowRenderer() {
  }

  private static void drawTitleBar(Graphics graphics, Window window, Rectangle titleArea) {
    if (titleArea.width() <= 0.0F || titleArea.height() <= 0.0F) {
      return;
    }
    AutoRendererSupport.fill(graphics, titleArea, AutoRendererSupport.GLASS_TITLE_SURFACE);
    AutoRendererSupport.drawBoundary(graphics, titleArea, AutoRendererSupport.OUTLINE);
    graphics.drawText(window.title(), titleArea.minX() + AutoRendererSupport.PADDING,
        titleArea.centralY(), AutoRendererSupport.LEFT_CENTER);
  }

  private static void drawCloseButton(Graphics graphics, Window window, Rectangle closeArea,
                                      float titleHeight) {
    if (window.closePressed() || window.closeHovered()) {
      AutoRendererSupport.fill(graphics, closeArea,
          window.closePressed() ? AutoRendererSupport.STATE_PRESSED : AutoRendererSupport.CLOSE_HOVER);
    }
    graphics.setTint(AutoRendererSupport.CLOSE_FOREGROUND);
    float inset = titleHeight * 0.32F;
    Rectangle iconArea = Rectangle.of(closeArea.minX() + inset, closeArea.minY() + inset,
        closeArea.width() - inset * 2.0F, closeArea.height() - inset * 2.0F);
    graphics.drawLine(iconArea.minX(), iconArea.minY(), iconArea.maxX(), iconArea.maxY());
    graphics.drawLine(iconArea.maxX(), iconArea.minY(), iconArea.minX(), iconArea.maxY());
    graphics.setTint(Color.WHITE);
  }

  private static void drawMinimizeButton(Graphics graphics, Window window, Rectangle minimizeArea,
                                         float titleHeight) {
    if (window.minimizePressed() || window.minimizeHovered()) {
      AutoRendererSupport.fill(graphics, minimizeArea,
          window.minimizePressed() ? AutoRendererSupport.STATE_PRESSED : AutoRendererSupport.CLOSE_HOVER);
    }
    graphics.setTint(AutoRendererSupport.CLOSE_FOREGROUND);
    float inset = titleHeight * 0.32F;
    float y = minimizeArea.maxY() - inset;
    graphics.drawLine(minimizeArea.minX() + inset, y, minimizeArea.maxX() - inset, y);
    graphics.setTint(Color.WHITE);
  }

  private static Rectangle buttonArea(Rectangle titleArea, float titleHeight, int fromRight) {
    float minX = titleArea.maxX() - titleHeight * (fromRight + 1.0F);
    return Rectangle.of(minX, titleArea.minY(), titleHeight, titleHeight);
  }

  /**
   * Draws a window using its current title, control visibility, and interaction state.
   *
   * @param graphics graphics context receiving the window
   * @param raw      window element to render
   * @param area     absolute window bounds
   */
  @Override
  public void render(Graphics graphics, Element raw, Rectangle area) {
    renderWindow(graphics, raw, area);
  }

  private void renderWindow(Graphics graphics, Element raw, Rectangle area) {
    Window window = (Window) raw;
    // Draw the glass surface and outer frame after the dispatcher backdrop pass.
    AutoRendererSupport.drawWindowBackground(graphics, area, window.shadowEnabled());
    // Draw the title-bar surface and separator frame.
    float titleHeight = Math.min(area.height(), window.titleHeightForRender());
    Rectangle titleArea = Rectangle.of(area.minX(), area.minY(), area.width(), titleHeight);
    drawTitleBar(graphics, window, titleArea);
    if (window.minimizable()) {
      // Draw the minimize button and its horizontal icon.
      Rectangle minimizeArea = buttonArea(titleArea, titleHeight,
          window.closable() ? 1 : 0);
      drawMinimizeButton(graphics, window, minimizeArea, titleHeight);
    }
    if (window.closable()) {
      // Draw the close button hover/pressed surface and its X icon.
      Rectangle closeArea = buttonArea(titleArea, titleHeight, 0);
      drawCloseButton(graphics, window, closeArea, titleHeight);
    }
  }
}
