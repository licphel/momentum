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

package io.viki.momentum.gfx.glfw;

import io.viki.momentum.event.Event;
import io.viki.momentum.gfx.GraphicsException;
import io.viki.momentum.gfx.input.KeyCode;
import io.viki.momentum.gfx.input.event.*;
import io.viki.momentum.gfx.io.ImageInfo;
import io.viki.momentum.gfx.view.DesktopView;
import io.viki.momentum.math.Vector2;
import io.viki.momentum.internal.InternalApi;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Provides a desktop display backed by a GLFW window and an OpenGL 3.3 core context.
 *
 * <p>Native callbacks are translated into platform-neutral {@link Event} values before they are
 * published to the display event bus. Instances are not thread-safe.
 */
@InternalApi
public final class GlfwDesktopView extends DesktopView {
  private static final int CB_FRAMEBUFFER_SIZE = 0;
  private static final int CB_KEY = 1;
  private static final int CB_CURSOR_POS = 2;
  private static final int CB_MOUSE_BUTTON = 3;
  private static final int CB_SCROLL = 4;
  private static final int CB_DROP = 5;
  private static final int CB_CHAR = 6;
  private static final int CB_CURSOR_ENTER = 7;
  private static final int CB_WINDOW_FOCUS = 8;
  private static final int CB_WINDOW_ICONIFY = 9;
  private static final int CB_WINDOW_MAXIMIZE = 10;
  private static final int CB_WINDOW_MOVE = 11;
  private static final int CB_WINDOW_CLOSE = 12;
  private static final int CB_COUNT = 16;

  /** Holds strong references to GLFW callback objects to prevent GC. */
  private final Object[] callbacks = new Object[CB_COUNT];
  private long handle;
  private boolean hasContext;
  private long cursorHandle;
  private boolean closeRequested;

  /** Creates an uninitialized GLFW display. */
  public GlfwDesktopView() {
  }

  /** Terminates the process-wide GLFW runtime and releases its error callback. */
  public static void terminate() {
    glfwTerminate();
    GLFWErrorCallback cb = glfwSetErrorCallback(null);
    if (cb != null) {
      cb.free();
    }
  }

  /**
   * Returns a deferred operation that makes this window's OpenGL context current.
   *
   * @return context-binding operation for the graphics device
   */
  @Override
  public Object procAddress() {
    long h = handle;
    return (Runnable) () -> {
      glfwMakeContextCurrent(h);
      glfwShowWindow(h);

      hasContext = true;
      applyPlatformVsync(vsync);
    };
  }

  /**
   * Returns whether the GLFW window has received a close request.
   *
   * @return {@code true} when the window should close
   */
  @Override
  public boolean shouldClose() {
    return closeRequested || (handle != NULL && glfwWindowShouldClose(handle));
  }

  /**
   * Swaps the GLFW front and back buffers.
   */
  @Override
  public void present() {
    if (handle != NULL) {
      glfwSwapBuffers(handle);
    }
  }

  @Override
  public Vector2 getInputSize() {
    if (handle == NULL) {
      return super.getInputSize();
    }

    try (MemoryStack stack = MemoryStack.stackPush()) {
      IntBuffer windowWidth = stack.mallocInt(1);
      IntBuffer windowHeight = stack.mallocInt(1);
      glfwGetWindowSize(handle, windowWidth, windowHeight);
      return new Vector2(windowWidth.get(0), windowHeight.get(0));
    }
  }

  /** Creates the GLFW window, context callbacks, and configured desktop resources. */
  @Override
  protected void onInitialize() {
    GLFWErrorCallback.createPrint(System.err).set();

    if (!glfwInit()) {
      throw new IllegalStateException("Failed to initialize GLFW");
    }

    glfwDefaultWindowHints();
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
    glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, debug ? GLFW_TRUE : GLFW_FALSE);
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
    glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
    glfwWindowHint(GLFW_REFRESH_RATE, 60); // Fix: on Vsync mode, sometimes fps drops to ~30

    handle = glfwCreateWindow(width, height, title, NULL, NULL);

    if (handle == NULL) {
      glfwTerminate();
      throw new RuntimeException("Failed to create GLFW window");
    }

    // Center on primary monitor
    GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
    if (vidMode != null) {
      int centeredX = (vidMode.width() - width) / 2;
      int centeredY = (vidMode.height() - height) / 2;
      x = centeredX;
      y = centeredY;
      glfwSetWindowPos(handle, centeredX, centeredY);
    }

    setupCallbacks();

    // Apply any state configured before init
    if (!title.isEmpty()) {
      glfwSetWindowTitle(handle, title);
    }

    applyPlatformSize(width, height);
    applyPlatformDecorated(decorated);
    applyPlatformResizable(resizable);
    applyPlatformAutoIconify(autoIconify);
    applyPlatformFloating(floating);
    applyPlatformVisible(visible);
    applyPlatformMaximized(maximized);

    if (icon != null) {
      applyPlatformIcon(icon);
    }
    if (cursorImage != null) {
      applyPlatformCursor(cursorImage, (int) cursorHotspot.x(), (int) cursorHotspot.y());
    }
  }

  /** Destroys the GLFW cursor, callbacks, and native window. */
  @Override
  protected void onClose() {
    if (cursorHandle != NULL) {
      glfwDestroyCursor(cursorHandle);
      cursorHandle = NULL;
    }
    if (handle != NULL) {
      glfwFreeCallbacks(handle);
      glfwDestroyWindow(handle);
      handle = NULL;
    }
  }

  /** Polls pending GLFW events. */
  @Override
  protected void onPollEvents() {
    glfwPollEvents();
  }

  @Override
  protected void applyPlatformSize(int w, int h) {
    if (handle != NULL) {
      glfwSetWindowSize(handle, w, h);
    }
  }

  @Override
  protected void applyPlatformPosition(int x, int y) {
    if (handle != NULL) {
      glfwSetWindowPos(handle, x, y);
    }
  }

  @Override
  protected void applyPlatformTitle(String title) {
    if (handle != NULL) {
      glfwSetWindowTitle(handle, title);
    }
  }

  @Override
  protected void applyPlatformIcon(@Nullable ImageInfo image) {
    if (handle != NULL) {
      if (image == null) {
        glfwSetWindowIcon(handle, null);
        return;
      }
      ByteBuffer buf = MemoryUtil.memAlloc(image.pixels().length);
      try {
        buf.put(image.pixels()).flip();
        try (GLFWImage.Buffer gBuf = GLFWImage.malloc(1)) {
          gBuf.width(image.width()).height(image.height()).pixels(buf);
          glfwSetWindowIcon(handle, gBuf);
        }
      } finally {
        MemoryUtil.memFree(buf);
      }
    }
  }

  @Override
  protected void applyPlatformCursorPosition(double x, double y) {
    if (handle != NULL) {
      glfwSetCursorPos(handle, x, y);
    }
  }

  @Override
  protected void applyPlatformCursor(@Nullable ImageInfo image, int hotX, int hotY) {
    if (handle == NULL) {
      return;
    }

    if (image == null) {
      glfwSetCursor(handle, NULL);
      return;
    }

    ByteBuffer buf = MemoryUtil.memAlloc(image.pixels().length);
    try {
      buf.put(image.pixels()).flip();
      GLFWImage gImg = GLFWImage.create().set(image.width(), image.height(), buf);
      long newCursor = glfwCreateCursor(gImg, hotX, hotY);
      if (newCursor == NULL) {
        throw new GraphicsException("Failed to create GLFW cursor");
      }
      glfwSetCursor(handle, newCursor);
      if (cursorHandle != NULL) {
        glfwDestroyCursor(cursorHandle);
      }
      cursorHandle = newCursor;
    } finally {
      MemoryUtil.memFree(buf);
    }
  }

  @Override
  protected void applyPlatformCursorRelativeMode(boolean enabled) {
    if (handle != NULL) {
      glfwSetInputMode(handle, GLFW_CURSOR, enabled ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
    }
  }

  @Override
  protected void applyPlatformClipboard(String text) {
    if (handle != NULL) {
      glfwSetClipboardString(handle, text);
    }
  }

  @Override
  protected String readPlatformClipboard() {
    if (handle != NULL) {
      String t = glfwGetClipboardString(handle);
      return t != null ? t : "";
    }
    return "";
  }

  @Override
  protected void applyPlatformDecorated(boolean decorated) {
    if (handle != NULL) {
      glfwSetWindowAttrib(handle, GLFW_DECORATED, decorated ? GLFW_TRUE : GLFW_FALSE);
    }
  }

  @Override
  protected void applyPlatformMaximized(boolean maximized) {
    if (handle != NULL) {
      if (maximized) {
        glfwMaximizeWindow(handle);
      } else {
        glfwRestoreWindow(handle);
      }
    }
  }

  @Override
  protected void applyPlatformAutoIconify(boolean autoIconify) {
    if (handle != NULL) {
      glfwSetWindowAttrib(handle, GLFW_AUTO_ICONIFY, autoIconify ? GLFW_TRUE : GLFW_FALSE);
    }
  }

  @Override
  protected void applyPlatformFloating(boolean floating) {
    if (handle != NULL) {
      glfwSetWindowAttrib(handle, GLFW_FLOATING, floating ? GLFW_TRUE : GLFW_FALSE);
    }
  }

  @Override
  protected void applyPlatformFocused(boolean focused) {
    if (handle != NULL && focused) {
      glfwFocusWindow(handle);
    }
  }

  @Override
  protected void applyPlatformVisible(boolean visible) {
    if (handle != NULL) {
      if (visible) {
        glfwShowWindow(handle);
      } else {
        glfwHideWindow(handle);
      }
    }
  }

  @Override
  protected void applyPlatformResizable(boolean resizable) {
    if (handle != NULL) {
      glfwSetWindowAttrib(handle, GLFW_RESIZABLE, resizable ? GLFW_TRUE : GLFW_FALSE);
    }
  }

  @Override
  protected void applyPlatformVsync(boolean vsync) {
    if (handle != NULL && hasContext) {
      glfwSwapInterval(vsync ? 1 : 0);
    }
  }

  /** Returns the native GLFW window handle. */
  long handle() {
    return handle;
  }

  /** Swaps the front and back buffers. Called by the swapchain hook. */
  void swapBuffers() {
    if (handle != NULL) {
      glfwSwapBuffers(handle);
    }
  }

  private void setupCallbacks() {
    // Framebuffer size
    callbacks[CB_FRAMEBUFFER_SIZE] = (GLFWFramebufferSizeCallbackI) (win, w, h) -> {
      width = w;
      height = h;
      Event event = new ResizeEvent(w, h);
      eventBus().post(event);
    };
    glfwSetFramebufferSizeCallback(handle, (GLFWFramebufferSizeCallbackI) callbacks[CB_FRAMEBUFFER_SIZE]);

    // Key
    callbacks[CB_KEY] = (GLFWKeyCallbackI) (win, key, scancode, action, mods) -> {
      KeyCode code = GlfwKeyMapper.kc(key);
      if (code != null) {
        KeyEvent event = new KeyEvent(code, GlfwKeyMapper.ac(action), mods);
        snapshot().applyKeyEvent(event);
        eventBus().post((Event) event);
      }
    };
    glfwSetKeyCallback(handle, (GLFWKeyCallbackI) callbacks[CB_KEY]);

    // Char
    callbacks[CB_CHAR] = (GLFWCharCallbackI) (win, codepoint) -> {
      Event event = new CharEvent(codepoint);
      eventBus().post(event);
    };
    glfwSetCharCallback(handle, (GLFWCharCallbackI) callbacks[CB_CHAR]);

    // Cursor position
    callbacks[CB_CURSOR_POS] = (GLFWCursorPosCallbackI) (win, cx, cy) -> {
      cursorX = cx;
      cursorY = cy;
      snapshot().applyMouseMove(cx, cy);
      Event event = new MouseMoveEvent(cx, cy);
      eventBus().post(event);
    };
    glfwSetCursorPosCallback(handle, (GLFWCursorPosCallbackI) callbacks[CB_CURSOR_POS]);

    // Mouse button
    callbacks[CB_MOUSE_BUTTON] = (GLFWMouseButtonCallbackI) (win, button, action, mods) -> {
      KeyCode mb = KeyCode.fromMouseId(button);
      if (mb != null) {
        MouseButtonEvent event = new MouseButtonEvent(mb, GlfwKeyMapper.ac(action), cursorX, cursorY, mods);
        snapshot().applyMouseButton(event);
        eventBus().post((Event) event);
      }
    };
    glfwSetMouseButtonCallback(handle, (GLFWMouseButtonCallbackI) callbacks[CB_MOUSE_BUTTON]);

    // Scroll
    callbacks[CB_SCROLL] = (GLFWScrollCallbackI) (win, xOffset, yOffset) -> {
      ScrollEvent event = new ScrollEvent(xOffset, yOffset, cursorX, cursorY);
      snapshot().applyScroll(xOffset, yOffset);
      eventBus().post((Event) event);
    };
    glfwSetScrollCallback(handle, (GLFWScrollCallbackI) callbacks[CB_SCROLL]);

    // Cursor enter
    callbacks[CB_CURSOR_ENTER] =
        (GLFWCursorEnterCallbackI) (win, entered) -> {
          Event event = new CursorEnterEvent(entered);
          eventBus().post(event);
        };
    glfwSetCursorEnterCallback(handle, (GLFWCursorEnterCallbackI) callbacks[CB_CURSOR_ENTER]);

    // Window close
    callbacks[CB_WINDOW_CLOSE] = (GLFWWindowCloseCallbackI) win -> closeRequested = true;
    glfwSetWindowCloseCallback(handle, (GLFWWindowCloseCallbackI) callbacks[CB_WINDOW_CLOSE]);

    // Window focus
    callbacks[CB_WINDOW_FOCUS] = (GLFWWindowFocusCallbackI) (win, focused) -> {
      this.focused = focused;
      Event event = new FocusEvent(focused);
      eventBus().post(event);
    };
    glfwSetWindowFocusCallback(handle, (GLFWWindowFocusCallbackI) callbacks[CB_WINDOW_FOCUS]);

    // Window iconify
    callbacks[CB_WINDOW_ICONIFY] =
        (GLFWWindowIconifyCallbackI) (win, iconified) -> {
          Event event = new IconifyEvent(iconified);
          eventBus().post(event);
        };
    glfwSetWindowIconifyCallback(handle, (GLFWWindowIconifyCallbackI) callbacks[CB_WINDOW_ICONIFY]);

    // Window maximize
    callbacks[CB_WINDOW_MAXIMIZE] = (GLFWWindowMaximizeCallbackI) (win, maximized) -> {
      this.maximized = maximized;
      Event event = new MaximizeEvent(maximized);
      eventBus().post(event);
    };
    glfwSetWindowMaximizeCallback(handle, (GLFWWindowMaximizeCallbackI) callbacks[CB_WINDOW_MAXIMIZE]);

    // Window move
    callbacks[CB_WINDOW_MOVE] = (GLFWWindowPosCallbackI) (win, xpos, ypos) -> {
      x = xpos;
      y = ypos;
      Event event = new MoveEvent(xpos, ypos);
      eventBus().post(event);
    };
    glfwSetWindowPosCallback(handle, (GLFWWindowPosCallbackI) callbacks[CB_WINDOW_MOVE]);

    // File drop
    callbacks[CB_DROP] = (GLFWDropCallbackI) (win, count, names) -> {
      String[] paths = new String[count];
      for (int i = 0; i < count; i++) {
        long pointer = MemoryUtil.memGetAddress(names + (long) i * Long.BYTES);
        paths[i] = MemoryUtil.memUTF8(pointer);
      }
      Event event = new FileDropEvent(paths);
      eventBus().post(event);
    };
    glfwSetDropCallback(handle, (GLFWDropCallbackI) callbacks[CB_DROP]);
  }
}
