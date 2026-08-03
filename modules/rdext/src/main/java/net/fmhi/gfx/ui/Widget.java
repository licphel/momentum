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

package net.fmhi.gfx.ui;

import net.fmhi.gfx.brush.Graphics2D;
import net.fmhi.math.Box2D;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Abstract base for all UI widgets.
 *
 * <p>Widgets form a parent–child tree. Each widget stores its position and size in
 * <em>parent-local coordinates</em> relative to the parent's {@link #contentBounds()} origin.
 * Root widgets treat the camera's top-left as their parent origin.
 *
 * <h3>Per-frame cycle</h3>
 * <ol>
 *   <li>{@link #layout(Box2D)} — resolves {@link AnchorLayout} positioning, recurses into
 *       children.</li>
 *   <li>{@link #update(float)} — per-frame logic tick.</li>
 *   <li>{@link #render(Graphics2D, Look, Box2D)} — draws self then children; containers apply
 *       scissor clipping.</li>
 * </ol>
 *
 * <h3>Input</h3>
 * <p>Events flow through {@link #handleEvent(UiEvent)} which dispatches to children in reverse
 * z-order — topmost widget first. A widget that fully consumes an event returns {@code true} to
 * stop propagation.
 *
 * <p>This class is not thread-safe. All methods must be called from the rendering thread.
 */
public abstract class Widget {
  /** Children of this widget, sorted by z-order. */
  protected final List<Widget> children = new ArrayList<>();
  /** X position in parent-local coordinates. */
  protected float x;
  /** Y position in parent-local coordinates. */
  protected float y;
  /** Width in logical units. */
  protected float width;
  /** Height in logical units. */
  protected float height;
  /** Whether this widget and its descendants are drawn and receive events. */
  protected boolean visible = true;
  /** Whether this widget responds to input events. */
  protected boolean enabled = true;
  /** Rendering order within siblings. Higher values are drawn on top and receive events first. */
  protected int zOrder = 0;
  /** The parent widget, or {@code null} if this is a root widget. */
  protected @Nullable Widget parent;
  /**
   * The anchor layout that controls positioning during {@link #layout(Box2D)}. When {@code null}, the widget uses its
   * raw position and size directly.
   */
  private @Nullable AnchorLayout anchorLayout = null;

  /**
   * Returns this widget's bounds in parent-local coordinates.
   *
   * <p>The origin {@code (0, 0)} is the parent's {@link #contentBounds()} top-left corner.
   *
   * @return the bounds rectangle in parent-local coordinates
   */
  public Box2D bounds() {
    return Box2D.create(x, y, width, height);
  }

  /**
   * Returns the content area in parent-local coordinates.
   *
   * <p>Plain widgets return the same rectangle as {@link #bounds()}. Containers with insets
   * — title bars, padding, borders — override this to return only the drawable interior. Children are positioned
   * relative to this area's top-left corner.
   *
   * @return the content area in parent-local coordinates
   */
  public Box2D contentBounds() {
    return bounds();
  }

  /**
   * Computes this widget's bounds in absolute root-level logical coordinates by walking up the parent chain and
   * accumulating each ancestor's content-area origin.
   *
   * @return the bounds rectangle in absolute logical coordinates
   */
  public Box2D absoluteBounds() {
    float ax = x;
    float ay = y;
    Widget p = parent;
    while (p != null) {
      Box2D cb = p.contentBounds();
      ax += cb.minX();
      ay += cb.minY();
      p = p.parent;
    }
    return Box2D.create(ax, ay, width, height);
  }

  /**
   * Returns the content area in absolute logical coordinates.
   *
   * @return the content rectangle in absolute logical coordinates
   */
  public Box2D absoluteContentBounds() {
    Box2D abs = absoluteBounds();
    Box2D local = contentBounds();
    float offX = local.minX() - x;
    float offY = local.minY() - y;
    return Box2D.create(abs.minX() + offX, abs.minY() + offY, local.width(), local.height());
  }

  /**
   * Adds a child widget and re-sorts the children list by z-order.
   *
   * <p>The child must not already have a parent.
   *
   * @param child the widget to add
   */
  public void addChild(Widget child) {
    child.parent = this;
    children.add(child);
    children.sort(Comparator.comparingInt(w -> w.zOrder));
  }

  /**
   * Removes a child widget, clearing its parent reference.
   *
   * @param child the widget to remove
   */
  public void removeChild(Widget child) {
    if (children.remove(child)) {
      child.parent = null;
    }
  }

  /**
   * Returns the list of child widgets.
   *
   * @return an unmodifiable view of the children
   */
  public List<Widget> children() {
    return children;
  }

  /**
   * Sets the anchor layout used during {@link #layout(Box2D)}.
   *
   * @param layout the anchor layout, or {@code null} to use raw positioning
   */
  public void setAnchorLayout(@Nullable AnchorLayout layout) {
    this.anchorLayout = layout;
  }

  /**
   * Returns the current anchor layout.
   *
   * @return the anchor layout, or {@code null} if none is set
   */
  public @Nullable AnchorLayout anchorLayout() {
    return anchorLayout;
  }

  /**
   * Resolves this widget's position and size from its anchor layout relative to the given parent content bounds, then
   * recurses into children.
   *
   * <p>If no anchor layout is set, the raw position and size are left unchanged.
   *
   * @param parentContent the parent's content area in absolute coordinates
   */
  public void layout(Box2D parentContent) {
    if (anchorLayout != null) {
      float pw = parentContent.width();
      float ph = parentContent.height();
      // Resolve each edge: anchor * parentSize + offset, relative to parentContent origin
      float absMinX = parentContent.minX() + anchorLayout.minAnchorX() * pw + anchorLayout.offsetMinX();
      float absMinY = parentContent.minY() + anchorLayout.minAnchorY() * ph + anchorLayout.offsetMinY();
      float absMaxX = parentContent.minX() + anchorLayout.maxAnchorX() * pw + anchorLayout.offsetMaxX();
      float absMaxY = parentContent.minY() + anchorLayout.maxAnchorY() * ph + anchorLayout.offsetMaxY();
      // Store as parent-local coords (subtract parent's own content origin for non-root widgets)
      // absoluteBounds walks the parent chain, so we store values that when walked give absMin/Max.
      // For root widgets (parent == null) parent-local == absolute, so direct assignment is correct.
      // For nested widgets the parent's contentBounds origin is already subtracted by layout caller,
      // because parentContent IS the absolute content origin — store relative to it.
      x = absMinX - parentContent.minX();
      y = absMinY - parentContent.minY();
      width = Math.max(0, absMaxX - absMinX);
      height = Math.max(0, absMaxY - absMinY);
    }
    Box2D myContent = absoluteContentBounds();
    for (Widget child : children) {
      child.layout(myContent);
    }
  }

  /**
   * Per-frame update tick.
   *
   * <p>Override to implement animations or polling logic. The default implementation skips
   * invisible widgets and recurses into visible children.
   *
   * @param deltaTime seconds elapsed since the previous frame
   */
  public void update(float deltaTime) {
    if (!visible) {
      return;
    }
    for (Widget child : children) {
      child.update(deltaTime);
    }
  }

  /**
   * Renders this widget and its children.
   *
   * <p>The default implementation calls {@link #renderSelf(Graphics2D, Look)} then draws children in
   * z-order. Containers that need scissor clipping override this method.
   *
   * @param g          the drawing context
   * @param look       the active look implementation
   * @param parentClip the effective scissor rectangle in absolute logical coordinates, or {@code null} if no clipping
   *                   is active
   */
  public void render(Graphics2D g, Look look, @Nullable Box2D parentClip) {
    if (!visible) {
      return;
    }
    renderSelf(g, look);
    for (Widget child : children) {
      child.render(g, look, parentClip);
    }
  }

  /**
   * Draws only this widget's own visual content, without children.
   *
   * @param g    the drawing context
   * @param look the active look implementation
   */
  protected abstract void renderSelf(Graphics2D g, Look look);

  /**
   * Handles an input event. Children are tried in reverse z-order — topmost first — before this widget processes the
   * event itself.
   *
   * @param event     the input event in logical UI coordinates
   * @param reachable {@code false} when this widget is covered by another widget above it in z-order; the event still
   *                  arrives so drag and release sequences can complete, but the widget must not trigger interactive
   *                  state changes such as hover or press transitions
   * @return {@code true} if the event was consumed and should not propagate further
   */
  public boolean handleEvent(UiEvent event, boolean reachable) {
    if (!visible || !enabled) {
      return false;
    }
    for (int i = children.size() - 1; i >= 0; i--) {
      if (children.get(i).handleEvent(event, reachable)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Handles an input event, assuming this widget is reachable.
   *
   * @param event the input event
   * @return {@code true} if the event was consumed
   */
  public boolean handleEvent(UiEvent event) {
    return handleEvent(event, true);
  }

  /**
   * Returns the X position in parent-local coordinates.
   *
   * @return the X position
   */
  public float x() {
    return x;
  }

  /**
   * Returns the Y position in parent-local coordinates.
   *
   * @return the Y position
   */
  public float y() {
    return y;
  }

  /**
   * Returns the width in logical units.
   *
   * @return the width
   */
  public float width() {
    return width;
  }

  /**
   * Returns the height in logical units.
   *
   * @return the height
   */
  public float height() {
    return height;
  }

  /**
   * Returns whether this widget is visible.
   *
   * @return {@code true} if visible
   */
  public boolean isVisible() {
    return visible;
  }

  /**
   * Sets the visibility of this widget. Invisible widgets are skipped during rendering and event dispatch.
   *
   * @param visible {@code true} to make the widget visible
   */
  public void setVisible(boolean visible) {
    this.visible = visible;
  }

  /**
   * Returns whether this widget responds to input events.
   *
   * @return {@code true} if enabled
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Sets whether this widget responds to input events.
   *
   * @param enabled {@code true} to enable input handling
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Returns the z-order of this widget. Higher values are drawn on top and receive input events first.
   *
   * @return the z-order value
   */
  public int zOrder() {
    return zOrder;
  }

  /**
   * Returns the parent widget.
   *
   * @return the parent widget, or {@code null} if this is a root widget
   */
  public @Nullable Widget parent() {
    return parent;
  }

  /**
   * Sets the position in parent-local coordinates.
   *
   * @param x the X position
   * @param y the Y position
   */
  public void setPosition(float x, float y) {
    this.x = x;
    this.y = y;
  }

  /**
   * Sets the size in logical units.
   *
   * @param width  the width
   * @param height the height
   */
  public void setSize(float width, float height) {
    this.width = width;
    this.height = height;
  }

  /**
   * Sets the z-order and re-sorts the parent's children list. Higher values are drawn on top and receive input events
   * first.
   *
   * @param zOrder the new z-order value
   */
  public void setZOrder(int zOrder) {
    this.zOrder = zOrder;
    if (parent != null) {
      parent.children.sort(Comparator.comparingInt(w -> w.zOrder));
    }
  }
}
