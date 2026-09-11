/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 */
package io.viki.momentum.gfx.ui;

import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.Box2D;
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
  private Box2D bounds;
  private Look look;
  private final List<Element> children = new ArrayList<>();
  private final List<Element> parts = new ArrayList<>();
  private final List<Element> childrenView = Collections.unmodifiableList(children);
  private final List<Element> partsView = Collections.unmodifiableList(parts);
  private @Nullable Element parent;

  protected Element(Box2D bounds, Look look) {
    this.bounds = bounds;
    this.look = look;
  }

  public Box2D bounds() {
    return bounds;
  }

  public Box2D absoluteBounds() {
    float minX = bounds.minX();
    float minY = bounds.minY();
    @Nullable Element ancestor = parent;
    while (ancestor != null) {
      minX += ancestor.bounds.minX();
      minY += ancestor.bounds.minY();
      ancestor = ancestor.parent;
    }
    return Box2D.create(minX, minY, bounds.width(), bounds.height());
  }

  public void setBounds(Box2D value) {
    bounds = value;
  }

  public Look look() {
    return look;
  }

  public void setLook(Look value) {
    look = value;
    for (Element child : children) {
      child.setLook(value);
    }
    for (Element part : parts) {
      part.setLook(value);
    }
  }

  public void draw(Graphics graphics) {
    Box2D absolute = absoluteBounds();
    drawAt(graphics, absolute.minX() - bounds.minX(), absolute.minY() - bounds.minY());
  }

  protected void drawSelf(Graphics graphics, Box2D absoluteBounds) {
  }

  protected final void drawChildren(Graphics graphics) {
    Box2D absolute = absoluteBounds();
    drawChildren(graphics, absolute.minX(), absolute.minY());
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

  protected final List<Element> parts() {
    return partsView;
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
    child.setLook(look);
    destination.add(child);
  }

  private void drawAt(Graphics graphics, float parentX, float parentY) {
    Box2D absolute = Box2D.create(parentX + bounds.minX(), parentY + bounds.minY(),
        bounds.width(), bounds.height());
    drawSelf(graphics, absolute);
    drawChildren(graphics, absolute.minX(), absolute.minY());
  }

  private void drawChildren(Graphics graphics, float absoluteX, float absoluteY) {
    for (Element child : children) {
      child.drawAt(graphics, absoluteX, absoluteY);
    }
    for (Element part : parts) {
      part.drawAt(graphics, absoluteX, absoluteY);
    }
  }
}
