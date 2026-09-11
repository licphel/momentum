/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 */
package io.viki.momentum.gfx.ui;

import io.viki.momentum.input.KeyAction;
import io.viki.momentum.input.KeyCode;
import io.viki.momentum.input.InputSnapshot;
import io.viki.momentum.input.event.CharEvent;
import io.viki.momentum.input.event.CursorEnterEvent;
import io.viki.momentum.input.event.FocusEvent;
import io.viki.momentum.input.event.KeyEvent;
import io.viki.momentum.input.event.MouseButtonEvent;
import io.viki.momentum.input.event.MouseMoveEvent;
import io.viki.momentum.input.event.ResizeEvent;
import io.viki.momentum.input.event.ScrollEvent;
import io.viki.momentum.gfx.math.TransformHandler;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.gfx.view.View;
import io.viki.momentum.math.Box2D;
import io.viki.momentum.math.Vector2;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Abstract UI screen and the sole entry point for input dispatch and coordinate conversion.
 *
 * <p>A context may be backed by a {@link View}, in which case it subscribes to the view when a
 * {@link Canvas} binds to it, or it may be created bare and driven through the dispatch methods.
 * It is mutable and not thread-safe; use it only on the owning UI thread.
 */
public final class PrimaryContext implements AutoCloseable {
  private static final KeyCode[] KEY_CODES = KeyCode.values();

  private final @Nullable View view;
  private final TransformHandler transformHandler;
  private final Resolution resolution;
  private final InputSnapshot snapshot = new InputSnapshot();
  private final @Nullable Element[] keyCaptures = new Element[KEY_CODES.length];
  private Vector2 inputSize;
  private @Nullable Canvas canvas;
  private @Nullable Element hoveredElement;
  private float pointerX;
  private float pointerY;
  private boolean registered;
  private boolean closed;

  public PrimaryContext(int framebufferWidth, int framebufferHeight,
                        TransformHandler transformHandler) {
    this(framebufferWidth, framebufferHeight, Resolution.DEFAULT_LOGICAL_WIDTH,
        Resolution.DEFAULT_LOGICAL_HEIGHT, false, transformHandler);
  }

  public PrimaryContext(int framebufferWidth, int framebufferHeight, float logicalWidth,
                        float logicalHeight, boolean onlyIntegerScale,
                        TransformHandler transformHandler) {
    this(null, new Vector2(framebufferWidth, framebufferHeight),
        Resolution.auto(framebufferWidth, framebufferHeight, logicalWidth, logicalHeight,
            onlyIntegerScale, transformHandler), transformHandler);
  }

  public PrimaryContext(View view, TransformHandler transformHandler) {
    this(view, Resolution.DEFAULT_LOGICAL_WIDTH, Resolution.DEFAULT_LOGICAL_HEIGHT, false,
        transformHandler);
  }

  public PrimaryContext(View view, float logicalWidth, float logicalHeight,
                        boolean onlyIntegerScale, TransformHandler transformHandler) {
    this(view, view.getInputSize(),
        Resolution.auto(view.getWidth(), view.getHeight(), logicalWidth, logicalHeight,
            onlyIntegerScale, transformHandler), transformHandler);
  }

  private PrimaryContext(@Nullable View view, Vector2 inputSize, Resolution resolution,
                         TransformHandler transformHandler) {
    validateSize(inputSize.x(), inputSize.y(), "Input coordinate");
    this.view = view;
    this.inputSize = inputSize;
    this.resolution = resolution;
    this.transformHandler = transformHandler;
  }

  public Vector2 getInputSize() {
    return view == null ? inputSize : view.getInputSize();
  }

  public Vector2 getLogicalSize() {
    return resolution.logicalSize();
  }

  public TransformHandler getTransformHandler() {
    return transformHandler;
  }

  public Resolution resolution() {
    return resolution;
  }

  /** Returns the screen's current pollable input state. */
  public InputSnapshot snapshot() {
    return snapshot;
  }

  public void setInputSize(float width, float height) {
    ensureBare();
    validateSize(width, height, "Input coordinate");
    inputSize = new Vector2(width, height);
  }

  public void resize(int framebufferWidth, int framebufferHeight) {
    ensureOpen();
    resolution.resize(framebufferWidth, framebufferHeight);
    if (view == null) {
      inputSize = new Vector2(framebufferWidth, framebufferHeight);
    }
  }

  public void dispatchMouseMove(double inputX, double inputY) {
    Canvas targetCanvas = boundCanvas();
    snapshot.applyMouseMove(inputX, inputY);
    updatePointer(inputX, inputY);
    purgeDetachedReferences(targetCanvas);

    Element captured = firstCapture();
    if (captured != null) {
      Box2D absolute = captured.absoluteBounds();
      setHovered(absolute.contains(pointerX, pointerY) ? captured : null);
      captured.onMouseMove(pointerX - absolute.minX(), pointerY - absolute.minY());
      return;
    }
    setHovered(routeMouseMove(targetCanvas, pointerX, pointerY));
  }

  public boolean dispatchMouseButton(KeyCode button, KeyAction action, double inputX,
                                     double inputY, int modifiers) {
    Canvas targetCanvas = boundCanvas();
    int mouseId = button.mouseId();
    if (mouseId < 0) {
      throw new IllegalArgumentException("Pointer callback requires a mouse button, got " + button);
    }
    snapshot.applyMouseButton(button, action, inputX, inputY, modifiers);
    updatePointer(inputX, inputY);
    purgeDetachedReferences(targetCanvas);

    if (action == KeyAction.PRESS) {
      int captureIndex = button.ordinal();
      Element previousCapture = keyCaptures[captureIndex];
      if (previousCapture != null) {
        cancelCapture(previousCapture, button);
      }
      Element target = routeMouseButton(targetCanvas, pointerX, pointerY, button, action, modifiers);
      keyCaptures[captureIndex] = target;
      targetCanvas.focusNearest(target);
      setHovered(routeMouseMove(targetCanvas, pointerX, pointerY));
      return target != null;
    }

    int captureIndex = button.ordinal();
    Element target = keyCaptures[captureIndex];
    if (action == KeyAction.RELEASE) {
      keyCaptures[captureIndex] = null;
    }
    if (target == null) {
      target = routeMouseButton(targetCanvas, pointerX, pointerY, button, action, modifiers);
    } else {
      Box2D absolute = target.absoluteBounds();
      target.onMouseButton(pointerX - absolute.minX(), pointerY - absolute.minY(), button, action,
          modifiers);
    }
    setHovered(routeMouseMove(targetCanvas, pointerX, pointerY));
    return target != null;
  }

  public boolean dispatchScroll(double deltaX, double deltaY, double inputX, double inputY) {
    Canvas targetCanvas = boundCanvas();
    snapshot.applyScroll(deltaX, deltaY);
    updatePointer(inputX, inputY);
    purgeDetachedReferences(targetCanvas);
    return routeScroll(targetCanvas, pointerX, pointerY, deltaX, deltaY) != null;
  }

  public boolean dispatchKey(KeyCode key, KeyAction action, int modifiers) {
    Canvas targetCanvas = boundCanvas();
    snapshot.applyKey(key, action, modifiers);
    int captureIndex = key.ordinal();
    Element target = keyCaptures[captureIndex];
    if (action == KeyAction.PRESS) {
      if (target != null) {
        cancelCapture(target, key);
      }
      target = routeKey(targetCanvas.focusedElement(), key, action, modifiers);
      keyCaptures[captureIndex] = target;
      return target != null;
    }
    if (target != null) {
      if (action == KeyAction.RELEASE) {
        keyCaptures[captureIndex] = null;
      }
      return target.onKey(key, action, modifiers);
    }
    return routeKey(targetCanvas.focusedElement(), key, action, modifiers) != null;
  }

  public boolean dispatchCharacter(int codepoint) {
    Canvas targetCanvas = boundCanvas();
    if (!Character.isValidCodePoint(codepoint)) {
      throw new IllegalArgumentException("Invalid Unicode code point: " + codepoint);
    }
    Element target = targetCanvas.focusedElement();
    while (target != null) {
      if (target.onCharacter(codepoint)) {
        return true;
      }
      target = target.parent();
    }
    return false;
  }

  /** Clears this screen's transient input state after it finishes a frame. */
  public void clearFrameState() {
    ensureOpen();
    snapshot.clearFrameState();
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
    if (canvas != null) {
      canvas.clearFocusFromContext();
    }
    closed = true;
  }

  void bind(Canvas targetCanvas) {
    ensureOpen();
    if (canvas != null) {
      throw new IllegalStateException("PrimaryContext is already bound to a Canvas");
    }
    canvas = targetCanvas;
    if (view != null) {
      registerViewCallbacks(view);
      registered = true;
    }
  }

  void apply(Graphics graphics) {
    ensureOpen();
    resolution.apply(graphics);
  }

  void treeChanged() {
    if (canvas != null) {
      purgeDetachedReferences(canvas);
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

  private void registerViewCallbacks(View source) {
    source.eventBus().register(ResizeEvent.class, (context, event) -> {
      if (event.width() > 0 && event.height() > 0) {
        resize(event.width(), event.height());
      }
    }, this);
    source.eventBus().register(MouseMoveEvent.class,
        (context, event) -> dispatchMouseMove(event.x(), event.y()), this);
    source.eventBus().register(MouseButtonEvent.class, (context, event) ->
        dispatchMouseButton(event.button(), event.action(), event.x(), event.y(),
            event.modifiers()), this);
    source.eventBus().register(ScrollEvent.class, (context, event) ->
        dispatchScroll(event.dx(), event.dy(), event.x(), event.y()), this);
    source.eventBus().register(KeyEvent.class,
        (context, event) -> dispatchKey(event.code(), event.action(), event.modifiers()), this);
    source.eventBus().register(CharEvent.class,
        (context, event) -> dispatchCharacter(event.codepoint()), this);
    source.eventBus().register(CursorEnterEvent.class, (context, event) -> {
      if (!event.entered()) {
        clearPointerState();
      }
    }, this);
    source.eventBus().register(FocusEvent.class, (context, event) -> {
      if (!event.focused()) {
        clearAllInputState();
        boundCanvas().clearFocusFromContext();
      }
    }, this);
  }

  private void updatePointer(double inputX, double inputY) {
    Vector2 size = getInputSize();
    Vector2 logical = resolution.inputToLogical(inputX, inputY, size.x(), size.y());
    pointerX = logical.x();
    pointerY = logical.y();
  }

  private @Nullable Element routeMouseMove(Element element, float x, float y) {
    if (!element.containsLocal(x, y)) {
      return null;
    }
    Element target = routeMouseMove(element.parts(), x, y);
    if (target != null) {
      return target;
    }
    target = routeMouseMove(element.children(), x, y);
    if (target != null) {
      return target;
    }
    return element.onMouseMove(x, y) ? element : null;
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

  private @Nullable Element routeMouseButton(Element element, float x, float y, KeyCode button,
                                             KeyAction action, int modifiers) {
    if (!element.containsLocal(x, y)) {
      return null;
    }
    Element target = routeMouseButton(element.parts(), x, y, button, action, modifiers);
    if (target != null) {
      return target;
    }
    target = routeMouseButton(element.children(), x, y, button, action, modifiers);
    if (target != null) {
      return target;
    }
    return element.onMouseButton(x, y, button, action, modifiers) ? element : null;
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
    if (!element.containsLocal(x, y)) {
      return null;
    }
    Element target = routeScroll(element.parts(), x, y, deltaX, deltaY);
    if (target != null) {
      return target;
    }
    target = routeScroll(element.children(), x, y, deltaX, deltaY);
    if (target != null) {
      return target;
    }
    return element.onScroll(x, y, deltaX, deltaY) ? element : null;
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

  private void purgeDetachedReferences(Canvas targetCanvas) {
    if (hoveredElement != null && !targetCanvas.isAttached(hoveredElement)) {
      setHovered(null);
    }
    for (KeyCode key : KEY_CODES) {
      Element capture = keyCaptures[key.ordinal()];
      if (capture != null && !targetCanvas.isAttached(capture)) {
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

  private Canvas boundCanvas() {
    ensureOpen();
    if (canvas == null) {
      throw new IllegalStateException("PrimaryContext is not bound to a Canvas");
    }
    return canvas;
  }

  private void ensureBare() {
    ensureOpen();
    if (view != null) {
      throw new IllegalStateException("A View-backed PrimaryContext obtains input size from its View");
    }
  }

  private void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("PrimaryContext is closed");
    }
  }

  private static void validateSize(float width, float height, String name) {
    if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0.0F || height <= 0.0F) {
      throw new IllegalArgumentException(name + " size must be finite and positive: "
          + width + "x" + height);
    }
  }
}
