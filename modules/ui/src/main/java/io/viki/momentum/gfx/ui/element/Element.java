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

import io.viki.momentum.gfx.text.Literal;
import io.viki.momentum.gfx.text.Text;
import io.viki.momentum.gfx.ui.InputListener;
import io.viki.momentum.gfx.ui.render.ElementRenderer;
import io.viki.momentum.gfx.ui.render.EmptyRenderer;
import io.viki.momentum.gfx.ui.render.UiRenderDispatcher;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A drawable logical UI region that handles only its own direct input callbacks.
 *
 * <p>Elements form a parent-local coordinate tree. They are mutable and not thread-safe;
 * drawing, tree mutation, and input callbacks must run on the owning view thread.
 */
public abstract class Element implements InputListener {
  private static final long DEFAULT_TOOLTIP_DELAY_MILLIS = 500L;

  private Rectangle bounds;
  private final List<Element> children = new ArrayList<>();
  private final List<Element> parts = new ArrayList<>();
  private final List<Element> childrenView = Collections.unmodifiableList(children);
  private final List<Element> partsView = Collections.unmodifiableList(parts);
  private @Nullable Element parent;
  private @Nullable ElementRenderer rendererOverride;
  private final List<Text> defaultTooltip = new ArrayList<>();
  private final List<Text> defaultTooltipView = Collections.unmodifiableList(defaultTooltip);
  private long tooltipDelayMillis = DEFAULT_TOOLTIP_DELAY_MILLIS;
  private boolean visible = true;

  /**
   * Creates an element with bounds expressed in its parent's local coordinate system.
   *
   * <p>Subclasses normally expose a more specific constructor and provide their default
   * renderer through {@link #defaultRenderer()}. The element tree and all mutable state are
   * expected to be accessed from the owning UI thread.
   *
   * @param bounds initial local bounds of the element
   */
  protected Element(Rectangle bounds) {
    this.bounds = bounds;
  }

  /**
   * Returns this element's local bounds.
   *
   * @return current bounds relative to the parent
   */
  public Rectangle bounds() {
    return bounds;
  }

  /**
   * Computes this element's bounds in canvas coordinates by walking its parent chain.
   *
   * @return absolute bounds suitable for hit testing and overlay placement
   */
  public Rectangle absoluteBounds() {
    float minX = bounds.minX();
    float minY = bounds.minY();
    @Nullable Element ancestor = parent;
    while (ancestor != null) {
      minX += ancestor.bounds.minX();
      minY += ancestor.bounds.minY();
      ancestor = ancestor.parent;
    }
    return Rectangle.of(minX, minY, bounds.width(), bounds.height());
  }

  /**
   * Replaces the element's local bounds.
   *
   * @param value new local bounds
   */
  public void setBounds(Rectangle value) {
    bounds = value;
  }

  /**
   * Renders this element and its descendants through the global UI dispatcher.
   *
   * @param graphics graphics context receiving the element tree
   */
  public void draw(Graphics graphics) {
    UiRenderDispatcher.INSTANCE.render(graphics, this);
  }

  /**
   * Returns the rendering strategy used by {@link UiRenderDispatcher}.
   *
   * <p>An explicitly installed override takes precedence over the subclass-provided default.
   *
   * @return the renderer currently responsible for this element
   */
  public ElementRenderer renderer() {
    @Nullable ElementRenderer override = rendererOverride;
    return override == null ? defaultRenderer() : override;
  }

  /**
   * Replaces this element's built-in renderer with a caller-owned renderer.
   *
   * @param value renderer to use until {@link #clearRendererOverride()} is called
   */
  public final void setRenderer(ElementRenderer value) {
    rendererOverride = value;
  }

  /**
   * Restores the renderer supplied by the element implementation.
   *
   * <p>After this call, {@link #renderer()} delegates to {@link #defaultRenderer()} again.
   */
  public final void clearRendererOverride() {
    rendererOverride = null;
  }

  /**
   * Returns whether this element must be rendered atomically with its parts.
   *
   * <p>Layer boundaries let the dispatcher preserve ordering for overlays such as windows and
   * popups while still traversing ordinary child elements normally.
   *
   * @return {@code true} when this element establishes an independent render layer
   */
  public boolean isRenderLayerBoundary() {
    return false;
  }

  /**
   * Supplies the renderer used when no custom renderer override is installed.
   *
   * <p>Subclasses should return a shared stateless renderer whenever their presentation does not
   * require per-element caching.
   *
   * @return default renderer for this element
   */
  protected ElementRenderer defaultRenderer() {
    return EmptyRenderer.INSTANCE;
  }

  /**
   * Supplies an optional clip rectangle for descendant rendering.
   *
   * @param absoluteBounds this element's absolute bounds
   * @return descendant clip in absolute coordinates, or {@code null} for no additional clip
   */
  protected @Nullable Rectangle childrenClip(Rectangle absoluteBounds) {
    return null;
  }

  /**
   * Appends a child to this element's paint and hit-test order.
   *
   * @param child element to attach
   * @throws IllegalArgumentException if the child is already attached or would create a cycle
   */
  public void addChild(Element child) {
    attach(child, children);
  }

  /**
   * Detaches a direct child from this element.
   *
   * @param child child to remove
   * @return {@code true} when the child was attached to this element
   */
  public boolean removeChild(Element child) {
    if (!children.remove(child)) {
      return false;
    }
    child.parent = null;
    return true;
  }

  /**
   * Detaches every direct child and leaves this element with an empty child list.
   */
  public void clearChildren() {
    for (Element child : children) {
      child.parent = null;
    }
    children.clear();
  }

  /**
   * Returns an unmodifiable live view of this element's direct children.
   *
   * @return children in paint and hit-test order
   */
  public List<Element> children() {
    return childrenView;
  }

  /** Moves a direct child to the end of this container's draw and hit-test order. */
  final boolean bringChildToFront(Element child) {
    if (!children.remove(child)) {
      return false;
    }
    children.add(child);
    return true;
  }

  /**
   * Attaches a visual part rendered after ordinary children.
   *
   * <p>Parts participate in the same parent tree but are kept in a separate render pass for
   * overlays such as popups and scrollbars.
   *
   * @param part visual element to attach
   * @throws IllegalArgumentException if the part is already attached or would create a cycle
   */
  protected final void addPart(Element part) {
    attach(part, parts);
  }

  /**
   * Returns an unmodifiable live view of visual parts rendered above normal children.
   *
   * @return attached parts in their render order
   */
  public final List<Element> parts() {
    return partsView;
  }

  /**
   * Exposes the element's clipping policy to the renderer dispatcher.
   *
   * @param absoluteBounds this element's absolute bounds
   * @return descendant clip, or {@code null} when descendants are not clipped
   */
  public final @Nullable Rectangle childrenClipForRender(Rectangle absoluteBounds) {
    return childrenClip(absoluteBounds);
  }

  /**
   * Called after this element has been attached to a parent.
   *
   * <p>Subclasses can use this hook to constrain geometry or initialize state that depends on
   * the parent. The default implementation does nothing.
   */
  protected void onAttached() {
  }

  /**
   * Tests whether a canvas-coordinate point lies inside this element.
   *
   * @param x horizontal canvas coordinate
   * @param y vertical canvas coordinate
   * @return {@code true} when the point lies inside the absolute bounds
   */
  public boolean contains(float x, float y) {
    return absoluteBounds().contains(x, y);
  }

  /**
   * Tests a point expressed in this element's local coordinates.
   *
   * @param x local horizontal coordinate
   * @param y local vertical coordinate
   * @return whether the point lies inside the local bounds
   */
  protected final boolean containsLocal(float x, float y) {
    return x >= 0.0F && y >= 0.0F && x <= bounds.width() && y <= bounds.height();
  }

  /**
   * Returns this element's direct parent, if it is attached.
   *
   * @return parent element, or {@code null} for a detached/root element
   */
  public final @Nullable Element parent() {
    return parent;
  }

  /**
   * Finds the canvas at the root of this element's attachment chain.
   *
   * @return owning canvas, or {@code null} while detached
   */
  protected final @Nullable Canvas owningCanvas() {
    @Nullable Element current = this;
    while (current != null) {
      if (current instanceof Canvas canvas) {
        return canvas;
      }
      current = current.parent;
    }
    return null;
  }

  /**
   * Returns the default rich tooltip entries configured for this element.
   *
   * <p>The returned list is an unmodifiable live view. Subclasses can add contextual entries by
   * overriding {@link #appendTooltips(List)} rather than mutating this view.
   *
   * @return default tooltip entries in display order
   */
  public final List<Text> defaultTooltip() {
    return defaultTooltipView;
  }

  /**
   * Replaces the default rich tooltip entries.
   *
   * @param value tooltip entries to display by default, in display order
   */
  public final void setTooltip(List<Text> value) {
    defaultTooltip.clear();
    defaultTooltip.addAll(value);
  }

  /**
   * Replaces the default rich tooltip entries.
   *
   * @param value tooltip entries to display by default, in display order
   */
  public final void setDefaultTooltip(List<Text> value) {
    setTooltip(value);
  }

  /**
   * Sets or clears a legacy single-string tooltip.
   *
   * <p>The string is converted to one {@link Text} entry and is therefore also available through
   * {@link #defaultTooltip()} and {@link #appendTooltips(List)}.
   *
   * @param value tooltip text; {@code null} or an empty string clears the default entries
   */
  public final void setDefaultTooltip(@Nullable String value) {
    defaultTooltip.clear();
    if (value != null) {
      defaultTooltip.add(Literal.of(value));
    }
  }

  /**
   * Appends this element's default tooltip entries to a caller-owned list.
   *
   * <p>Subclasses may override this method to add dynamic context such as the current value,
   * validation state, or keyboard hints. An override that wants the configured defaults should
   * call {@code super.appendTooltips(tooltip)} before adding its own entries.
   *
   * @param tooltip destination list receiving entries in display order
   */
  public void appendTooltips(List<Text> tooltip) {
    tooltip.addAll(defaultTooltip);
  }

  /**
   * Returns whether this element currently contributes at least one tooltip entry.
   *
   * @return {@code true} when {@link #appendTooltips(List)} produces visible content
   */
  final boolean hasTooltipForRender() {
    List<Text> entries = new ArrayList<>();
    appendTooltips(entries);
    return !entries.isEmpty();
  }

  /**
   * Returns the effective delay before tooltip content is rendered.
   *
   * @return delay in milliseconds
   */
  public final long tooltipDelayMillis() {
    return tooltipDelayMillis;
  }

  /**
   * Returns whether this element participates in rendering and hit testing.
   *
   * @return {@code true} when visible
   */
  public final boolean visible() {
    return visible;
  }

  /**
   * Changes this element's visibility.
   *
   * @param value {@code true} to render and hit-test the element
   */
  public final void setVisible(boolean value) {
    visible = value;
  }

  /**
   * Sets a per-element tooltip delay.
   *
   * @param value non-negative delay in milliseconds
   * @throws IllegalArgumentException if {@code value} is negative
   */
  public final void setTooltipDelayMillis(long value) {
    if (value < 0L) {
      throw new IllegalArgumentException("Tooltip delay must not be negative: " + value);
    }
    tooltipDelayMillis = value;
  }

  private void attach(Element child, List<Element> destination) {
    if (child == this) {
      throw new IllegalArgumentException("An element cannot contain itself");
    }
    @Nullable Element ancestor = this;
    while (ancestor != null) {
      if (ancestor == child) {
        throw new IllegalArgumentException("An element cannot contain one of its ancestors");
      }
      ancestor = ancestor.parent;
    }
    if (child.parent != null) {
      throw new IllegalArgumentException("Element already has a parent: " + child.getClass().getName());
    }
    child.parent = this;
    destination.add(child);
    child.onAttached();
  }
}
