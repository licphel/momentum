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

import io.viki.momentum.gfx.ui.element.DropDown;
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

/**
 * Draws one visible option row inside a {@link DropDown} popup.
 *
 * <p>Rows derive their selected and hovered state from their owning drop-down. The renderer is
 * stateless and therefore safe to share across all option parts on the UI thread.
 */
public final class DropDownOptionRenderer implements ElementRenderer {
  /** Shared stateless renderer used by option parts. */
  public static final DropDownOptionRenderer INSTANCE = new DropDownOptionRenderer();

  private DropDownOptionRenderer() {
  }

  /**
   * Draws an option row when its owning drop-down is expanded.
   *
   * @param graphics graphics context receiving the row
   * @param raw option-part element to render
   * @param area absolute row bounds
   */
  @Override
  public void render(Graphics graphics, Element raw, Rectangle area) {
    DropDown.OptionPart option = (DropDown.OptionPart) raw;
    DropDown owner = option.owner();
    if (!owner.expanded()) {
      return;
    }
    // Draw the selected or hovered option row surface.
    drawStateSurface(graphics, option, owner, area);
    // Draw the option label inside the popup row.
    drawOptionLabel(graphics, owner, option, area);
  }

  private static void drawStateSurface(Graphics graphics, DropDown.OptionPart option,
                                       DropDown owner, Rectangle area) {
    if (option.index() == owner.selectedIndex()) {
      RendererSupport.fill(graphics, area, RendererSupport.STATE_PRESSED);
    } else if (option.optionHovered()) {
      RendererSupport.fill(graphics, area, RendererSupport.STATE_HOVER);
    }
  }

  private static void drawOptionLabel(Graphics graphics, DropDown owner,
                                      DropDown.OptionPart option, Rectangle area) {
    RendererSupport.drawText(graphics, owner.options().get(option.index()),
        area.minX() + RendererSupport.PADDING, area.centralY(), RendererSupport.LEFT_CENTER,
        RendererSupport.FOREGROUND);
  }
}
