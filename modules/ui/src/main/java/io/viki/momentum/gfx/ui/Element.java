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
package io.viki.momentum.gfx.ui;

import io.viki.momentum.gfx.quick2d.impl.Graphics;
import io.viki.momentum.math.Box2D;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A drawable logical UI region with an injected visual {@link Look}.
 *
 * <p>Elements are mutable and not thread-safe; keep a tree on its rendering thread.
 */
public abstract class Element {
  private Box2D bounds;
  private Look look;
  private final List<Element> children = new ArrayList<>();
  private final List<Element> parts = new ArrayList<>();
  private final List<Element> childrenView = Collections.unmodifiableList(children);
  private final List<Element> partsView = Collections.unmodifiableList(parts);
  private @Nullable Element parent;

  public Element(Box2D bounds, Look look) {
    this.bounds = bounds;
    this.look = look;
  }

  /** Returns the parent-local logical bounds of this element. */
  public Box2D bounds() {
    return bounds;
  }

  /** Returns this element's logical bounds after adding every parent-local position. */
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

  /** Draws this element, then content children, then subclass-owned internal parts. */
  public void draw(Graphics graphics) {
    Box2D absolute = absoluteBounds();
    drawAt(graphics, absolute.minX() - bounds.minX(), absolute.minY() - bounds.minY());
  }

  /** Draws only this element's own visual content. */
  protected void drawSelf(Graphics graphics, Box2D absoluteBounds) {
  }

  /** Draws the content and internal-part channels without allocating a merged list. */
  protected final void drawChildren(Graphics graphics) {
    Box2D absoluteBounds = absoluteBounds();
    drawChildren(graphics, absoluteBounds.minX(), absoluteBounds.minY());
  }

  /** Dispatches an event to parts, content children, and finally this element. */
  public boolean dispatch(UiEvent event) {
    if (event instanceof UiEvent.Positioned positioned) {
      if (dispatchPosition(positioned)) {
        return true;
      }
    } else if (dispatchNonPosition(event)) {
      return true;
    }
    return onEvent(event);
  }

  /** Handles an event not consumed by a descendant. */
  protected boolean onEvent(UiEvent event) {
    return false;
  }

  /** Adds an externally managed content child. An element can have only one parent. */
  public void addChild(Element child) {
    attach(child, children);
  }

  /** Removes a content child and detaches it from this element. */
  public boolean removeChild(Element child) {
    if (!children.remove(child)) {
      return false;
    }
    child.parent = null;
    return true;
  }

  /** Removes all content children while retaining subclass-owned internal parts. */
  public void clearChildren() {
    for (Element child : children) {
      child.parent = null;
    }
    children.clear();
  }

  /** Returns the live, read-only content-child view. */
  public List<Element> children() {
    return childrenView;
  }

  /** Adds a subclass-owned internal part to the reverse-hit-tested part channel. */
  protected final void addPart(Element part) {
    attach(part, parts);
  }

  /** Removes a subclass-owned internal part and detaches it from this element. */
  protected final boolean removePart(Element part) {
    if (!parts.remove(part)) {
      return false;
    }
    part.parent = null;
    return true;
  }

  /** Returns the live, read-only internal-part view for subclasses. */
  protected final List<Element> parts() {
    return partsView;
  }

  public boolean contains(float x, float y) {
    return absoluteBounds().contains(x, y);
  }

  /** Tests a position expressed relative to this element's local origin. */
  protected final boolean containsLocal(float x, float y) {
    return x >= 0.0F && y >= 0.0F && x <= bounds.width() && y <= bounds.height();
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
      throw new IllegalArgumentException("Element already has a parent");
    }
    child.parent = this;
    child.setLook(look);
    destination.add(child);
  }

  private boolean dispatchPosition(UiEvent.Positioned event) {
    List<Element> parts = this.parts;
    for (int i = parts.size() - 1; i >= 0; i--) {
      Element part = parts.get(i);
      if (part.dispatch(event.translated(-part.bounds.minX(), -part.bounds.minY()))) {
        return true;
      }
    }
    List<Element> children = this.children;
    for (int i = children.size() - 1; i >= 0; i--) {
      Element child = children.get(i);
      if (child.dispatch(event.translated(-child.bounds.minX(), -child.bounds.minY()))) {
        return true;
      }
    }
    return false;
  }

  private boolean dispatchNonPosition(UiEvent event) {
    for (int i = parts.size() - 1; i >= 0; i--) {
      if (parts.get(i).dispatch(event)) {
        return true;
      }
    }
    for (int i = children.size() - 1; i >= 0; i--) {
      if (children.get(i).dispatch(event)) {
        return true;
      }
    }
    return false;
  }

  private void drawAt(Graphics graphics, float parentX, float parentY) {
    Box2D absolute = Box2D.create(parentX + bounds.minX(), parentY + bounds.minY(), bounds.width(), bounds.height());
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
