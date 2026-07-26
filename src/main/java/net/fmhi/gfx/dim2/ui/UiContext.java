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

package net.fmhi.gfx.dim2.ui;

import net.fmhi.fml.event.EventBus;
import net.fmhi.fml.event.EventListener;
import net.fmhi.gfx.Device;
import net.fmhi.gfx.View;
import net.fmhi.gfx.input.KeyAction;
import net.fmhi.gfx.input.event.*;
import net.fmhi.gfx.dim2.mesh.Graphics2D;
import net.fmhi.math.Box2D;
import net.fmhi.math.Vector2;
import net.fmhi.math.dim2.Camera2D;
import net.fmhi.gfx.dim2.ui.widget.Window;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Root manager for the UI widget tree.
 *
 * <p>{@code UiContext} translates physical screen events into logical {@link UiEvent}s using an
 * active {@link Camera2D}, then dispatches them through the widget tree. Keyboard and character events are routed to
 * the currently focused widget. Mouse events are routed to floating windows in reverse z-order, with modal windows
 * intercepting all input. Root widgets receive events only when no floating window covers the cursor position.
 *
 * <p>This class is not thread-safe. All methods must be called from the rendering thread.
 */
public final class UiContext implements AutoCloseable {
  private final Look look;
  private final Camera2D camera;

  // floating windows stored in z-order: index 0 = bottommost
  private final List<Window> windows = new ArrayList<>();
  // root non-window widgets, also in z-order
  private final List<Widget> rootWidgets = new ArrayList<>();
  // modal stack: only the top modal receives events
  private final Deque<Window> modalStack = new ArrayDeque<>();
  private @Nullable Widget focusedWidget;
  // current logical mouse position
  private float mouseX;
  private float mouseY;
  // physical screen viewport (updated on resize)
  private Box2D viewport;
  // physical pixels per logical unit
  private float pixelDensity = 1.0F;
  private boolean disposed;
  private EventBus eventBus;

  /**
   * Creates a {@code UiContext} attached to the given view.
   *
   * <p>The camera's logical size is initialized to the canvas. On
   * subsequent resizes both the camera and viewport scale together, so widgets with anchor layouts reflow naturally and
   * floating windows are clamped to stay on-screen.
   *
   * @param device the graphics device
   * @param view   the view to attach to
   * @param look   the look implementation used for all rendering
   * @param canvas the size of the canvas
   */
  public UiContext(Device device, View view, Look look, Box2D canvas) {
    float w = canvas.width();
    float h = canvas.height();

    this.look = look;
    this.camera = new Camera2D(w, h);
    this.viewport = canvas;

    updatePixelDensity();
    attach(view);
  }

  /** Extracts the X coordinate from positional events, or NaN for key/char events. */
  private static float eventX(UiEvent event) {
    return switch (event) {
      case UiEvent.MouseMove mm -> mm.x();
      case UiEvent.MouseButton mb -> mb.x();
      case UiEvent.Scroll sc -> sc.x();
      default -> Float.NaN;
    };
  }

  /** Extracts the Y coordinate from positional events, or NaN for key/char events. */
  private static float eventY(UiEvent event) {
    return switch (event) {
      case UiEvent.MouseMove mm -> mm.y();
      case UiEvent.MouseButton mb -> mb.y();
      case UiEvent.Scroll sc -> sc.y();
      default -> Float.NaN;
    };
  }

  private void attach(View view) {
    this.eventBus = view.eventBus();
    EventBus bus = this.eventBus;

    // Mouse move
    EventListener<MouseMoveEvent> mouseMove = (ctx, e) -> {
      Vector2 logical = camera.unproject(new Vector2((float) e.x(), (float) e.y()), viewport);
      mouseX = logical.x();
      mouseY = logical.y();
      dispatchEvent(new UiEvent.MouseMove(mouseX, mouseY));
    };
    bus.register(MouseMoveEvent.class, mouseMove, this);

    // Mouse button
    EventListener<MouseButtonEvent> mouseButton = (ctx, e) -> {
      dispatchEvent(new UiEvent.MouseButton(mouseX, mouseY, e.button().mouseId(), e.action() != KeyAction.RELEASE));
    };
    bus.register(MouseButtonEvent.class, mouseButton, this);

    // Scroll
    EventListener<ScrollEvent> scroll = (ctx, e) -> dispatchEvent(new UiEvent.Scroll(mouseX, mouseY, (float) e.dx(),
        (float) e.dy()));
    bus.register(ScrollEvent.class, scroll, this);

    // Key
    EventListener<KeyEvent> key = (ctx, e) -> {
      boolean pressed = e.action() != KeyAction.RELEASE;
      boolean repeat = e.action() == KeyAction.REPEAT;
      dispatchEvent(new UiEvent.Key(e.code().hidCode(), pressed, repeat));
    };
    bus.register(KeyEvent.class, key, this);

    // Char
    EventListener<CharEvent> ch = (ctx, e) -> dispatchEvent(new UiEvent.Char((char) e.codepoint()));
    bus.register(CharEvent.class, ch, this);

    // Resize
    EventListener<ResizeEvent> resize = (ctx, e) -> {
      float pw = e.width();
      float ph = e.height();
      float logW = camera.width();
      float logH = camera.height();
      float scale = Math.max(pw / logW, ph / logH);
      float scaledW = logW * scale;
      float scaledH = logH * scale;
      float offX = (pw - scaledW) * 0.5F;
      float offY = (ph - scaledH) * 0.5F;
      viewport = Box2D.create(offX, offY, scaledW, scaledH);
      updatePixelDensity();
      look.setPixelDensity(pixelDensity);
      for (Window fw : windows) {
        fw.clampToBounds(logW, logH);
      }
    };
    bus.register(ResizeEvent.class, resize, this);
  }

  private void updatePixelDensity() {
    float cw = camera.width();
    // viewport.width() is the scaled physical extent that covers the logical area
    pixelDensity = cw > 0 ? viewport.width() / cw : 1.0F;
  }

  /**
   * Runs layout, update, and render passes for the entire widget tree.
   *
   * <p>Call this once per frame, before {@code Device.execute()}.
   *
   * @param g         the drawing context
   * @param deltaTime seconds elapsed since the previous frame
   */
  public void render(Graphics2D g, float deltaTime) {
    if (disposed) {
      return;
    }

    Box2D rootContent = Box2D.create(0, 0, camera.width(), camera.height());

    // Layout pass
    for (Widget w : rootWidgets) {
      w.layout(rootContent);
    }
    for (Window fw : windows) {
      fw.layout(rootContent);
    }

    // Update pass
    for (Widget w : rootWidgets) {
      w.update(deltaTime);
    }
    for (Window fw : windows) {
      fw.update(deltaTime);
    }

    // Render pass
    g.setCamera(camera);
    g.setViewport(viewport);

    for (Widget w : rootWidgets) {
      w.render(g, look, null);
    }
    for (Window fw : windows) {
      fw.render(g, look, null);
    }

    g.flush();
  }

  private void dispatchEvent(UiEvent event) {
    // Keyboard/char events go to the focused widget first
    if (event instanceof UiEvent.Key || event instanceof UiEvent.Char) {
      if (focusedWidget != null && focusedWidget.handleEvent(event, true)) {
        return;
      }
    }
    // Modal window intercepts everything else
    if (!modalStack.isEmpty()) {
      Window modal = modalStack.peek();
      modal.handleEvent(event, true);
      return;
    }
    // Dispatch to all windows in reverse z-order (topmost first).
    // A window is reachable only if no higher-z visible window's bounds contain the cursor.
    float ex = eventX(event);
    float ey = eventY(event);
    boolean covered = false;
    for (int i = windows.size() - 1; i >= 0; i--) {
      Window fw = windows.get(i);
      fw.handleEvent(event, !covered);
      // Once we've seen a visible window whose bounds contain the cursor, all windows
      // below it are covered for this event position.
      if (!covered && fw.isVisible() && fw.absoluteBounds().contains(ex, ey)) {
        covered = true;
      }
    }
    // Root widgets: covered if any window sits on top of the cursor position
    boolean rootCovered = windows.stream().anyMatch(fw -> fw.isVisible() && fw.absoluteBounds().contains(ex, ey));
    for (int i = rootWidgets.size() - 1; i >= 0; i--) {
      rootWidgets.get(i).handleEvent(event, !rootCovered);
    }
  }

  /**
   * Adds a floating window to the root level.
   *
   * @param window the window to add
   */
  public void addWindow(Window window) {
    window.setContext(this);
    windows.add(window);
  }

  /**
   * Removes a floating window from the root level.
   *
   * @param window the window to remove
   */
  public void removeWindow(Window window) {
    if (windows.remove(window)) {
      window.setContext(null);
    }
  }

  /**
   * Brings the given window to the top of the z-order and gives it keyboard focus. All other windows lose focus.
   *
   * @param window the window to bring to the front
   */
  public void bringToFront(Window window) {
    if (windows.remove(window)) {
      windows.add(window);
    }
    for (Window fw : windows) {
      fw.setFocused(fw == window);
    }
  }

  /**
   * Pushes a modal window onto the modal stack. While any modal is active, only the topmost modal receives events.
   *
   * @param window the modal window
   */
  public void pushModal(Window window) {
    modalStack.push(window);
  }

  /**
   * Removes the topmost modal window from the stack.
   */
  public void popModal() {
    modalStack.poll();
  }

  /**
   * Adds a root-level widget that is not a floating window.
   *
   * @param widget the widget to add
   */
  public void addWidget(Widget widget) {
    rootWidgets.add(widget);
  }

  /**
   * Removes a root-level widget.
   *
   * @param widget the widget to remove
   */
  public void removeWidget(Widget widget) {
    rootWidgets.remove(widget);
  }

  /**
   * Sets the widget that receives keyboard and character events.
   *
   * @param widget the widget to focus, or {@code null} to clear focus
   */
  public void setFocus(@Nullable Widget widget) {
    focusedWidget = widget;
  }

  /**
   * Returns the currently focused widget.
   *
   * @return the focused widget, or {@code null} if no widget has focus
   */
  public @Nullable Widget focusedWidget() {
    return focusedWidget;
  }

  /**
   * Returns the logical-coordinate camera used by this UI context.
   *
   * @return the camera
   */
  public Camera2D camera() {
    return camera;
  }

  /**
   * Returns the active look implementation.
   *
   * @return the look instance
   */
  public Look look() {
    return look;
  }

  /**
   * Returns the ratio of physical pixels per logical unit. A value of 1.0 means logical coordinates map 1:1 to screen
   * pixels.
   *
   * @return the current pixel density ratio
   */
  public float pixelDensity() {
    return pixelDensity;
  }

  /**
   * Returns the current physical viewport rectangle.
   *
   * @return the viewport rectangle
   */
  public Box2D viewport() {
    return viewport;
  }

  /**
   * Destroys this UI context, deregistering all input listeners and releasing resources. This method is idempotent.
   */
  @Override
  public void close() {
    if (disposed) {
      return;
    }
    disposed = true;
    eventBus.deregister(this);
  }
}
