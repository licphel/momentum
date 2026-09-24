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

import io.viki.momentum.gfx.ui.render.TooltipRenderer;
import io.viki.momentum.gfx.text.Text;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.gfx.view.DesktopView;
import io.viki.momentum.gfx.view.View;
import io.viki.momentum.input.InputSnapshot;
import io.viki.momentum.input.KeyAction;
import io.viki.momentum.input.KeyCode;
import io.viki.momentum.input.event.CharEvent;
import io.viki.momentum.input.event.CursorEnterEvent;
import io.viki.momentum.input.event.FocusEvent;
import io.viki.momentum.input.event.KeyEvent;
import io.viki.momentum.input.event.MouseButtonEvent;
import io.viki.momentum.input.event.MouseMoveEvent;
import io.viki.momentum.input.event.ResizeEvent;
import io.viki.momentum.input.event.ScrollEvent;
import io.viki.momentum.math.Vector2;
import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.util.InternalApi;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;

/** Routes view input to one canvas; not thread-safe. */
@InternalApi
final class InputRouter implements AutoCloseable {
  private static final KeyCode[] KEY_CODES = KeyCode.values();

  private final Canvas canvas;
  private final DpiContext context;
  private final @Nullable View view;
  private final InputSnapshot snapshot;
  private final @Nullable Element[] keyCaptures = new Element[KEY_CODES.length];
  private @Nullable Element hoveredElement;
  private float pointerX;
  private float pointerY;
  private long hoveredSinceNanos;
  private String clipboardText = "";
  private boolean registered;
  private boolean closed;

  InputRouter(Canvas canvas, DpiContext context) {
    this.canvas = canvas;
    this.context = context;
    view = context.view();
    snapshot = view == null ? new InputSnapshot() : view.snapshot();
    if (view != null) {
      registerViewCallbacks(view);
      registered = true;
    }
  }

  InputSnapshot snapshot() {
    return snapshot;
  }

  void dispatchMouseMove(double inputX, double inputY) {
    ensureOpen();
    if (view == null) {
      snapshot.applyMouseMove(inputX, inputY);
    }
    updatePointer(inputX, inputY);
    purgeDetachedReferences();

    Element captured = firstCapture();
    if (captured != null) {
      Rectangle absolute = captured.absoluteBounds();
      setHovered(absolute.contains(pointerX, pointerY) ? captured : null);
      captured.onMouseMove(pointerX - absolute.minX(), pointerY - absolute.minY());
      return;
    }
    setHovered(routeMouseMove(canvas, pointerX, pointerY));
  }

  boolean dispatchMouseButton(KeyCode button, KeyAction action, double inputX, double inputY,
                              int modifiers) {
    ensureOpen();
    int mouseId = button.mouseId();
    if (mouseId < 0) {
      throw new IllegalArgumentException("Pointer callback requires a mouse button, got " + button);
    }
    if (view == null) {
      snapshot.applyMouseButton(button, action, inputX, inputY, modifiers);
    }
    updatePointer(inputX, inputY);
    purgeDetachedReferences();

    if (action == KeyAction.PRESS) {
      int captureIndex = button.ordinal();
      Element previousCapture = keyCaptures[captureIndex];
      if (previousCapture != null) {
        cancelCapture(previousCapture, button);
      }
      canvas.bringWindowToFrontAt(pointerX, pointerY);
      Element target = routeMouseButton(canvas, pointerX, pointerY, button, action, modifiers);
      keyCaptures[captureIndex] = target;
      canvas.focusNearest(target);
      setHovered(routeMouseMove(canvas, pointerX, pointerY));
      return target != null;
    }

    int captureIndex = button.ordinal();
    Element target = keyCaptures[captureIndex];
    if (action == KeyAction.RELEASE) {
      keyCaptures[captureIndex] = null;
    }
    if (target == null) {
      target = routeMouseButton(canvas, pointerX, pointerY, button, action, modifiers);
    } else {
      Rectangle absolute = target.absoluteBounds();
      target.onMouseButton(pointerX - absolute.minX(), pointerY - absolute.minY(), button, action,
          modifiers);
    }
    setHovered(routeMouseMove(canvas, pointerX, pointerY));
    return target != null;
  }

  boolean dispatchScroll(double deltaX, double deltaY, double inputX, double inputY) {
    ensureOpen();
    if (view == null) {
      snapshot.applyScroll(deltaX, deltaY);
    }
    updatePointer(inputX, inputY);
    purgeDetachedReferences();
    return routeScroll(canvas, pointerX, pointerY, deltaX, deltaY) != null;
  }

  boolean dispatchKey(KeyCode key, KeyAction action, int modifiers) {
    ensureOpen();
    if (view == null) {
      snapshot.applyKey(key, action, modifiers);
    }
    int captureIndex = key.ordinal();
    Element target = keyCaptures[captureIndex];
    if (action == KeyAction.PRESS) {
      if (target != null) {
        cancelCapture(target, key);
      }
      target = routeKey(canvas.focusedElement(), key, action, modifiers);
      keyCaptures[captureIndex] = target;
      return target != null;
    }
    if (target != null) {
      if (action == KeyAction.RELEASE) {
        keyCaptures[captureIndex] = null;
      }
      return target.onKey(key, action, modifiers);
    }
    return routeKey(canvas.focusedElement(), key, action, modifiers) != null;
  }

  boolean dispatchCharacter(int codepoint) {
    ensureOpen();
    if (!Character.isValidCodePoint(codepoint)) {
      throw new IllegalArgumentException("Invalid Unicode code point: " + codepoint);
    }
    Element target = canvas.focusedElement();
    while (target != null) {
      if (target.onCharacter(codepoint)) {
        return true;
      }
      target = target.parent();
    }
    return false;
  }

  void treeChanged() {
    if (!closed) {
      purgeDetachedReferences();
    }
  }

  void clearPointerState() {
    setHovered(null);
    cancelPointerCaptures();
  }

  void clearAllInputState() {
    setHovered(null);
    cancelCaptures();
    snapshot.clearInputState();
  }

  String getClipboardText() {
    if (view instanceof DesktopView desktopView) {
      return desktopView.getClipboardText();
    }
    return clipboardText;
  }

  void setClipboardText(String value) {
    clipboardText = value;
    if (view instanceof DesktopView desktopView) {
      desktopView.setClipboardText(value);
    }
  }

  void drawTooltip(Graphics graphics) {
    if (hoveredElement == null) {
      return;
    }
    List<Text> tooltip = new ArrayList<>();
    hoveredElement.appendTooltips(tooltip);
    if (tooltip.isEmpty()) {
      return;
    }
    long delayNanos = hoveredElement.tooltipDelayMillis() * 1_000_000L;
    if (System.nanoTime() - hoveredSinceNanos < delayNanos) {
      return;
    }
    TooltipRenderer.render(graphics, tooltip, pointerX + 4.0F, pointerY + 4.0F);
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    if (view != null && registered) {
      view.eventBus().deregister(this);
      registered = false;
    }
    clearAllInputState();
    closed = true;
  }

  private void registerViewCallbacks(View source) {
    source.eventBus().register(ResizeEvent.class, (eventContext, event) -> {
      if (event.width() > 0 && event.height() > 0) {
        context.resize(event.width(), event.height());
      }
    }, this);
    source.eventBus().register(MouseMoveEvent.class,
        (eventContext, event) -> dispatchMouseMove(event.x(), event.y()), this);
    source.eventBus().register(MouseButtonEvent.class, (eventContext, event) ->
        dispatchMouseButton(event.button(), event.action(), event.x(), event.y(),
            event.modifiers()), this);
    source.eventBus().register(ScrollEvent.class, (eventContext, event) ->
        dispatchScroll(event.dx(), event.dy(), event.x(), event.y()), this);
    source.eventBus().register(KeyEvent.class,
        (eventContext, event) -> dispatchKey(event.code(), event.action(), event.modifiers()), this);
    source.eventBus().register(CharEvent.class,
        (eventContext, event) -> dispatchCharacter(event.codepoint()), this);
    source.eventBus().register(CursorEnterEvent.class, (eventContext, event) -> {
      if (!event.entered()) {
        clearPointerState();
      }
    }, this);
    source.eventBus().register(FocusEvent.class, (eventContext, event) -> {
      if (!event.focused()) {
        clearAllInputState();
        canvas.clearFocusFromContext();
      }
    }, this);
  }

  private void updatePointer(double inputX, double inputY) {
    Vector2 logical = context.inputToLogical(inputX, inputY);
    pointerX = logical.x();
    pointerY = logical.y();
  }

  private @Nullable Element routeMouseMove(Element element, float x, float y) {
    if (!element.visible()) {
      return null;
    }
    Element target = routeMouseMoveParts(element, x, y);
    if (target != null) {
      return target;
    }
    return routeMouseMoveContent(element, x, y);
  }

  private @Nullable Element routeMouseMoveContent(Element element, float x, float y) {
    if (!element.containsLocal(x, y)) {
      return null;
    }
    Element target = routeMouseMoveContent(element.children(), x, y);
    if (target != null) {
      return target;
    }
    return element.onMouseMove(x, y) || element.hasTooltipForRender() ? element : null;
  }

  private @Nullable Element routeMouseMoveContent(List<Element> elements, float x, float y) {
    for (int i = elements.size() - 1; i >= 0; i--) {
      Element child = elements.get(i);
      Element target = routeMouseMoveContent(child,
          x - child.bounds().minX(), y - child.bounds().minY());
      if (target != null) {
        return target;
      }
    }
    return null;
  }

  private @Nullable Element routeMouseMoveParts(Element element, float x, float y) {
    Element target = routeMouseMove(element.parts(), x, y);
    if (target != null) {
      return target;
    }
    for (int i = element.children().size() - 1; i >= 0; i--) {
      Element child = element.children().get(i);
      target = routeMouseMoveParts(child,
          x - child.bounds().minX(), y - child.bounds().minY());
      if (target != null) {
        return target;
      }
    }
    return null;
  }

  private @Nullable Element routeMouseMove(List<Element> elements, float x, float y) {
    for (int i = elements.size() - 1; i >= 0; i--) {
      Element child = elements.get(i);
      Element target = routeMouseMove(child,
          x - child.bounds().minX(), y - child.bounds().minY());
      if (target != null) {
        return target;
      }
    }
    return null;
  }

  private @Nullable Element routeMouseButton(Element element, float x, float y,
                                             KeyCode button, KeyAction action, int modifiers) {
    if (!element.visible()) {
      return null;
    }
    Element target = routeMouseButtonParts(element, x, y, button, action, modifiers);
    if (target != null) {
      return target;
    }
    return routeMouseButtonContent(element, x, y, button, action, modifiers);
  }

  private @Nullable Element routeMouseButtonContent(Element element, float x, float y,
      KeyCode button, KeyAction action, int modifiers) {
    if (!element.containsLocal(x, y)) {
      return null;
    }
    Element target = routeMouseButtonContent(element.children(), x, y, button, action, modifiers);
    if (target != null) {
      return target;
    }
    return element.onMouseButton(x, y, button, action, modifiers) ? element : null;
  }

  private @Nullable Element routeMouseButtonParts(Element element, float x, float y,
      KeyCode button, KeyAction action, int modifiers) {
    Element target = routeMouseButton(element.parts(), x, y, button, action, modifiers);
    if (target != null) {
      return target;
    }
    for (int i = element.children().size() - 1; i >= 0; i--) {
      Element child = element.children().get(i);
      target = routeMouseButtonParts(child,
          x - child.bounds().minX(), y - child.bounds().minY(), button, action, modifiers);
      if (target != null) {
        return target;
      }
    }
    return null;
  }

  private @Nullable Element routeMouseButtonContent(List<Element> elements, float x, float y,
      KeyCode button, KeyAction action, int modifiers) {
    for (int i = elements.size() - 1; i >= 0; i--) {
      Element child = elements.get(i);
      Element target = routeMouseButtonContent(child,
          x - child.bounds().minX(), y - child.bounds().minY(), button, action, modifiers);
      if (target != null) {
        return target;
      }
    }
    return null;
  }

  private @Nullable Element routeMouseButton(List<Element> elements, float x, float y,
                                             KeyCode button, KeyAction action, int modifiers) {
    for (int i = elements.size() - 1; i >= 0; i--) {
      Element child = elements.get(i);
      Element target = routeMouseButton(child,
          x - child.bounds().minX(), y - child.bounds().minY(), button, action, modifiers);
      if (target != null) {
        return target;
      }
    }
    return null;
  }

  private @Nullable Element routeScroll(Element element, float x, float y,
                                        double deltaX, double deltaY) {
    if (!element.visible()) {
      return null;
    }
    Element target = routeScrollParts(element, x, y, deltaX, deltaY);
    if (target != null) {
      return target;
    }
    return routeScrollContent(element, x, y, deltaX, deltaY);
  }

  private @Nullable Element routeScrollContent(Element element, float x, float y,
      double deltaX, double deltaY) {
    if (!element.containsLocal(x, y)) {
      return null;
    }
    Element target = routeScrollContent(element.children(), x, y, deltaX, deltaY);
    if (target != null) {
      return target;
    }
    return element.onScroll(x, y, deltaX, deltaY) ? element : null;
  }

  private @Nullable Element routeScrollParts(Element element, float x, float y,
      double deltaX, double deltaY) {
    Element target = routeScroll(element.parts(), x, y, deltaX, deltaY);
    if (target != null) {
      return target;
    }
    for (int i = element.children().size() - 1; i >= 0; i--) {
      Element child = element.children().get(i);
      target = routeScrollParts(child,
          x - child.bounds().minX(), y - child.bounds().minY(), deltaX, deltaY);
      if (target != null) {
        return target;
      }
    }
    return null;
  }

  private @Nullable Element routeScrollContent(List<Element> elements, float x, float y,
      double deltaX, double deltaY) {
    for (int i = elements.size() - 1; i >= 0; i--) {
      Element child = elements.get(i);
      Element target = routeScrollContent(child,
          x - child.bounds().minX(), y - child.bounds().minY(), deltaX, deltaY);
      if (target != null) {
        return target;
      }
    }
    return null;
  }

  private @Nullable Element routeScroll(List<Element> elements, float x, float y,
                                        double deltaX, double deltaY) {
    for (int i = elements.size() - 1; i >= 0; i--) {
      Element child = elements.get(i);
      Element target = routeScroll(child,
          x - child.bounds().minX(), y - child.bounds().minY(), deltaX, deltaY);
      if (target != null) {
        return target;
      }
    }
    return null;
  }

  private void setHovered(@Nullable Element target) {
    if (hoveredElement == target) {
      return;
    }
    if (hoveredElement != null) {
      hoveredElement.onPointerExit();
    }
    hoveredElement = target;
    if (target != null) {
      hoveredSinceNanos = System.nanoTime();
      target.onPointerEnter();
    }
  }

  private @Nullable Element firstCapture() {
    for (KeyCode key : KEY_CODES) {
      if (key.mouseId() >= 0) {
        Element capture = keyCaptures[key.ordinal()];
        if (capture != null) {
          return capture;
        }
      }
    }
    return null;
  }

  private void cancelCaptures() {
    for (KeyCode key : KEY_CODES) {
      Element capture = keyCaptures[key.ordinal()];
      if (capture != null) {
        cancelCapture(capture, key);
        keyCaptures[key.ordinal()] = null;
      }
    }
  }

  private void cancelPointerCaptures() {
    for (KeyCode key : KEY_CODES) {
      if (key.mouseId() >= 0) {
        Element capture = keyCaptures[key.ordinal()];
        if (capture != null) {
          cancelCapture(capture, key);
          keyCaptures[key.ordinal()] = null;
        }
      }
    }
  }

  private void purgeDetachedReferences() {
    if (hoveredElement != null && !canvas.isAttached(hoveredElement)) {
      setHovered(null);
    }
    for (KeyCode key : KEY_CODES) {
      Element capture = keyCaptures[key.ordinal()];
      if (capture != null && !canvas.isAttached(capture)) {
        cancelCapture(capture, key);
        keyCaptures[key.ordinal()] = null;
      }
    }
  }

  private @Nullable Element routeKey(@Nullable Element target, KeyCode key, KeyAction action,
                                     int modifiers) {
    Element current = target;
    while (current != null) {
      if (current.onKey(key, action, modifiers)) {
        return current;
      }
      current = current.parent();
    }
    return null;
  }

  private static void cancelCapture(Element target, KeyCode key) {
    if (key.mouseId() >= 0) {
      target.onPointerCancel(key);
    } else {
      target.onKeyCancel(key);
    }
  }

  private void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("Canvas input router is closed");
    }
  }
}
