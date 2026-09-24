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

import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.ui.element.ScrollBar;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

/**
 * Draws a {@link ScrollBar} track, independent frame, and movable thumb.
 *
 * <p>The track frame is intentionally separate from the hosting scroll pane so the scrollbar
 * remains visually distinct even when the two controls touch.
 */
public final class ScrollBarRenderer implements ElementRenderer {
  /** Shared stateless renderer used by scrollbars. */
  public static final ScrollBarRenderer INSTANCE = new ScrollBarRenderer();

  private ScrollBarRenderer() {
  }

  /**
   * Draws the scrollbar frame and current thumb geometry.
   *
   * @param graphics graphics context receiving the scrollbar
   * @param raw scrollbar element to render
   * @param area absolute scrollbar bounds
   */
  @Override
  public void render(Graphics graphics, Element raw, Rectangle area) {
    ScrollBar scrollBar = (ScrollBar) raw;
    // Draw the scrollbar's independent outer frame.
    drawFrame(graphics, scrollBar, area);
    // Compute the thumb geometry in the current orientation.
    Rectangle thumb = scrollBar.thumbBounds(area);
    Rectangle visual = visualThumb(area, thumb);
    // Draw the thumb fill over the track.
    drawThumbSurface(graphics, visual);
    // Draw the thumb frame separately from the scrollbar frame.
    drawThumbFrame(graphics, scrollBar, visual);
  }

  private static void drawFrame(Graphics graphics, ScrollBar scrollBar, Rectangle area) {
    var border = scrollBar.focused() ? RendererSupport.OUTLINE_FOCUS
        : scrollBar.hovered() ? RendererSupport.OUTLINE_HOVER : RendererSupport.OUTLINE;
    RendererSupport.drawBoundary(graphics, area, border);
  }

  private static Rectangle visualThumb(Rectangle area, Rectangle thumb) {
    return area.width() >= area.height()
        ? Rectangle.of(thumb.minX(), area.centralY() - RendererSupport.SCROLLBAR_THUMB_THICKNESS
        * 0.5F, thumb.width(), RendererSupport.SCROLLBAR_THUMB_THICKNESS)
        : Rectangle.of(area.centralX() - RendererSupport.SCROLLBAR_THUMB_THICKNESS * 0.5F,
        thumb.minY(), RendererSupport.SCROLLBAR_THUMB_THICKNESS, thumb.height());
  }

  private static void drawThumbSurface(Graphics graphics, Rectangle visual) {
    RendererSupport.fill(graphics, visual, RendererSupport.TRACK_FILL);
  }

  private static void drawThumbFrame(Graphics graphics, ScrollBar scrollBar, Rectangle visual) {
    var border = scrollBar.focused() ? RendererSupport.OUTLINE_FOCUS
        : scrollBar.hovered() ? RendererSupport.OUTLINE_HOVER : RendererSupport.OUTLINE;
    RendererSupport.drawBoundary(graphics, visual, border);
  }
}
