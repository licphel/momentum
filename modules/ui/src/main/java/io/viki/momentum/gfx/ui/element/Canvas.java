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

import io.viki.momentum.gfx.math.TransformHandler;
import io.viki.momentum.gfx.ui.Resolution;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.gfx.ui.render.UiRenderDispatcher;
import io.viki.momentum.gfx.view.View;
import io.viki.momentum.input.InputSnapshot;
import io.viki.momentum.input.KeyAction;
import io.viki.momentum.input.KeyCode;
import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.math.Vector2;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Root UI surface containing the element tree and its single global keyboard focus.
 *
 * <p>Resolution mapping is supplied by {@link DpiContext}; input routing is owned by this
 * canvas. This class is mutable and not thread-safe.
 */
public final class Canvas extends Element implements AutoCloseable {
  private final DpiContext context;
  private final UiRenderDispatcher renderer;
  private final InputRouter inputRouter;
  private @Nullable Element focusedElement;
  private boolean closed;

  /**
   * Creates a canvas using the process-wide renderer dispatcher.
   *
   * <p>The canvas owns the logical context's input routing and must be closed when the view is
   * no longer used. It is mutable and is intended for access from the view thread.
   *
   * @param context logical coordinate and input mapping for this canvas
   */
  public Canvas(DpiContext context) {
    this(context, UiRenderDispatcher.INSTANCE);
  }

  /**
   * Creates a canvas with an explicitly selected renderer dispatcher.
   *
   * @param context logical coordinate and input mapping for this canvas
   * @param renderer dispatcher used to render the element tree
   */
  public Canvas(DpiContext context, UiRenderDispatcher renderer) {
    super(Rectangle.of(Vector2.ZERO, context.getLogicalSize()));
    this.context = context;
    this.renderer = renderer;
    inputRouter = new InputRouter(this, context);
  }

  /**
   * Opens a canvas mapped to a view using that view's current logical size policy.
   *
   * @param view view providing framebuffer size and input events
   * @param transformHandler handler used for input and graphics transforms
   * @return a newly opened canvas
   */
  public static Canvas open(View view, TransformHandler transformHandler) {
    return new Canvas(new DpiContext(view, transformHandler));
  }

  /**
   * Opens a canvas around an existing logical context.
   *
   * @param context logical coordinate and input mapping for the canvas
   * @return a newly opened canvas
   */
  public static Canvas open(DpiContext context) {
    return new Canvas(context);
  }

  /**
   * Opens a canvas around an existing context and renderer dispatcher.
   *
   * @param context logical coordinate and input mapping for the canvas
   * @param renderer dispatcher used to render the element tree
   * @return a newly opened canvas
   */
  public static Canvas open(DpiContext context, UiRenderDispatcher renderer) {
    return new Canvas(context, renderer);
  }

  /**
   * Opens a canvas with an explicit logical resolution policy.
   *
   * @param view view providing framebuffer size and input events
   * @param transformHandler handler used for input and graphics transforms
   * @param logicalWidth logical canvas width
   * @param logicalHeight logical canvas height
   * @param onlyIntegerScale whether scaling must use an integer factor
   * @return a newly opened canvas
   */
  public static Canvas open(View view, TransformHandler transformHandler, float logicalWidth,
                            float logicalHeight, boolean onlyIntegerScale) {
    return new Canvas(new DpiContext(view, logicalWidth, logicalHeight, onlyIntegerScale, transformHandler));
  }

  /**
   * Opens a canvas with an explicit resolution policy and renderer dispatcher.
   *
   * @param view view providing framebuffer size and input events
   * @param transformHandler handler used for input and graphics transforms
   * @param logicalWidth logical canvas width
   * @param logicalHeight logical canvas height
   * @param onlyIntegerScale whether scaling must use an integer factor
   * @param renderer dispatcher used to render the element tree
   * @return a newly opened canvas
   */
  public static Canvas open(View view, TransformHandler transformHandler, float logicalWidth,
                            float logicalHeight, boolean onlyIntegerScale,
                            UiRenderDispatcher renderer) {
    return new Canvas(new DpiContext(view, logicalWidth, logicalHeight, onlyIntegerScale, transformHandler), renderer);
  }
  /**
   * Returns the coordinate and input mapping owned by this canvas.
   *
   * <p>The context is updated when the canvas is resized and is closed together with this canvas.
   *
   * @return canvas coordinate context
   */
  public DpiContext context() {
    return context;
  }
  /**
   * Returns the current logical-to-framebuffer resolution mapping.
   *
   * <p>The returned mapping is the same object held by the canvas context and reflects the latest
   * framebuffer size.
   *
   * @return current resolution mapping
   */
  public Resolution resolution() {
    return context.resolution();
  }
  /**
   * Returns the most recent input snapshot used by the router.
   *
   * <p>The snapshot is suitable for constructing key bindings that are evaluated against future
   * dispatch calls.
   *
   * @return current input snapshot
   * @throws IllegalStateException if the canvas has been closed
   */
  public InputSnapshot inputSnapshot() {
    ensureOpen();
    return inputRouter.snapshot();
  }

  /**
   * Routes a pointer move from input coordinates into the element tree.
   *
   * @param inputX input-space horizontal coordinate
   * @param inputY input-space vertical coordinate
   * @throws IllegalStateException if the canvas has been closed
   */
  public void dispatchMouseMove(double inputX, double inputY) {
    inputRouter.dispatchMouseMove(inputX, inputY);
  }

  /**
   * Routes a mouse button event to the element under the pointer.
   *
   * @param button mouse button key code
   * @param action press or release action
   * @param inputX input-space horizontal coordinate
   * @param inputY input-space vertical coordinate
   * @param modifiers active modifier mask
   * @return {@code true} when an element consumed the event
   * @throws IllegalStateException if the canvas has been closed
   */
  public boolean dispatchMouseButton(KeyCode button, KeyAction action, double inputX,
                                     double inputY, int modifiers) {
    return inputRouter.dispatchMouseButton(button, action, inputX, inputY, modifiers);
  }

  /**
   * Routes a scroll-wheel event to the element under the pointer.
   *
   * @param deltaX horizontal scroll amount
   * @param deltaY vertical scroll amount
   * @param inputX input-space horizontal coordinate
   * @param inputY input-space vertical coordinate
   * @return {@code true} when an element consumed the event
   * @throws IllegalStateException if the canvas has been closed
   */
  public boolean dispatchScroll(double deltaX, double deltaY, double inputX, double inputY) {
    return inputRouter.dispatchScroll(deltaX, deltaY, inputX, inputY);
  }

  /**
   * Routes a key event to the focused element.
   *
   * @param key key code
   * @param action press, repeat, or release action
   * @param modifiers active modifier mask
   * @return {@code true} when the focused element consumed the event
   * @throws IllegalStateException if the canvas has been closed
   */
  public boolean dispatchKey(KeyCode key, KeyAction action, int modifiers) {
    return inputRouter.dispatchKey(key, action, modifiers);
  }

  /**
   * Routes a Unicode code point to the focused element.
   *
   * @param codepoint Unicode code point produced by text input
   * @return {@code true} when the focused element consumed the character
   * @throws IllegalStateException if the canvas has been closed
   */
  public boolean dispatchCharacter(int codepoint) {
    return inputRouter.dispatchCharacter(codepoint);
  }

  /**
   * Adds a top-level element to the canvas.
   *
   * @param element element to attach
   * @throws IllegalStateException if the canvas has been closed
   * @throws IllegalArgumentException if the element is already attached
   */
  public void add(Element element) {
    ensureOpen();
    addChild(element);
  }

  /**
   * Removes a top-level element from the canvas.
   *
   * @param element element to detach
   * @return {@code true} when the element was attached to this canvas
   * @throws IllegalStateException if the canvas has been closed
   */
  public boolean remove(Element element) {
    ensureOpen();
    boolean removed = removeChild(element);
    if (removed) {
      inputRouter.treeChanged();
      if (focusedElement != null
          && (!isAttached(focusedElement) || !isEffectivelyVisible(focusedElement))) {
        setFocusedElement(null);
      }
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

  /**
   * Removes all top-level elements and clears focus and pointer state.
   *
   * @throws IllegalStateException if the canvas has been closed
   */
  public void clear() {
    ensureOpen();
    inputRouter.clearAllInputState();
    setFocusedElement(null);
    clearChildren();
  }

  /**
   * Applies the current resolution and renders the complete canvas tree.
   *
   * @param graphics graphics context receiving the canvas
   * @throws IllegalStateException if the canvas has been closed
   */
  @Override
  public void draw(Graphics graphics) {
    ensureOpen();
    context.apply(graphics);
    renderer.render(graphics, this);
    inputRouter.drawTooltip(graphics);
  }
  /**
   * Returns the element that currently owns keyboard focus.
   *
   * <p>Detached or hidden elements are cleared before the value is returned.
   *
   * @return focused element, or {@code null} when focus is clear
   */
  public @Nullable Element focusedElement() {
    if (focusedElement != null
        && (!isAttached(focusedElement) || !isEffectivelyVisible(focusedElement))) {
      setFocusedElement(null);
    }
    return focusedElement;
  }

  /**
   * Assigns keyboard focus to an attached, visible focusable element.
   *
   * @param element element that should receive keyboard input
   * @throws IllegalArgumentException if the element is detached, hidden, or not focusable
   * @throws IllegalStateException if the canvas has been closed
   */
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

  /**
   * Clears keyboard focus from the canvas.
   *
   * @throws IllegalStateException if the canvas has been closed
   */
  public void clearFocus() {
    ensureOpen();
    setFocusedElement(null);
  }

  /**
   * Releases input and logical-context resources owned by this canvas.
   *
   * <p>Closing is idempotent. Subsequent mutating or dispatch operations fail with
   * {@link IllegalStateException}.
   */
  @Override
  public void close() {
    if (closed) {
      return;
    }
    inputRouter.close();
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
    inputRouter.setClipboardText(value);
  }

  String pasteFromClipboard() {
    return inputRouter.getClipboardText();
  }

  /**
   * Returns the clipboard text exposed to text-editing controls.
   *
   * @return current clipboard text
   * @throws IllegalStateException if the canvas has been closed
   */
  public String getClipboardText() {
    ensureOpen();
    return inputRouter.getClipboardText();
  }

  /**
   * Replaces the clipboard text exposed to text-editing controls.
   *
   * @param value new clipboard text
   * @throws IllegalStateException if the canvas has been closed
   */
  public void setClipboardText(String value) {
    ensureOpen();
    inputRouter.setClipboardText(value);
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
