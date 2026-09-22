/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 */
package io.viki.momentum.gfx.ui;

import io.viki.momentum.gfx.ui.render.ElementRenderer;
import io.viki.momentum.gfx.ui.render.EmptyRenderer;
import io.viki.momentum.gfx.ui.render.UIRenderDispatcher;
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
  private @Nullable String tooltip;
  private @Nullable Long tooltipDelayMillis;
  private boolean visible = true;

  protected Element(Rectangle bounds) {
    this.bounds = bounds;
  }

  public Rectangle bounds() {
    return bounds;
  }

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

  public void setBounds(Rectangle value) {
    bounds = value;
  }

  public void draw(Graphics graphics) {
    UIRenderDispatcher.INSTANCE.render(graphics, this);
  }

  /** Returns the rendering strategy used by {@link UIRenderDispatcher}. */
  public ElementRenderer renderer() {
    @Nullable ElementRenderer override = rendererOverride;
    return override == null ? defaultRenderer() : override;
  }

  /** Replaces this element's built-in renderer with a caller-owned renderer. */
  public final void setRenderer(ElementRenderer value) {
    rendererOverride = java.util.Objects.requireNonNull(value, "value");
  }

  /** Restores the renderer supplied by the element implementation. */
  public final void clearRendererOverride() {
    rendererOverride = null;
  }

  /** Returns whether this element must be rendered atomically with its parts. */
  public boolean isRenderLayerBoundary() {
    return false;
  }

  /** Returns this element's built-in renderer when no override is installed. */
  protected ElementRenderer defaultRenderer() {
    return EmptyRenderer.INSTANCE;
  }

  protected @Nullable Rectangle childrenClip(Rectangle absoluteBounds) {
    return null;
  }

  public void addChild(Element child) {
    attach(child, children);
  }

  public boolean removeChild(Element child) {
    if (!children.remove(child)) {
      return false;
    }
    child.parent = null;
    return true;
  }

  public void clearChildren() {
    for (Element child : children) {
      child.parent = null;
    }
    children.clear();
  }

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

  protected final void addPart(Element part) {
    attach(part, parts);
  }

  protected final boolean removePart(Element part) {
    if (!parts.remove(part)) {
      return false;
    }
    part.parent = null;
    return true;
  }

  public final List<Element> parts() {
    return partsView;
  }

  /** Exposes the element's clipping policy to the renderer dispatcher. */
  public final @Nullable Rectangle childrenClipForRender(Rectangle absoluteBounds) {
    return childrenClip(absoluteBounds);
  }

  public boolean contains(float x, float y) {
    return absoluteBounds().contains(x, y);
  }

  protected final boolean containsLocal(float x, float y) {
    return x >= 0.0F && y >= 0.0F && x <= bounds.width() && y <= bounds.height();
  }

  public final @Nullable Element parent() {
    return parent;
  }

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

  public final @Nullable String tooltip() {
    return tooltip;
  }

  public final void setTooltip(@Nullable String value) {
    tooltip = value == null || value.isEmpty() ? null : value;
  }

  public final long tooltipDelayMillis() {
    if (tooltipDelayMillis != null) {
      return tooltipDelayMillis;
    }
    return DEFAULT_TOOLTIP_DELAY_MILLIS;
  }

  public final boolean visible() {
    return visible;
  }

  public final void setVisible(boolean value) {
    visible = value;
  }

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
      throw new IllegalArgumentException("Element already has a parent: "
          + child.getClass().getName());
    }
    child.parent = this;
    destination.add(child);
  }

}
