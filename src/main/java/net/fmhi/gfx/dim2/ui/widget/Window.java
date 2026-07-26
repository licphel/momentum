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

package net.fmhi.gfx.dim2.ui.widget;

import net.fmhi.gfx.dim2.mesh.Graphics2D;
import net.fmhi.gfx.dim2.ui.*;
import net.fmhi.gfx.dim2.ui.*;
import net.fmhi.gfx.text.Text;
import net.fmhi.gfx.dim2.ui.*;
import net.fmhi.gfx.dim2.ui.*;
import net.fmhi.math.Box2D;
import org.jspecify.annotations.Nullable;

/**
 * A draggable, resizable, closeable, minimizable, and maximizable floating window.
 *
 * <p>A {@code Window} is the primary container for grouping widgets into independent panels.
 * It consists of outer chrome — a border and a title bar with decoration buttons — and an inner content area to which
 * children are added.
 *
 * <p>Instances are created via {@link #create(float, float, float, float)} and registered with
 * {@link UiContext#addWindow(Window)}. The context must be set before the window can participate in focus management
 * and maximize/restore operations. Dragging the title bar moves the window; dragging the bottom-right corner resizes it
 * (if enabled).
 *
 * <p>This class is not thread-safe.
 */
public final class Window extends Widget {
  private static final int DRAG_NONE = 0;
  private static final int DRAG_TITLE = 1;
  private static final int DRAG_RESIZE = 2;
  private static final float MIN_WIDTH = 80.0F;
  private static final float MIN_HEIGHT = 40.0F;
  private static final float BTN_SIZE = 14.0F;
  private static final float BTN_PAD = 4.0F;

  /** The owning {@link UiContext}. Set by {@link UiContext#addWindow(Window)}. */
  @Nullable UiContext context;
  private @Nullable Text title;
  private boolean closeable = true;
  private boolean resizable = false;
  private boolean minimizable = true;
  private boolean maximizable = false;
  private boolean minimized = false;
  private boolean maximized = false;
  private boolean focused = false;
  // drag / resize state
  private int dragMode = DRAG_NONE;
  private float dragOffsetX;
  private float dragOffsetY;
  private float preDragW;
  private float preDragH;
  // saved pre-minimize / pre-maximize geometry
  private float savedMinH;
  private float savedMaxX;
  private float savedMaxY;
  private float savedMaxW;
  private float savedMaxH;
  // absolute bounds of decoration buttons (recomputed each layout())
  private Box2D closeButtonBounds = Box2D.ZERO;
  private Box2D minButtonBounds = Box2D.ZERO;
  private Box2D maxButtonBounds = Box2D.ZERO;
  // hover state for decoration buttons
  private boolean closeHovered;
  private boolean minHovered;
  private boolean maxHovered;

  /**
   * Creates a floating window at the given position and size.
   *
   * @param x      the X position in absolute root coordinates
   * @param y      the Y position in absolute root coordinates
   * @param width  the width in logical units
   * @param height the height in logical units
   * @return the new window
   */
  public static Window create(float x, float y, float width, float height) {
    Window w = new Window();
    w.x = x;
    w.y = y;
    w.width = width;
    w.height = height;
    return w;
  }

  /**
   * Sets the owning {@link UiContext}. Called by {@link UiContext#addWindow(Window)}.
   *
   * @param ctx the context, or {@code null} to clear
   */
  public void setContext(@Nullable UiContext ctx) {
    this.context = ctx;
  }

  /**
   * Sets the title text displayed in the title bar.
   *
   * @param title the title text, or {@code null} for no title
   */
  public void setTitle(@Nullable Text title) {
    this.title = title;
  }

  /**
   * Sets whether the minimize button is shown.
   *
   * @param v {@code true} to show the minimize button
   */
  public void setMinimizable(boolean v) {
    this.minimizable = v;
  }

  /**
   * Sets whether the maximize button is shown.
   *
   * @param v {@code true} to show the maximize button
   */
  public void setMaximizable(boolean v) {
    this.maximizable = v;
  }

  /**
   * Returns the title text.
   *
   * @return the title text, or {@code null} if none is set
   */
  public @Nullable Text title() {
    return title;
  }

  /**
   * Returns whether the close button is shown.
   *
   * @return {@code true} if closeable
   */
  public boolean isCloseable() {
    return closeable;
  }

  /**
   * Sets whether the close button is shown.
   *
   * @param v {@code true} to show the close button
   */
  public void setCloseable(boolean v) {
    this.closeable = v;
  }

  /**
   * Returns whether the window is resizable by dragging the bottom-right corner.
   *
   * @return {@code true} if resizable
   */
  public boolean isResizable() {
    return resizable;
  }

  /**
   * Sets whether the window is resizable by dragging the bottom-right corner.
   *
   * @param v {@code true} to enable resizing
   */
  public void setResizable(boolean v) {
    this.resizable = v;
  }

  /**
   * Returns whether the window is minimized.
   *
   * @return {@code true} if minimized
   */
  public boolean isMinimized() {
    return minimized;
  }

  /**
   * Returns whether the window is maximized.
   *
   * @return {@code true} if maximized
   */
  public boolean isMaximized() {
    return maximized;
  }

  /**
   * Returns whether the window has keyboard focus.
   *
   * @return {@code true} if focused
   */
  public boolean isFocused() {
    return focused;
  }

  /**
   * Sets the keyboard focus state. Called by {@link UiContext#bringToFront(Window)}.
   *
   * @param focused {@code true} if this window has focus
   */
  public void setFocused(boolean focused) {
    this.focused = focused;
  }

  /**
   * Shows the window.
   */
  public void open() {
    setVisible(true);
  }

  /**
   * Hides the window without destroying it.
   */
  public void hide() {
    setVisible(false);
  }

  /**
   * Returns whether the window is visible.
   *
   * @return {@code true} if visible
   */
  public boolean isOpen() {
    return visible;
  }

  private float titleBarHeight() {
    return context != null ? context.look().titleBarHeight() : 22.0F;
  }

  private float borderWidth() {
    return context != null ? context.look().borderWidth() : 1.0F;
  }

  private float resizeHandleSize() {
    return context != null ? context.look().resizeHandleSize() : 10.0F;
  }

  /**
   * Returns the inner content area in parent-local coordinates, below the title bar and inside the border. Children
   * added to this window are positioned relative to this area's top-left corner. When minimized, the content height is
   * zero.
   */
  @Override
  public Box2D contentBounds() {
    float bw = borderWidth();
    float tbh = titleBarHeight();
    // visible content height: full height minus title bar minus bottom border
    float contentH = minimized ? 0.0F : (height - tbh - bw);
    return Box2D.create(x + bw, y + tbh, width - bw * 2.0F, Math.max(0.0F, contentH));
  }

  /**
   * Computes decoration button positions and lays out children relative to the content area. Floating windows use
   * absolute root coordinates, so the parent content is ignored.
   */
  @Override
  public void layout(Box2D parentContent) {
    // Floating windows are positioned in absolute root coords; the parentContent is ignored
    // (the x/y set directly by the user or drag system is used as-is).
    Box2D abs = absoluteBounds();
    float tbh = titleBarHeight();
    float ry = abs.minY() + (tbh - BTN_SIZE) / 2.0F;
    float rx = abs.maxX() - BTN_PAD - BTN_SIZE;

    if (closeable) {
      closeButtonBounds = Box2D.create(rx, ry, BTN_SIZE, BTN_SIZE);
      rx -= BTN_SIZE + BTN_PAD;
    }
    if (maximizable) {
      maxButtonBounds = Box2D.create(rx, ry, BTN_SIZE, BTN_SIZE);
      rx -= BTN_SIZE + BTN_PAD;
    }
    if (minimizable) {
      minButtonBounds = Box2D.create(rx, ry, BTN_SIZE, BTN_SIZE);
    }

    // Layout children relative to the absolute content origin.
    Box2D absContent = absoluteContentBounds();
    for (Widget child : children) {
      child.layout(absContent);
    }
  }

  /**
   * Renders the window chrome, then draws children clipped to the content area. Minimized windows skip child
   * rendering.
   */
  @Override
  public void render(Graphics2D g, Look look, @Nullable Box2D parentClip) {
    if (!visible) {
      return;
    }
    renderSelf(g, look);

    if (!minimized && !children.isEmpty()) {
      Box2D contentAbs = absoluteContentBounds();
      Box2D effectiveClip = parentClip != null ? Box2D.getIntersection(parentClip, contentAbs) : contentAbs;

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
  }

  @Override
  protected void renderSelf(Graphics2D g, Look look) {
    Box2D abs = absoluteBounds();
    WindowState ws = focused ? WindowState.FOCUSED : WindowState.NORMAL;
    look.drawWindowFrame(g, ws, abs);

    Box2D titleAbs = absoluteTitleBarBounds();
    look.drawWindowTitleBar(g, focused, titleAbs, title);

    if (closeable) {
      look.drawWindowCloseButton(g, closeHovered, closeButtonBounds);
    }
    if (maximizable) {
      look.drawWindowMaximizeButton(g, maxHovered, maxButtonBounds);
    }
    if (minimizable) {
      look.drawWindowMinimizeButton(g, minHovered, minButtonBounds);
    }
  }

  /**
   * Handles mouse, keyboard, and character events for this window. Manages title bar dragging, resize handle
   * interaction, and decoration button clicks. Delegates to children for events inside the content area.
   */
  @Override
  public boolean handleEvent(UiEvent event, boolean reachable) {
    if (!visible) {
      return false;
    }
    return switch (event) {
      case UiEvent.MouseMove mm -> handleMouseMove(mm, reachable);
      case UiEvent.MouseButton mb -> handleMouseButton(mb, reachable);
      case UiEvent.Scroll sc -> handleScroll(sc, reachable);
      case UiEvent.Key k -> {
        if (focused) {
          yield super.handleEvent(k, reachable);
        }
        yield false;
      }
      case UiEvent.Char c -> {
        if (focused) {
          yield super.handleEvent(c, reachable);
        }
        yield false;
      }
    };
  }

  /** Title-bar bounds in absolute logical coordinates. */
  private Box2D absoluteTitleBarBounds() {
    Box2D abs = absoluteBounds();
    return Box2D.create(abs.minX(), abs.minY(), abs.width(), titleBarHeight());
  }

  /** Resize handle bounds in absolute logical coordinates. */
  private Box2D absoluteResizeHandleBounds() {
    Box2D abs = absoluteBounds();
    float s = resizeHandleSize();
    return Box2D.create(abs.maxX() - s, abs.maxY() - s, s, s);
  }

  private boolean handleMouseButton(UiEvent.MouseButton mb, boolean reachable) {
    Box2D abs = absoluteBounds();
    if (!abs.contains(mb.x(), mb.y())) {
      if (mb.button() == 0 && !mb.pressed()) {
        dragMode = DRAG_NONE;
      }
      return false;
    }

    // Bring to front only when reachable.
    if (mb.pressed() && reachable && context != null) {
      context.bringToFront(this);
    }

    if (!reachable) {
      // covered — don't start new drags or fire decoration buttons, but end active ones.
      if (mb.button() == 0 && !mb.pressed()) {
        dragMode = DRAG_NONE;
      }
      return false;
    }

    if (mb.button() == 0) {
      if (closeable && closeButtonBounds.contains(mb.x(), mb.y())) {
        if (!mb.pressed()) {
          hide();
        }
        return true;
      }
      if (maximizable && maxButtonBounds.contains(mb.x(), mb.y())) {
        if (!mb.pressed()) {
          maximize();
        }
        return true;
      }
      if (minimizable && minButtonBounds.contains(mb.x(), mb.y())) {
        if (!mb.pressed()) {
          minimize();
        }
        return true;
      }

      Box2D titleAbs = absoluteTitleBarBounds();
      if (titleAbs.contains(mb.x(), mb.y())) {
        if (mb.pressed()) {
          dragMode = DRAG_TITLE;
          dragOffsetX = mb.x() - x;
          dragOffsetY = mb.y() - y;
        } else {
          dragMode = DRAG_NONE;
        }
        return true;
      }

      if (resizable && !minimized) {
        Box2D handle = absoluteResizeHandleBounds();
        if (handle.contains(mb.x(), mb.y())) {
          if (mb.pressed()) {
            dragMode = DRAG_RESIZE;
            dragOffsetX = mb.x();
            dragOffsetY = mb.y();
            preDragW = width;
            preDragH = height;
          } else {
            dragMode = DRAG_NONE;
          }
          return true;
        }
      }
    }

    if (!minimized) {
      return super.handleEvent(mb, true);
    }
    return true;
  }

  private boolean handleMouseMove(UiEvent.MouseMove mm, boolean reachable) {
    Box2D abs = absoluteBounds();
    boolean inside = abs.contains(mm.x(), mm.y());

    if (inside && reachable) {
      closeHovered = closeable && closeButtonBounds.contains(mm.x(), mm.y());
      minHovered = minimizable && minButtonBounds.contains(mm.x(), mm.y());
      maxHovered = maximizable && maxButtonBounds.contains(mm.x(), mm.y());
    } else {
      closeHovered = false;
      minHovered = false;
      maxHovered = false;
    }

    // Drag continues regardless of reachable — already started, must complete
    if (dragMode == DRAG_TITLE) {
      x = mm.x() - dragOffsetX;
      y = mm.y() - dragOffsetY;
      return true;
    }
    if (dragMode == DRAG_RESIZE) {
      float dw = mm.x() - dragOffsetX;
      float dh = mm.y() - dragOffsetY;
      width = Math.max(MIN_WIDTH, preDragW + dw);
      height = Math.max(MIN_HEIGHT, preDragH + dh);
      return true;
    }

    if (!inside) {
      return false;
    }
    super.handleEvent(mm, reachable);
    return true;
  }

  private boolean handleScroll(UiEvent.Scroll sc, boolean reachable) {
    Box2D abs = absoluteBounds();
    if (!abs.contains(sc.x(), sc.y())) {
      return false;
    }
    return super.handleEvent(sc, reachable);
  }

  /**
   * Toggles the window between minimized — collapsed to its title bar — and restored to its previous height.
   */
  public void minimize() {
    if (minimized) {
      height = savedMinH;
      minimized = false;
    } else {
      savedMinH = height;
      height = titleBarHeight() + borderWidth();
      minimized = true;
    }
  }

  /**
   * Toggles the window between maximized — filling the entire camera view — and its previous position and size.
   */
  public void maximize() {
    if (maximized) {
      x = savedMaxX;
      y = savedMaxY;
      width = savedMaxW;
      height = savedMaxH;
      maximized = false;
    } else {
      savedMaxX = x;
      savedMaxY = y;
      savedMaxW = width;
      savedMaxH = height;
      if (context != null) {
        x = 0;
        y = 0;
        width = context.camera().width();
        height = context.camera().height();
      }
      maximized = true;
    }
  }

  /**
   * Clamps the window position so it stays within the given logical viewport bounds. Called by {@link UiContext} after
   * a resize.
   *
   * @param logW the logical viewport width
   * @param logH the logical viewport height
   */
  public void clampToBounds(float logW, float logH) {
    x = Math.max(0, Math.min(x, logW - width));
    y = Math.max(0, Math.min(y, logH - titleBarHeight()));
  }
}
