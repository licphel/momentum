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

package io.viki.momentum.gfx.ui.element;

import io.viki.momentum.input.KeyAction;
import io.viki.momentum.input.KeyCode;
import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.gfx.ui.render.ElementRenderer;
import io.viki.momentum.gfx.ui.render.WindowRenderer;
import org.jspecify.annotations.Nullable;

/**
 * Provides a simulated window with a title bar, optional controls, and a clipped content area.
 *
 * <p>A window participates in the ordinary element tree, can be constrained to its parent, and
 * consumes pointer input over its entire surface so that sibling content cannot receive events
 * through it. Geometry and interaction state are mutable and must be accessed on the owning UI
 * thread.
 */
public final class Window extends Element {
  private String title;
  private boolean movable = true;
  private boolean closable = true;
  private boolean minimizable = true;
  private boolean closed;
  private boolean minimized;
  private boolean dragging;
  private boolean closeHovered;
  private boolean closePressed;
  private boolean minimizeHovered;
  private boolean minimizePressed;
  private float titleHeight = 18.0F;
  private float dragOffsetX;
  private float dragOffsetY;
  private @Nullable Rectangle restoredBounds;
  private @Nullable Runnable onClose;

  /**
   * Creates a window at the supplied local bounds.
   *
   * @param bounds initial local bounds
   * @param title title displayed in the title bar
   */
  public Window(Rectangle bounds, String title) {
    super(bounds);
    this.title = title;
  }
  /**
   * Returns the title displayed in the title bar.
   *
   * @return current window title
   */
  public String title() {
    return title;
  }

  /**
   * Changes the title-bar text.
   *
   * @param value new title
   */
  public void setTitle(String value) {
    title = value;
  }

  @Override
  public void setBounds(Rectangle value) {
    Rectangle constrained = constrainToParent(value);
    if (!minimized) {
      super.setBounds(constrained);
      return;
    }
    Rectangle normal = restoredBounds;
    float normalHeight = normal == null ? constrained.height() : normal.height();
    restoredBounds = Rectangle.of(constrained.minX(), constrained.minY(), constrained.width(),
        normalHeight);
    super.setBounds(Rectangle.of(constrained.minX(), constrained.minY(), constrained.width(),
        Math.min(titleHeight, normalHeight)));
  }
  /**
   * Returns the normal title-bar height used by this window.
   *
   * @return title-bar height in logical units
   */
  public float titleHeight() {
    return titleHeight;
  }

  /**
   * Sets the title-bar height and updates minimized geometry when necessary.
   *
   * @param value finite, non-negative title-bar height
   * @throws IllegalArgumentException if {@code value} is negative or not finite
   */
  public void setTitleHeight(float value) {
    if (!Float.isFinite(value) || value < 0.0F) {
      throw new IllegalArgumentException("Window title height must be finite and non-negative: "
          + value);
    }
    titleHeight = value;
    if (minimized) {
      Rectangle normal = restoredBounds;
      float height = normal == null ? bounds().height() : normal.height();
      Rectangle current = bounds();
      super.setBounds(Rectangle.of(current.minX(), current.minY(), current.width(),
          Math.min(value, height)));
    }
  }
  /**
   * Reports whether title-bar dragging is enabled.
   *
   * @return whether the window is movable
   */
  public boolean movable() {
    return movable;
  }

  /**
   * Enables or disables title-bar dragging.
   *
   * @param value {@code true} to allow dragging
   */
  public void setMovable(boolean value) {
    movable = value;
    if (!value) {
      dragging = false;
    }
  }
  /**
   * Reports whether the close button is enabled.
   *
   * @return whether the window is closable
   */
  public boolean closable() {
    return closable;
  }
  /**
   * Reports whether the minimize button is enabled.
   *
   * @return whether the window is minimizable
   */
  public boolean minimizable() {
    return minimizable;
  }

  /**
   * Enables or disables the minimize button.
   *
   * @param value {@code true} to show and accept minimize interaction
   */
  public void setMinimizable(boolean value) {
    minimizable = value;
    if (!value) {
      minimizeHovered = false;
      minimizePressed = false;
    }
  }
  /**
   * Reports whether the pointer is over the close button.
   *
   * @return whether the close button is hovered
   */
  public boolean closeHovered() {
    return closeHovered;
  }
  /**
   * Reports whether the close button is being pressed.
   *
   * @return whether the close button is pressed
   */
  public boolean closePressed() {
    return closePressed;
  }
  /**
   * Reports whether the pointer is over the minimize button.
   *
   * @return whether the minimize button is hovered
   */
  public boolean minimizeHovered() {
    return minimizeHovered;
  }
  /**
   * Reports whether the minimize button is being pressed.
   *
   * @return whether the minimize button is pressed
   */
  public boolean minimizePressed() {
    return minimizePressed;
  }
  /**
   * Reports whether the window is currently collapsed to its title bar.
   *
   * @return whether the window is minimized
   */
  public boolean minimized() {
    return minimized;
  }
  /**
   * Returns the effective title-bar height used by the renderer.
   *
   * @return rendered title-bar height in logical units
   */
  public float titleHeightForRender() {
    return effectiveTitleHeight();
  }

  /**
   * Enables or disables the close button.
   *
   * @param value {@code true} to show and accept close interaction
   */
  public void setClosable(boolean value) {
    closable = value;
    if (!value) {
      closeHovered = false;
      closePressed = false;
    }
  }
  /**
   * Reports whether the window has been closed.
   *
   * @return whether the window is closed
   */
  public boolean closed() {
    return closed;
  }

  /**
   * Installs a callback invoked once when the window closes.
   *
   * @param value callback, or {@code null} to remove the current callback
   */
  public void setOnClose(@Nullable Runnable value) {
    onClose = value;
  }

  /**
   * Closes the window, hides it, and invokes the close callback if one is configured.
   *
   * <p>The operation is idempotent; a closed window can be shown again with {@link #open()}.
   */
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    dragging = false;
    closePressed = false;
    minimizePressed = false;
    setVisible(false);
    if (onClose != null) {
      onClose.run();
    }
  }

  /**
   * Reopens the window and makes it visible without changing its geometry.
   */
  public void open() {
    closed = false;
    setVisible(true);
  }

  /**
   * Collapses the window to its title bar.
   */
  public void minimize() {
    setMinimized(true);
  }

  /**
   * Restores the geometry saved before minimization.
   */
  public void restore() {
    setMinimized(false);
  }

  /**
   * Sets the minimized state while preserving and restoring normal bounds.
   *
   * @param value {@code true} to collapse to the title bar
   */
  public void setMinimized(boolean value) {
    if (value == minimized) {
      return;
    }
    if (value) {
      Rectangle current = constrainToParent(bounds());
      restoredBounds = current;
      minimized = true;
      super.setBounds(Rectangle.of(current.minX(), current.minY(), current.width(),
          Math.min(titleHeight, current.height())));
    } else {
      Rectangle normal = restoredBounds;
      minimized = false;
      restoredBounds = null;
      if (normal != null) {
        setBounds(normal);
      }
    }
    dragging = false;
    closeHovered = false;
    closePressed = false;
    minimizeHovered = false;
    minimizePressed = false;
  }

  /**
   * Adds an element to the window's clipped client area.
   *
   * @param element content element to attach
   * @throws IllegalArgumentException if the element is already attached or creates a cycle
   */
  public void addContent(Element element) {
    addChild(element);
  }

  @Override
  protected ElementRenderer defaultRenderer() {
    return WindowRenderer.INSTANCE;
  }

  @Override
  public boolean isRenderLayerBoundary() {
    return true;
  }

  @Override
  protected Rectangle childrenClip(Rectangle absoluteBounds) {
    float titleHeight = effectiveTitleHeight();
    return Rectangle.of(
        absoluteBounds.minX(),
        absoluteBounds.minY() + titleHeight,
        absoluteBounds.width(),
        Math.max(0.0F, absoluteBounds.height() - titleHeight));
  }

  @Override
  protected void onAttached() {
    setBounds(bounds());
  }

  @Override
  public boolean onMouseMove(float x, float y) {
    if (dragging) {
      Rectangle current = bounds();
      setBounds(Rectangle.of(current.minX() + x - dragOffsetX,
          current.minY() + y - dragOffsetY, current.width(), current.height()));
    }
    closeHovered = closable && inCloseButton(x, y);
    minimizeHovered = minimizable && inMinimizeButton(x, y);
    // A window is an opaque input surface. Empty content must not expose siblings below it.
    return true;
  }

  @Override
  public boolean onMouseButton(float x, float y, KeyCode button, KeyAction action, int modifiers) {
    if (action == KeyAction.PRESS && button.mouseId() >= 0) {
      Canvas canvas = owningCanvas();
      if (canvas != null) {
        canvas.bringWindowToFrontAt(absoluteBounds().minX() + x, absoluteBounds().minY() + y);
      }
    }
    if (button != KeyCode.MOUSE_LEFT) {
      return true;
    }
    if (action == KeyAction.PRESS && closable && inCloseButton(x, y)) {
      closePressed = true;
      return true;
    }
    if (action == KeyAction.PRESS && minimizable && inMinimizeButton(x, y)) {
      minimizePressed = true;
      return true;
    }
    if (action == KeyAction.RELEASE && closePressed) {
      closePressed = false;
      if (inCloseButton(x, y)) {
        close();
      }
      return true;
    }
    if (action == KeyAction.RELEASE && minimizePressed) {
      minimizePressed = false;
      if (inMinimizeButton(x, y)) {
        setMinimized(!minimized);
      }
      return true;
    }
    if (action == KeyAction.RELEASE && dragging) {
      dragging = false;
      return true;
    }
    if (action == KeyAction.PRESS) {
      if (movable) {
        dragging = true;
        dragOffsetX = x;
        dragOffsetY = y;
      }
    }
    // Consume clicks in the client area even when this window is not movable.
    return true;
  }

  @Override
  public boolean onScroll(float x, float y, double deltaX, double deltaY) {
    return true;
  }

  @Override
  public void onPointerCancel(KeyCode button) {
    dragging = false;
    closePressed = false;
    minimizePressed = false;
  }

  @Override
  public void onPointerExit() {
    closeHovered = false;
    minimizeHovered = false;
  }

  private float effectiveTitleHeight() {
    return Math.min(titleHeight, bounds().height());
  }

  private boolean inCloseButton(float x, float y) {
    float height = effectiveTitleHeight();
    float minX = bounds().width() - (closable ? height : 0.0F);
    return closable && x >= minX && x <= minX + height && y >= 0.0F && y <= height;
  }

  private boolean inMinimizeButton(float x, float y) {
    float height = effectiveTitleHeight();
    float rightInset = closable ? height : 0.0F;
    float minX = bounds().width() - rightInset - height;
    return minimizable && x >= minX && x <= minX + height && y >= 0.0F && y <= height;
  }

  private Rectangle constrainToParent(Rectangle value) {
    Element parent = parent();
    if (parent == null) {
      return value;
    }

    Rectangle parentBounds = parent.bounds();
    float minX = 0.0F;
    float minY = 0.0F;
    float parentWidth = parentBounds.width();
    float parentHeight = parentBounds.height();

    if (parent instanceof Window parentWindow) {
      minY = parentWindow.effectiveTitleHeight();
      parentHeight = Math.max(0.0F, parentHeight - minY);
    }

    float width = Math.min(value.width(), parentWidth);
    float height = Math.min(value.height(), parentHeight);
    float maxX = Math.max(minX, parentWidth - width);
    float maxY = Math.max(minY, minY + parentHeight - height);
    float x = Math.max(minX, Math.min(value.minX(), maxX));
    float y = Math.max(minY, Math.min(value.minY(), maxY));

    return Rectangle.of(x, y, width, height);
  }
}
