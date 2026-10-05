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

import io.viki.momentum.gfx.ui.present.ElementRenderer;
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.ui.element.ImageView;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

/**
 * Renders an {@link ImageView} within its destination bounds.
 *
 * <p>The renderer has no per-view state and is safe to share among views used by the same UI
 * thread.
 */
public final class AutoImageViewRenderer implements ElementRenderer {
  /** Shared renderer for image views. */
  public static final AutoImageViewRenderer INSTANCE = new AutoImageViewRenderer();

  private AutoImageViewRenderer() {
  }

  /**
   * Draws the image and clips it to the element's visible area.
   *
   * @param graphics graphics context receiving the image
   * @param element  element expected to be an {@link ImageView}
   * @param area     absolute destination bounds
   */
  @Override
  public void render(Graphics graphics, Element element, Rectangle area) {
    graphics.pushScissor(area);
    try {
      ((ImageView) element).image().draw(graphics, area.minX(), area.minY(),
          area.width(), area.height());
    } finally {
      graphics.popScissor();
    }
  }
}
