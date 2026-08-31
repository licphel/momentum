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

package net.momentum.gfx.ui.widget;

import net.momentum.gfx.text.Text;
import net.momentum.gfx.ui.ButtonState;
import net.momentum.gfx.ui.Look;
import net.momentum.gfx.ui.UiEvent;
import net.momentum.gfx.ui.Widget;
import net.momentum.gfx.util.fast2d.impl.Graphics;
import net.momentum.math.Box2D;
import org.jspecify.annotations.Nullable;

/**
 * A clickable button widget.
 *
 * <p>The button tracks three interactive states — idle, hovered, and pressed — in response to
 * mouse events. Its click callback fires when the primary mouse button is released while the cursor is inside its
 * bounds and the widget is reachable. Rendering is delegated to {@link Look#drawButton}.
 */
public final class Button extends Widget {
  private ButtonState state = ButtonState.IDLE;
  private @Nullable Text label;
  private @Nullable Runnable onClick;

  /**
   * Creates a button at the given position and size.
   *
   * @param x      the X position in parent-local coordinates
   * @param y      the Y position in parent-local coordinates
   * @param width  the width in logical units
   * @param height the height in logical units
   * @return the new button
   */
  public static Button create(float x, float y, float width, float height) {
    Button b = new Button();
    b.x = x;
    b.y = y;
    b.width = width;
    b.height = height;
    return b;
  }

  /**
   * Sets the label text displayed inside the button.
   *
   * @param label the label text, or {@code null} to show no text
   */
  public void setLabel(@Nullable Text label) {
    this.label = label;
  }

  /**
   * Returns the current label text.
   *
   * @return the label text, or {@code null} if none is set
   */
  public @Nullable Text label() {
    return label;
  }

  /**
   * Sets the callback to invoke when the button is clicked.
   *
   * @param handler the callback, or {@code null} to remove the handler
   */
  public void onClick(@Nullable Runnable handler) {
    this.onClick = handler;
  }

  /**
   * Returns the current visual state of the button.
   *
   * @return the current button state
   */
  public ButtonState state() {
    return state;
  }

  @Override
  protected void renderSelf(Graphics g, Look look) {
    ButtonState drawState = enabled ? state : ButtonState.DISABLED;
    look.drawButton(g, drawState, absoluteBounds(), label);
  }

  /**
   * Handles mouse events to track hover and press states, and fires the click callback on primary button release.
   */
  @Override
  public boolean handleEvent(UiEvent event, boolean reachable) {
    if (!visible || !enabled) {
      return false;
    }
    Box2D abs = absoluteBounds();
    return switch (event) {
      case UiEvent.MouseMove(float mx, float my) -> {
        if (!reachable) {
          // covered — clear hover so button doesn't stay highlighted
          if (state == ButtonState.HOVERED) {
            state = ButtonState.IDLE;
          }
          yield false;
        }
        boolean inside = abs.contains(mx, my);
        if (inside && state == ButtonState.IDLE) {
          state = ButtonState.HOVERED;
        } else if (!inside && state == ButtonState.HOVERED) {
          state = ButtonState.IDLE;
        }
        yield false;
      }
      case UiEvent.MouseButton(float mx, float my, int button, boolean pressed) -> {
        if (button != 0) {
          yield false;
        }
        boolean inside = abs.contains(mx, my);
        if (pressed) {
          if (!inside || !reachable) {
            yield false;
          }
          state = ButtonState.PRESSED;
          yield true;
        } else {
          // release always fires to complete a press-drag-release sequence
          if (state == ButtonState.PRESSED) {
            if (inside && reachable && onClick != null) {
              onClick.run();
            }
            state = inside && reachable ? ButtonState.HOVERED : ButtonState.IDLE;
            yield inside && reachable;
          }
          yield false;
        }
      }
      default -> false;
    };
  }
}
