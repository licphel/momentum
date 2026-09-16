/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 */
package io.viki.momentum.gfx.ui;

import io.viki.momentum.gfx.math.TransformHandler;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.gfx.view.View;
import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.math.Vector2;
import org.jspecify.annotations.Nullable;

/**
 * Root UI surface containing the element tree and its single global keyboard focus.
 *
 * <p>Input and resolution handling are composed through {@link PrimaryContext}; elements never
 * implement or proxy that context. This class is mutable and not thread-safe.
 */
public final class Canvas extends Element implements AutoCloseable {
  private final PrimaryContext context;
  private @Nullable Element focusedElement;
  private boolean closed;

  public Canvas(PrimaryContext context, Look look) {
    super(Rectangle.of(Vector2.ZERO, context.getLogicalSize()), look);
    this.context = context;
    context.bind(this);
  }

  public static Canvas open(View view, TransformHandler transformHandler, Look look) {
    return new Canvas(new PrimaryContext(view, transformHandler), look);
  }

  public static Canvas open(View view, TransformHandler transformHandler, Look look,
                            float logicalWidth, float logicalHeight, boolean onlyIntegerScale) {
    return new Canvas(new PrimaryContext(view, logicalWidth, logicalHeight, onlyIntegerScale,
        transformHandler), look);
  }

  public static Canvas open(PrimaryContext context, Look look) {
    return new Canvas(context, look);
  }

  public PrimaryContext context() {
    return context;
  }

  public Resolution resolution() {
    return context.resolution();
  }

  public void add(Element element) {
    ensureOpen();
    addChild(element);
  }

  public boolean remove(Element element) {
    ensureOpen();
    boolean removed = removeChild(element);
    if (removed) {
      context.treeChanged();
      validFocusedElement();
    }
    return removed;
  }

  public void clear() {
    ensureOpen();
    context.clearAllInputState();
    setFocusedElement(null);
    clearChildren();
  }

  @Override
  public void draw(Graphics graphics) {
    ensureOpen();
    context.apply(graphics);
    super.draw(graphics);
  }

  public void requestFocus(Element element) {
    ensureOpen();
    if (!isAttached(element)) {
      throw new IllegalArgumentException("Cannot focus an element outside this Canvas");
    }
    if (!element.acceptsFocus()) {
      throw new IllegalArgumentException("Element does not accept keyboard focus: "
          + element.getClass().getName());
    }
    setFocusedElement(element);
  }

  public void clearFocus() {
    ensureOpen();
    setFocusedElement(null);
  }

  public @Nullable Element focusedElement() {
    return validFocusedElement();
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    context.close();
    setFocusedElement(null);
    closed = true;
  }

  void focusNearest(@Nullable Element target) {
    Element candidate = target;
    while (candidate != null && !candidate.acceptsFocus()) {
      candidate = candidate.parent();
    }
    setFocusedElement(candidate == this ? null : candidate);
  }

  void clearFocusFromContext() {
    setFocusedElement(null);
  }

  boolean isAttached(Element element) {
    @Nullable Element current = element;
    while (current != null) {
      if (current == this) {
        return true;
      }
      current = current.parent();
    }
    return false;
  }

  private void setFocusedElement(@Nullable Element target) {
    if (focusedElement == target) {
      return;
    }
    if (focusedElement != null) {
      focusedElement.onFocusChanged(false);
    }
    focusedElement = target;
    if (target != null) {
      target.onFocusChanged(true);
    }
  }

  private @Nullable Element validFocusedElement() {
    if (focusedElement != null && !isAttached(focusedElement)) {
      setFocusedElement(null);
    }
    return focusedElement;
  }

  private void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("Canvas is closed");
    }
  }
}
