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

package net.fmhi.gfx;

import net.fmhi.event.Event;
import net.fmhi.event.EventBus;
import net.fmhi.gfx.input.Snapshot;
import net.fmhi.gfx.input.event.*;
import net.fmhi.gfx.io.ImageInfo;
import net.fmhi.math.Vector2;
import org.jspecify.annotations.Nullable;

/**
 * A window or drawable surface that a graphics device renders into.
 *
 * <p>{@code View} provides concrete window state management and input event routing, while
 * delegating platform-specific window operations to protected methods that subclasses implement. It supports desktop
 * windowing, mobile surfaces, and VR compositor layers through a single API.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Obtain a {@code View} from a {@link net.fmhi.gfx.spi.ViewProvider ViewProvider}.</li>
 *   <li>Configure state — title, size, decorations, etc. — before calling {@link #initialize()}.</li>
 *   <li>Call {@link #initialize()}.</li>
 *   <li>Each frame, call {@link #pollEvents()}, render, then {@link #present()}.</li>
 *   <li>Call {@link #close()} to destroy the view.</li>
 * </ol>
 *
 * <p>This class is not thread-safe. All methods must be called from the rendering thread.
 *
 * @see EventBus
 * @see Snapshot
 */
public abstract class View implements AutoCloseable {
  private final EventBus eventBus = new EventBus();
  private final Snapshot snapshot = new Snapshot();
  /** Current framebuffer width in pixels. */
  protected int width = 800;
  /** Current framebuffer height in pixels. */
  protected int height = 450;
  /** Current window X position in screen coordinates. */
  protected int x;
  /** Current window Y position in screen coordinates. */
  protected int y;
  /** Current window title string. */
  protected String title = "";
  /** Whether the window has title bar and borders. */
  protected boolean decorated = true;
  /** Whether the window is maximized. */
  protected boolean maximized;
  /** Whether the window auto-iconifies on focus loss. */
  protected boolean autoIconify = true;
  /** Whether the window floats above other windows. */
  protected boolean floating;
  /** Whether the window currently has input focus. */
  protected boolean focused;
  /** Whether the window is visible. */
  protected boolean visible;
  /** Whether the window can be resized by the user. */
  protected boolean resizable = true;
  /** Whether vertical sync is enabled. */
  protected boolean vsync = true;
  /** Whether graphics debug output is enabled. Must be set before {@link #initialize()}. */
  protected boolean debug;
  /** The window icon image, or {@code null} if no icon is set. */
  protected @Nullable ImageInfo iconImage;
  /** Cursor X position in screen coordinates relative to the window. */
  protected double cursorX;
  /** Cursor Y position in screen coordinates relative to the window. */
  protected double cursorY;
  /** Custom cursor image, or {@code null} for the system default cursor. */
  protected @Nullable ImageInfo cursorImage;
  /** Hotspot offset of the custom cursor in pixels, relative to the image top-left. */
  protected Vector2 cursorHotspot = Vector2.ZERO;
  /** Whether the cursor is in relative (raw) motion mode. */
  protected boolean cursorRelativeMode;
  /** Cached system clipboard text. */
  protected String clipboardText = "";
  /** Whether the view has been initialized via {@link #initialize()}. */
  protected boolean initialized;

  /**
   * Returns static information about this view, including the backend name.
   *
   * @return the view information
   */
  public abstract ViewInfo info();

  /**
   * Returns an opaque native handle used by the graphics device to bind to this view.
   *
   * <p>The concrete type is backend-specific and must be treated as opaque outside the paired
   * graphics backend.
   *
   * @return the native view handle
   */
  public abstract Object procAddress();

  /**
   * Returns the platform-native window handle used to create rendering surfaces.
   *
   * <p>On GLFW backends this is the {@code GLFWwindow*} pointer. Returns 0 if the view
   * has not been initialized or does not support native surface creation.
   *
   * @return the native window handle, or 0
   */
  public long nativeWindowHandle() {
    return 0L;
  }

  /**
   * Initializes the native view, configuring the platform window and graphics surface.
   *
   * <p>Must be called after configuring window state properties such as size, title, and
   * decorations. This method is idempotent — subsequent calls after the first have no effect.
   */
  public final void initialize() {
    if (initialized) {
      return;
    }
    onInitialize();
    initialized = true;
  }

  /**
   * Returns whether the view has been requested to close, for example by the user clicking the close button or the
   * platform sending a close signal.
   *
   * @return {@code true} if the view should close
   */
  public abstract boolean shouldClose();

  /**
   * Processes pending window events and dispatches them through the input system.
   *
   * <p>Call once per frame, before rendering. After dispatching events this method resets
   * transient per-frame input state such as scroll deltas and press transitions.
   */
  public final void pollEvents() {
    onPollEvents();
  }

  /**
   * Presents the most recent rendering result to the screen.
   *
   * <p>This is a synchronous operation. To present correctly from a rendering thread, wrap the
   * call in {@link net.fmhi.gfx.Device#submit(Runnable)}.
   */
  public abstract void present();

  /**
   * Destroys the view and releases all associated resources.
   *
   * <p>This method is idempotent — it is safe to call multiple times. All registered input
   * listeners are removed.
   */
  @Override
  public final void close() {
    if (!initialized) {
      return;
    }
    initialized = false;
    eventBus.clear();
    onClose();
  }

  /**
   * Returns the current view width in pixels.
   *
   * @return the width in pixels
   */
  public int width() {
    return width;
  }

  /**
   * Returns the current view height in pixels.
   *
   * @return the height in pixels
   */
  public int height() {
    return height;
  }

  /**
   * Returns the event bus for registering input event listeners.
   *
   * <p>Listeners are registered via {@link EventBus#register(Class, net.fmhi.event.EventListener)}
   * and removed via {@link EventBus#deregister(Object)}.
   *
   * @return the event bus
   */
  public EventBus eventBus() {
    return eventBus;
  }

  /**
   * Returns the pollable per-frame input state.
   *
   * @return the input state
   */
  public Snapshot snapshot() {
    return snapshot;
  }

  /**
   * Returns the current view size in pixels.
   *
   * @return the size as a vector
   */
  public Vector2 size() {
    return new Vector2(width, height);
  }

  /**
   * Sets the view size in pixels.
   *
   * <p>If the view has already been initialized, the new size is applied to the platform window
   * immediately.
   *
   * @param size the new size
   */
  public void setSize(Vector2 size) {
    width = (int) size.x();
    height = (int) size.y();
    if (initialized) {
      applyPlatformSize(width, height);
    }
  }

  /**
   * Returns the current view position in screen coordinates.
   *
   * @return the position as a vector
   */
  public Vector2 position() {
    return new Vector2(x, y);
  }

  /**
   * Sets the view position in screen coordinates.
   *
   * <p>If the view has already been initialized, the new position is applied to the platform
   * window immediately.
   *
   * @param position the new position
   */
  public void setPosition(Vector2 position) {
    x = (int) position.x();
    y = (int) position.y();
    if (initialized) {
      applyPlatformPosition(x, y);
    }
  }

  /**
   * Returns the current window title.
   *
   * @return the title string
   */
  public String title() {
    return title;
  }

  /**
   * Sets the window title.
   *
   * <p>If the view has already been initialized, the title is applied to the platform window
   * immediately.
   *
   * @param title the new title
   */
  public void setTitle(String title) {
    this.title = title;
    if (initialized) {
      applyPlatformTitle(title);
    }
  }

  /**
   * Sets the window icon.
   *
   * <p>If called before {@link #initialize()}, the icon is stored and applied automatically once
   * the view is initialized.
   *
   * @param image the icon image, or {@code null} to remove the icon
   */
  public void setIcon(@Nullable ImageInfo image) {
    iconImage = image;
    if (initialized) {
      applyPlatformIcon(iconImage);
    }
  }

  /**
   * Returns the last-set window icon.
   *
   * @return the icon image, or {@code null} if no icon has been set
   */
  public @Nullable ImageInfo icon() {
    return iconImage;
  }

  /**
   * Returns the current cursor position in screen coordinates relative to the window.
   *
   * @return the cursor position as a vector
   */
  public Vector2 cursorPosition() {
    return new Vector2((float) cursorX, (float) cursorY);
  }

  /**
   * Sets the cursor position in screen coordinates.
   *
   * <p>If the view has already been initialized, the cursor is warped to the new position
   * immediately.
   *
   * @param position the new cursor position
   */
  public void setCursorPosition(Vector2 position) {
    cursorX = position.x();
    cursorY = position.y();
    if (initialized) {
      applyPlatformCursorPosition(cursorX, cursorY);
    }
  }

  /**
   * Sets a custom cursor image.
   *
   * <p>If called before {@link #initialize()}, the cursor image is stored and applied
   * automatically once the view is initialized.
   *
   * @param image   the cursor image, or {@code null} to restore the system default cursor
   * @param hotspot the hotspot offset in pixels relative to the image top-left corner
   */
  public void setCursor(@Nullable ImageInfo image, Vector2 hotspot) {
    cursorHotspot = hotspot;
    cursorImage = image;
    if (initialized) {
      applyPlatformCursor(cursorImage, (int) hotspot.x(), (int) hotspot.y());
    }
  }

  /**
   * Returns the last-set custom cursor image.
   *
   * @return the cursor image, or {@code null} if the system default cursor is active
   */
  public @Nullable ImageInfo cursorImage() {
    return cursorImage;
  }

  /**
   * Returns the last-set cursor hotspot.
   *
   * @return the hotspot offset
   */
  public Vector2 cursorHotspot() {
    return cursorHotspot;
  }

  /**
   * Returns whether the cursor is in relative (raw) motion mode.
   *
   * @return {@code true} if relative mode is active
   */
  public boolean isCursorRelativeMode() {
    return cursorRelativeMode;
  }

  /**
   * Sets whether the cursor is in relative (raw) motion mode.
   *
   * <p>In relative mode, cursor motion events report raw deltas rather than absolute screen
   * coordinates.
   *
   * @param enabled {@code true} to enable relative mode
   */
  public void setCursorRelativeMode(boolean enabled) {
    cursorRelativeMode = enabled;
    if (initialized) {
      applyPlatformCursorRelativeMode(enabled);
    }
  }

  /**
   * Returns the system clipboard text.
   *
   * <p>If the view is initialized, the clipboard content is read from the platform immediately.
   *
   * @return the clipboard text
   */
  public String clipboardText() {
    if (initialized) {
      clipboardText = readPlatformClipboard();
    }
    return clipboardText;
  }

  /**
   * Sets the system clipboard text.
   *
   * <p>If the view has already been initialized, the text is pushed to the platform clipboard
   * immediately.
   *
   * @param text the text to place on the clipboard
   */
  public void setClipboardText(String text) {
    clipboardText = text;
    if (initialized) {
      applyPlatformClipboard(text);
    }
  }

  /**
   * Returns whether the window has title bar and borders.
   *
   * @return {@code true} if decorated
   */
  public boolean isDecorated() {
    return decorated;
  }

  /**
   * Sets whether the window has title bar and borders.
   *
   * <p>If the view has already been initialized, the change is applied immediately.
   *
   * @param decorated {@code true} to show decorations
   */
  public void setDecorated(boolean decorated) {
    this.decorated = decorated;
    if (initialized) {
      applyPlatformDecorated(decorated);
    }
  }

  /**
   * Returns whether the window is maximized.
   *
   * @return {@code true} if maximized
   */
  public boolean isMaximized() {
    return maximized;
  }

  /**
   * Sets whether the window is maximized.
   *
   * <p>If the view has already been initialized, the change is applied immediately.
   *
   * @param maximized {@code true} to maximize the window
   */
  public void setMaximized(boolean maximized) {
    this.maximized = maximized;
    if (initialized) {
      applyPlatformMaximized(maximized);
    }
  }

  /**
   * Returns whether the window auto-iconifies on focus loss.
   *
   * @return {@code true} if auto-iconify is enabled
   */
  public boolean isAutoIconify() {
    return autoIconify;
  }

  /**
   * Sets whether the window auto-iconifies on focus loss.
   *
   * <p>If the view has already been initialized, the change is applied immediately.
   *
   * @param autoIconify {@code true} to enable auto-iconify
   */
  public void setAutoIconify(boolean autoIconify) {
    this.autoIconify = autoIconify;
    if (initialized) {
      applyPlatformAutoIconify(autoIconify);
    }
  }

  /**
   * Returns whether the window floats above other windows.
   *
   * @return {@code true} if floating
   */
  public boolean isFloating() {
    return floating;
  }

  /**
   * Sets whether the window floats above other windows.
   *
   * <p>If the view has already been initialized, the change is applied immediately.
   *
   * @param floating {@code true} to make the window float
   */
  public void setFloating(boolean floating) {
    this.floating = floating;
    if (initialized) {
      applyPlatformFloating(floating);
    }
  }

  /**
   * Returns whether the window currently has input focus.
   *
   * @return {@code true} if focused
   */
  public boolean isFocused() {
    return focused;
  }

  /**
   * Requests input focus for this window.
   *
   * <p>If the view has already been initialized, the request is forwarded to the platform
   * immediately.
   *
   * @param focused {@code true} to request focus
   */
  public void setFocused(boolean focused) {
    this.focused = focused;
    if (initialized) {
      applyPlatformFocused(focused);
    }
  }

  /**
   * Returns whether the window is visible.
   *
   * @return {@code true} if visible
   */
  public boolean isVisible() {
    return visible;
  }

  /**
   * Shows or hides the window.
   *
   * <p>If the view has already been initialized, the change is applied immediately.
   *
   * @param visible {@code true} to show the window
   */
  public void setVisible(boolean visible) {
    this.visible = visible;
    if (initialized) {
      applyPlatformVisible(visible);
    }
  }

  /**
   * Returns whether the window can be resized by the user.
   *
   * @return {@code true} if resizable
   */
  public boolean isResizable() {
    return resizable;
  }

  /**
   * Sets whether the window can be resized by the user.
   *
   * <p>If the view has already been initialized, the change is applied immediately.
   *
   * @param resizable {@code true} to allow user resizing
   */
  public void setResizable(boolean resizable) {
    this.resizable = resizable;
    if (initialized) {
      applyPlatformResizable(resizable);
    }
  }

  /**
   * Returns whether vertical sync is enabled.
   *
   * @return {@code true} if vsync is enabled
   */
  public boolean isVsync() {
    return vsync;
  }

  /**
   * Sets whether vertical sync is enabled.
   *
   * <p>If the view has already been initialized, the change is applied immediately.
   *
   * @param vsync {@code true} to enable vsync
   */
  public void setVsync(boolean vsync) {
    this.vsync = vsync;
    if (initialized) {
      applyPlatformVsync(vsync);
    }
  }

  /**
   * Returns whether graphics debug output is enabled.
   *
   * @return {@code true} if debug output is enabled
   */
  public boolean isDebug() {
    return debug;
  }

  /**
   * Sets whether to enable graphics debug output.
   *
   * <p>Must be set before {@link #initialize()}.
   *
   * @param debug {@code true} to enable debug output
   * @throws GraphicsException if the view has already been initialized
   */
  public void setDebug(boolean debug) {
    if (initialized) {
      throw new GraphicsException("Cannot set debug after view initialization");
    }
    this.debug = debug;
  }

  /**
   * Routes an input event through both the polling state and the event bus.
   *
   * <p>Called by backend implementations during {@link #onPollEvents()} after translating a
   * native event into an event record.
   *
   * @param event the input event to dispatch
   */
  protected void dispatchInputEvent(Event event) {
    // Update pollable state
    switch (event) {
      case KeyEvent e -> snapshot.applyKeyEvent(e);
      case MouseMoveEvent(double x1, double y1) -> snapshot.applyMouseMove(x1, y1);
      case MouseButtonEvent e -> snapshot.applyMouseButton(e);
      case ScrollEvent e -> snapshot.applyScroll(e.dx(), e.dy());
      case ResizeEvent(int width1, int height1) -> {
        width = width1;
        height = height1;
      }
      case MoveEvent(int x1, int y1) -> {
        x = x1;
        y = y1;
      }
      case FocusEvent(boolean focused1) -> focused = focused1;
      case MaximizeEvent(boolean maximized1) -> maximized = maximized1;
      default -> {
      }
    }
    // Fire all registered listeners
    eventBus.post(event);
  }

  /**
   * Creates the native view — a window, surface, or compositor layer — and configures it with the current view state.
   */
  protected abstract void onInitialize();

  /**
   * Destroys the native view and releases all platform resources.
   */
  protected abstract void onClose();

  /**
   * Polls native events from the platform, translates each into an {@link Event}, and dispatches it via
   * {@link #dispatchInputEvent(Event)}.
   */
  protected abstract void onPollEvents();

  /**
   * Applies a size change to the platform window.
   *
   * @param w the new width in pixels
   * @param h the new height in pixels
   */
  protected void applyPlatformSize(int w, int h) {
  }

  /**
   * Applies a position change to the platform window.
   *
   * @param x the new X position in screen coordinates
   * @param y the new Y position in screen coordinates
   */
  protected void applyPlatformPosition(int x, int y) {
  }

  /**
   * Applies a title change to the platform window.
   *
   * @param title the new title string
   */
  protected void applyPlatformTitle(String title) {
  }

  /**
   * Applies a window icon change to the platform window.
   *
   * @param image the icon image, or {@code null} to remove the icon
   */
  protected void applyPlatformIcon(@Nullable ImageInfo image) {
  }

  /**
   * Warps the platform cursor to the given position.
   *
   * @param x the X position in screen coordinates
   * @param y the Y position in screen coordinates
   */
  protected void applyPlatformCursorPosition(double x, double y) {
  }

  /**
   * Applies a custom cursor image to the platform cursor.
   *
   * @param image the cursor image, or {@code null} for the system default
   * @param hotX  the hotspot X offset in pixels
   * @param hotY  the hotspot Y offset in pixels
   */
  protected void applyPlatformCursor(@Nullable ImageInfo image, int hotX, int hotY) {
  }

  /**
   * Enables or disables relative cursor motion mode on the platform.
   *
   * @param enabled {@code true} to enable relative mode
   */
  protected void applyPlatformCursorRelativeMode(boolean enabled) {
  }

  /**
   * Pushes clipboard text to the platform clipboard.
   *
   * @param text the text to place on the clipboard
   */
  protected void applyPlatformClipboard(String text) {
  }

  /**
   * Reads the current platform clipboard content.
   *
   * @return the clipboard text, or an empty string if the clipboard is empty
   */
  protected String readPlatformClipboard() {
    return "";
  }

  /**
   * Applies a decoration change to the platform window.
   *
   * @param decorated {@code true} to show decorations
   */
  protected void applyPlatformDecorated(boolean decorated) {
  }

  /**
   * Applies a maximize change to the platform window.
   *
   * @param maximized {@code true} to maximize
   */
  protected void applyPlatformMaximized(boolean maximized) {
  }

  /**
   * Applies an auto-iconify change to the platform window.
   *
   * @param autoIconify {@code true} to enable auto-iconify on focus loss
   */
  protected void applyPlatformAutoIconify(boolean autoIconify) {
  }

  /**
   * Applies a floating change to the platform window.
   *
   * @param floating {@code true} to make the window float above others
   */
  protected void applyPlatformFloating(boolean floating) {
  }

  /**
   * Applies a focus request to the platform window.
   *
   * @param focused {@code true} to request focus
   */
  protected void applyPlatformFocused(boolean focused) {
  }

  /**
   * Applies a visibility change to the platform window.
   *
   * @param visible {@code true} to show the window
   */
  protected void applyPlatformVisible(boolean visible) {
  }

  /**
   * Applies a resizable change to the platform window.
   *
   * @param resizable {@code true} to allow user resizing
   */
  protected void applyPlatformResizable(boolean resizable) {
  }

  /**
   * Applies a vsync change to the platform window.
   *
   * @param vsync {@code true} to enable vsync
   */
  protected void applyPlatformVsync(boolean vsync) {
  }

  /**
   * Applies a debug output change to the platform window.
   *
   * @param debug {@code true} to enable debug output
   */
  protected void applyPlatformDebug(boolean debug) {
  }
}
