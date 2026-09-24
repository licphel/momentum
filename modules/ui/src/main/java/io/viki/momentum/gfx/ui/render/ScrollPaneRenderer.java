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
import io.viki.momentum.gfx.ui.element.ScrollPane;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

/**
 * Draws the viewport surface associated with a {@link ScrollPane}.
 *
 * <p>Scrollbar parts and content are rendered by their own elements. This renderer owns only the
 * fixed viewport surface and frame, so the decoration remains stationary while content scrolls.
 */
public final class ScrollPaneRenderer implements ElementRenderer {
  /** Shared stateless renderer used by scroll panes. */
  public static final ScrollPaneRenderer INSTANCE = new ScrollPaneRenderer();

  private ScrollPaneRenderer() {
  }

  /**
   * Draws the scroll pane's fixed viewport surface and frame.
   *
   * @param graphics graphics context receiving the viewport
   * @param raw scroll-pane element to render
   * @param area absolute scroll-pane bounds
   */
  @Override
  public void render(Graphics graphics, Element raw, Rectangle area) {
    ScrollPane pane = (ScrollPane) raw;
    Rectangle viewport = Rectangle.of(area.minX(), area.minY(), pane.viewportWidth(), pane.viewportHeight());
    RendererSupport.drawScrollPaneBackground(graphics, viewport);
  }
}
