/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 */
package io.viki.momentum.gfx.ui;

import io.viki.momentum.gfx.math.TransformHandler;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.gfx.ui.render.ElementRenderer;
import io.viki.momentum.gfx.ui.render.EmptyRenderer;
import io.viki.momentum.gfx.view.View;
import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.math.Vector2;
import org.jspecify.annotations.Nullable;

import java.util.List;

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

  public Canvas(PrimaryContext context) {
    super(Rectangle.of(Vector2.ZERO, context.getLogicalSize()));
    this.context = context;
    context.bind(this);
  }

  public static Canvas open(View view, TransformHandler transformHandler) {
    return new Canvas(new PrimaryContext(view, transformHandler));
  }

  public static Canvas open(PrimaryContext context) {
    return new Canvas(context);
  }

  public static Canvas open(View view, TransformHandler transformHandler, float logicalWidth,
                            float logicalHeight, boolean onlyIntegerScale) {
    return new Canvas(new PrimaryContext(view, logicalWidth, logicalHeight, onlyIntegerScale,
        transformHandler));
  }

  public PrimaryContext context() {
    return context;
  }

  @Override
  protected ElementRenderer defaultRenderer() {
    return EmptyRenderer.INSTANCE;
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

  /** Brings the direct window under a logical pointer position above its sibling windows. */
  void bringWindowToFrontAt(float x, float y) {
    ensureOpen();
    List<Element> elements = children();
    for (int i = elements.size() - 1; i >= 0; i--) {
      Element element = elements.get(i);
      if (element instanceof Window window && window.visible()
          && window.absoluteBounds().contains(x, y)) {
        bringChildToFront(window);
        return;
      }
    }
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
    context.drawTooltip(graphics);
  }

  public void requestFocus(Element element) {
    ensureOpen();
    if (!isAttached(element)) {
      throw new IllegalArgumentException("Cannot focus an element outside this Canvas");
    }
    if (!isEffectivelyVisible(element)) {
      throw new IllegalArgumentException("Cannot focus a hidden element");
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

  void copyToClipboard(String value) {
    context.setClipboardText(value);
  }

  String pasteFromClipboard() {
    return context.getClipboardText();
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
    if (focusedElement != null
        && (!isAttached(focusedElement) || !isEffectivelyVisible(focusedElement))) {
      setFocusedElement(null);
    }
    return focusedElement;
  }

  private static boolean isEffectivelyVisible(Element element) {
    @Nullable Element current = element;
    while (current != null) {
      if (!current.visible()) {
        return false;
      }
      current = current.parent();
    }
    return true;
  }

  private void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("Canvas is closed");
    }
  }
}
