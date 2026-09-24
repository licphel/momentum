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
import io.viki.momentum.gfx.ui.element.CheckBox;
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

/**
 * Draws the square surface, check state, outline, and label for a {@link CheckBox}.
 *
 * <p>The renderer is stateless and uses the checkbox's current enabled, focus, hover, and
 * checked state to select the corresponding monochrome visual treatment.
 */
public final class CheckBoxRenderer implements ElementRenderer {
  /** Shared stateless renderer used by default checkbox instances. */
  public static final CheckBoxRenderer INSTANCE = new CheckBoxRenderer();

  private CheckBoxRenderer() {
  }

  /**
   * Draws a checkbox using the state encoded by the supplied widget.
   *
   * @param graphics graphics context receiving the checkbox
   * @param raw checkbox element to render
   * @param area absolute checkbox bounds
   */
  @Override
  public void render(Graphics graphics, Element raw, Rectangle area) {
    CheckBox checkBox = (CheckBox) raw;
    float size = Math.min(area.height(), checkBox.boxSizeForRender());
    Rectangle box = Rectangle.of(area.minX(), area.minY() + (area.height() - size) * 0.5F,
        size, size);
    // Draw the checkbox surface for checked, hovered, pressed, or idle state.
    drawSurface(graphics, checkBox, box);
    // Draw the checkbox outline with the current focus/hover emphasis.
    drawBorder(graphics, checkBox, box);
    // Draw the inner mark that represents the checked state.
    drawCheckStatus(graphics, checkBox, box, size);
    // Draw the label beside the checkbox square.
    drawLabel(graphics, checkBox, area, box);
  }

  private static void drawSurface(Graphics graphics, CheckBox checkBox, Rectangle box) {
    if (checkBox.checked()) {
      RendererSupport.fill(graphics, box, RendererSupport.CHECKED_SURFACE);
    } else if (checkBox.pressed() || checkBox.hovered() || checkBox.focused()) {
      RendererSupport.fill(graphics, box,
          checkBox.pressed() ? RendererSupport.STATE_PRESSED : RendererSupport.STATE_HOVER);
    }
  }

  private static void drawBorder(Graphics graphics, CheckBox checkBox, Rectangle box) {
    Color outline = !checkBox.enabled() ? new Color(RendererSupport.OUTLINE, 0.40F)
        : checkBox.focused() ? RendererSupport.OUTLINE_FOCUS
        : checkBox.hovered() || checkBox.pressed() ? RendererSupport.OUTLINE_HOVER
        : checkBox.checked() ? RendererSupport.THUMB : RendererSupport.OUTLINE;
    RendererSupport.drawBoundary(graphics, box, outline);
  }

  private static void drawCheckStatus(Graphics graphics, CheckBox checkBox, Rectangle box,
                                      float size) {
    if (checkBox.checked()) {
      float inset = Math.max(1.0F, size * checkBox.checkMarkInsetForRender());
      Rectangle mark = Rectangle.of(box.minX() + inset, box.minY() + inset,
          Math.max(0.0F, size - inset * 2.0F), Math.max(0.0F, size - inset * 2.0F));
      RendererSupport.fill(graphics, mark, RendererSupport.FOREGROUND);
    }
  }

  private static void drawLabel(Graphics graphics, CheckBox checkBox, Rectangle area,
                                Rectangle box) {
    RendererSupport.drawText(graphics, checkBox.label(), box.maxX() + RendererSupport.PADDING,
        area.centralY(), RendererSupport.LEFT_CENTER, RendererSupport.FOREGROUND);
  }
}
