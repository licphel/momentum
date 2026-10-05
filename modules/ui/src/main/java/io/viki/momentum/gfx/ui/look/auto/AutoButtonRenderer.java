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
import io.viki.momentum.gfx.ui.element.Button;
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.util.Alignment;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

/**
 * Draws the surface, outline, and centered label for a {@link Button}.
 *
 * <p>The renderer reads interaction state from the button and contains no persistent widget
 * state, so the shared instance can be used by every button on the UI thread.
 */
public final class AutoButtonRenderer implements ElementRenderer {
  /** Shared stateless renderer used by default button instances. */
  public static final AutoButtonRenderer INSTANCE = new AutoButtonRenderer();

  private AutoButtonRenderer() {
  }

  private static void drawSurface(Graphics graphics, Rectangle area, Button button,
                                  Button.State state) {
    var fill = switch (state) {
      case IDLE -> button.focused() ? AutoRendererSupport.STATE_HOVER : AutoRendererSupport.CONTROL_SURFACE;
      case HOVERED -> AutoRendererSupport.STATE_HOVER;
      case PRESSED -> AutoRendererSupport.STATE_PRESSED;
      case DISABLED -> AutoRendererSupport.STATE_DISABLED;
    };
    AutoRendererSupport.fill(graphics, area, fill);
  }

  private static void drawBorder(Graphics graphics, Rectangle area, Button button,
                                 Button.State state) {
    var outline = state == Button.State.DISABLED
        ? new Color(AutoRendererSupport.OUTLINE, 0.35F)
        : button.focused() ? AutoRendererSupport.OUTLINE_FOCUS
        : state == Button.State.HOVERED ? AutoRendererSupport.OUTLINE_HOVER
        : AutoRendererSupport.OUTLINE;
    AutoRendererSupport.drawBoundary(graphics, area, outline);
  }

  private static void drawLabel(Graphics graphics, Button button, Rectangle area) {
    graphics.drawText(button.label(), area.centralX(), area.centralY(), Alignment.CENTRAL);
  }

  /**
   * Draws a button using the state encoded by the supplied widget.
   *
   * @param graphics graphics context receiving the button
   * @param raw      button element to render
   * @param area     absolute button bounds
   */
  @Override
  public void render(Graphics graphics, Element raw, Rectangle area) {
    Button button = (Button) raw;
    Button.State state = button.state();
    // Draw the button surface for its current interaction state.
    drawSurface(graphics, area, button, state);
    // Draw the button outline, including focused and disabled variants.
    drawBorder(graphics, area, button, state);
    // Draw the button label centered inside the control.
    drawLabel(graphics, button, area);
  }
}
