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

package net.fmhi.gfx.ui.widget;

import net.fmhi.gfx.ui.Look;
import net.fmhi.gfx.ui.UiEvent;
import net.fmhi.gfx.ui.Widget;
import net.fmhi.gfx.util.fast2d.impl.Graphics;
import net.fmhi.math.Box2D;
import org.jspecify.annotations.Nullable;

/**
 * A rectangular container that clips its children to its own bounds.
 *
 * <p>Use a panel to group and visually separate a set of child widgets. Children are scissor-
 * clipped to the panel's content area. Rendering is delegated to {@link Look#drawPanel}.
 */
public class Panel extends Widget {
  /**
   * Creates a panel at the given position and size.
   *
   * @param x      the X position in parent-local coordinates
   * @param y      the Y position in parent-local coordinates
   * @param width  the width in logical units
   * @param height the height in logical units
   * @return the new panel
   */
  public static Panel create(float x, float y, float width, float height) {
    Panel p = new Panel();
    p.x = x;
    p.y = y;
    p.width = width;
    p.height = height;
    return p;
  }

  /**
   * Renders the panel background, then draws children clipped to the panel's content bounds.
   */
  @Override
  public void render(Graphics g, Look look, @Nullable Box2D parentClip) {
    if (!visible) {
      return;
    }
    renderSelf(g, look);

    Box2D myClip = absoluteContentBounds();
    Box2D effectiveClip = parentClip != null ? Box2D.getIntersection(parentClip, myClip) : myClip;

    g.pushScissor(effectiveClip);
    for (Widget child : children) {
      child.render(g, look, effectiveClip);
    }
    if (parentClip != null) {
      g.pushScissor(parentClip);
    } else {
      g.popScissor();
    }
  }

  @Override
  protected void renderSelf(Graphics g, Look look) {
    look.drawPanel(g, absoluteBounds());
  }

  /**
   * Handles an input event, dispatching to children only when the cursor is inside the panel's bounds. Keyboard and
   * character events always pass through.
   */
  @Override
  public boolean handleEvent(UiEvent event) {
    if (!visible || !enabled) {
      return false;
    }
    Box2D abs = absoluteBounds();
    boolean inside = switch (event) {
      case UiEvent.MouseMove(float mx, float my) -> abs.contains(mx, my);
      case UiEvent.MouseButton(float mx, float my, int b, boolean p) -> abs.contains(mx, my);
      case UiEvent.Scroll(float mx, float my, float dx, float dy) -> abs.contains(mx, my);
      default -> true; // key/char events pass through regardless of position
    };
    if (!inside) {
      return false;
    }
    return super.handleEvent(event);
  }
}
