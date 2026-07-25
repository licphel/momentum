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

package net.fmhi.gfx.glfw;

import net.fmhi.gfx.GraphicsException;
import net.fmhi.gfx.View;
import net.fmhi.gfx.ViewInfo;
import net.fmhi.gfx.input.KeyAction;
import net.fmhi.gfx.input.KeyCode;
import net.fmhi.gfx.input.event.*;
import net.fmhi.gfx.io.ImageInfo;
import net.fmhi.util.InternalApi;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.*;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * GLFW window implementing the {@link View} abstract class with an OpenGL 3.3 Core Profile context.
 *
 * <p>Input arrives via GLFW callbacks that translate native events into {@link net.fmhi.event.Event}
 * records and dispatch them through {@link #dispatchInputEvent(net.fmhi.event.Event)}.
 */
@InternalApi
public final class GlfwView extends View {
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

  private static final KeyCode[] GLFW_KEY_MAP = new KeyCode[GLFW_KEY_LAST + 1];

  static {
    // Letters
    map(GLFW_KEY_A, KeyCode.A);
    map(GLFW_KEY_B, KeyCode.B);
    map(GLFW_KEY_C, KeyCode.C);
    map(GLFW_KEY_D, KeyCode.D);
    map(GLFW_KEY_E, KeyCode.E);
    map(GLFW_KEY_F, KeyCode.F);
    map(GLFW_KEY_G, KeyCode.G);
    map(GLFW_KEY_H, KeyCode.H);
    map(GLFW_KEY_I, KeyCode.I);
    map(GLFW_KEY_J, KeyCode.J);
    map(GLFW_KEY_K, KeyCode.K);
    map(GLFW_KEY_L, KeyCode.L);
    map(GLFW_KEY_M, KeyCode.M);
    map(GLFW_KEY_N, KeyCode.N);
    map(GLFW_KEY_O, KeyCode.O);
    map(GLFW_KEY_P, KeyCode.P);
    map(GLFW_KEY_Q, KeyCode.Q);
    map(GLFW_KEY_R, KeyCode.R);
    map(GLFW_KEY_S, KeyCode.S);
    map(GLFW_KEY_T, KeyCode.T);
    map(GLFW_KEY_U, KeyCode.U);
    map(GLFW_KEY_V, KeyCode.V);
    map(GLFW_KEY_W, KeyCode.W);
    map(GLFW_KEY_X, KeyCode.X);
    map(GLFW_KEY_Y, KeyCode.Y);
    map(GLFW_KEY_Z, KeyCode.Z);

    // Digits
    map(GLFW_KEY_0, KeyCode.DIGIT_0);
    map(GLFW_KEY_1, KeyCode.DIGIT_1);
    map(GLFW_KEY_2, KeyCode.DIGIT_2);
    map(GLFW_KEY_3, KeyCode.DIGIT_3);
    map(GLFW_KEY_4, KeyCode.DIGIT_4);
    map(GLFW_KEY_5, KeyCode.DIGIT_5);
    map(GLFW_KEY_6, KeyCode.DIGIT_6);
    map(GLFW_KEY_7, KeyCode.DIGIT_7);
    map(GLFW_KEY_8, KeyCode.DIGIT_8);
    map(GLFW_KEY_9, KeyCode.DIGIT_9);

    // Editing / navigation
    map(GLFW_KEY_SPACE, KeyCode.SPACE);
    map(GLFW_KEY_ENTER, KeyCode.ENTER);
    map(GLFW_KEY_ESCAPE, KeyCode.ESCAPE);
    map(GLFW_KEY_BACKSPACE, KeyCode.BACKSPACE);
    map(GLFW_KEY_TAB, KeyCode.TAB);
    map(GLFW_KEY_MINUS, KeyCode.MINUS);
    map(GLFW_KEY_EQUAL, KeyCode.EQUALS);
    map(GLFW_KEY_LEFT_BRACKET, KeyCode.LEFT_BRACKET);
    map(GLFW_KEY_RIGHT_BRACKET, KeyCode.RIGHT_BRACKET);
    map(GLFW_KEY_BACKSLASH, KeyCode.BACKSLASH);
    map(GLFW_KEY_SEMICOLON, KeyCode.SEMICOLON);
    map(GLFW_KEY_APOSTROPHE, KeyCode.APOSTROPHE);
    map(GLFW_KEY_GRAVE_ACCENT, KeyCode.GRAVE_ACCENT);
    map(GLFW_KEY_COMMA, KeyCode.COMMA);
    map(GLFW_KEY_PERIOD, KeyCode.PERIOD);
    map(GLFW_KEY_SLASH, KeyCode.SLASH);
    map(GLFW_KEY_CAPS_LOCK, KeyCode.CAPS_LOCK);

    // Function keys
    map(GLFW_KEY_F1, KeyCode.F1);
    map(GLFW_KEY_F2, KeyCode.F2);
    map(GLFW_KEY_F3, KeyCode.F3);
    map(GLFW_KEY_F4, KeyCode.F4);
    map(GLFW_KEY_F5, KeyCode.F5);
    map(GLFW_KEY_F6, KeyCode.F6);
    map(GLFW_KEY_F7, KeyCode.F7);
    map(GLFW_KEY_F8, KeyCode.F8);
    map(GLFW_KEY_F9, KeyCode.F9);
    map(GLFW_KEY_F10, KeyCode.F10);
    map(GLFW_KEY_F11, KeyCode.F11);
    map(GLFW_KEY_F12, KeyCode.F12);
    map(GLFW_KEY_F13, KeyCode.F13);
    map(GLFW_KEY_F14, KeyCode.F14);
    map(GLFW_KEY_F15, KeyCode.F15);
    map(GLFW_KEY_F16, KeyCode.F16);
    map(GLFW_KEY_F17, KeyCode.F17);
    map(GLFW_KEY_F18, KeyCode.F18);
    map(GLFW_KEY_F19, KeyCode.F19);
    map(GLFW_KEY_F20, KeyCode.F20);
    map(GLFW_KEY_F21, KeyCode.F21);
    map(GLFW_KEY_F22, KeyCode.F22);
    map(GLFW_KEY_F23, KeyCode.F23);
    map(GLFW_KEY_F24, KeyCode.F24);
    map(GLFW_KEY_F25, KeyCode.F25);

    // System keys
    map(GLFW_KEY_PRINT_SCREEN, KeyCode.PRINT_SCREEN);
    map(GLFW_KEY_SCROLL_LOCK, KeyCode.SCROLL_LOCK);
    map(GLFW_KEY_PAUSE, KeyCode.PAUSE);
    map(GLFW_KEY_INSERT, KeyCode.INSERT);
    map(GLFW_KEY_HOME, KeyCode.HOME);
    map(GLFW_KEY_PAGE_UP, KeyCode.PAGE_UP);
    map(GLFW_KEY_DELETE, KeyCode.DELETE);
    map(GLFW_KEY_END, KeyCode.END);
    map(GLFW_KEY_PAGE_DOWN, KeyCode.PAGE_DOWN);

    // Arrow keys
    map(GLFW_KEY_RIGHT, KeyCode.RIGHT);
    map(GLFW_KEY_LEFT, KeyCode.LEFT);
    map(GLFW_KEY_DOWN, KeyCode.DOWN);
    map(GLFW_KEY_UP, KeyCode.UP);

    // Num lock and keypad
    map(GLFW_KEY_NUM_LOCK, KeyCode.NUM_LOCK);
    map(GLFW_KEY_KP_DIVIDE, KeyCode.KP_DIVIDE);
    map(GLFW_KEY_KP_MULTIPLY, KeyCode.KP_MULTIPLY);
    map(GLFW_KEY_KP_SUBTRACT, KeyCode.KP_SUBTRACT);
    map(GLFW_KEY_KP_ADD, KeyCode.KP_ADD);
    map(GLFW_KEY_KP_ENTER, KeyCode.KP_ENTER);
    map(GLFW_KEY_KP_0, KeyCode.KP_0);
    map(GLFW_KEY_KP_1, KeyCode.KP_1);
    map(GLFW_KEY_KP_2, KeyCode.KP_2);
    map(GLFW_KEY_KP_3, KeyCode.KP_3);
    map(GLFW_KEY_KP_4, KeyCode.KP_4);
    map(GLFW_KEY_KP_5, KeyCode.KP_5);
    map(GLFW_KEY_KP_6, KeyCode.KP_6);
    map(GLFW_KEY_KP_7, KeyCode.KP_7);
    map(GLFW_KEY_KP_8, KeyCode.KP_8);
    map(GLFW_KEY_KP_9, KeyCode.KP_9);
    map(GLFW_KEY_KP_DECIMAL, KeyCode.KP_DECIMAL);
    map(GLFW_KEY_KP_EQUAL, KeyCode.KP_EQUALS);

    // Modifiers
    map(GLFW_KEY_LEFT_SHIFT, KeyCode.LEFT_SHIFT);
    map(GLFW_KEY_RIGHT_SHIFT, KeyCode.RIGHT_SHIFT);
    map(GLFW_KEY_LEFT_CONTROL, KeyCode.LEFT_CONTROL);
    map(GLFW_KEY_RIGHT_CONTROL, KeyCode.RIGHT_CONTROL);
    map(GLFW_KEY_LEFT_ALT, KeyCode.LEFT_ALT);
    map(GLFW_KEY_RIGHT_ALT, KeyCode.RIGHT_ALT);
    map(GLFW_KEY_LEFT_SUPER, KeyCode.LEFT_SUPER);
    map(GLFW_KEY_RIGHT_SUPER, KeyCode.RIGHT_SUPER);

    // Menu
    map(GLFW_KEY_MENU, KeyCode.MENU);
  }

  /** Holds strong references to GLFW callback objects to prevent GC. */
  private final Object[] callbacks = new Object[CB_COUNT];
  private long handle;
  private long cursorHandle;
  private boolean closeRequested;

  private static void map(int glfwKey, KeyCode code) {
    if (glfwKey >= 0 && glfwKey < GLFW_KEY_MAP.length) {
      GLFW_KEY_MAP[glfwKey] = code;
    }
  }

  /** Converts a GLFW key code to a platform-agnostic {@link KeyCode}. Returns {@code null} if unmapped. */
  static @Nullable KeyCode glfwToKeyCode(int glfwKey) {
    return (glfwKey >= 0 && glfwKey < GLFW_KEY_MAP.length) ? GLFW_KEY_MAP[glfwKey] : null;
  }

  /** Converts a GLFW action to a {@link KeyAction}. */
  static KeyAction glfwToKeyAction(int action) {
    return switch (action) {
      case GLFW_PRESS -> KeyAction.PRESS;
      case GLFW_REPEAT -> KeyAction.REPEAT;
      default -> KeyAction.RELEASE;
    };
  }

  /**
   * Terminates GLFW globally. Should be called once when the application exits.
   */
  public static void terminate() {
    glfwTerminate();
    GLFWErrorCallback cb = glfwSetErrorCallback(null);
    if (cb != null) {
      cb.free();
    }
  }

  @Override
  public ViewInfo info() {
    return new ViewInfo("GLFW (LWJGL 3)");
  }

  @Override
  public Object procAddress() {
    long h = handle;
    return (Runnable) () -> {
      glfwMakeContextCurrent(h);
      applyPlatformVsync(vsync);
      glfwShowWindow(h);
    };
  }

  @Override
  public boolean shouldClose() {
    return closeRequested || (handle != NULL && glfwWindowShouldClose(handle));
  }

  @Override
  public void present() {
    if (handle != NULL) {
      glfwSwapBuffers(handle);
    }
  }

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
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
    glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

    handle = glfwCreateWindow(width, height, title, NULL, NULL);

    if (handle == NULL) {
      glfwTerminate();
      throw new RuntimeException("Failed to create GLFW window");
    }

    // Center on primary monitor
    GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
    if (vidMode != null) {
      x = (vidMode.width() - width) / 2;
      y = (vidMode.height() - height) / 2;
      glfwSetWindowPos(handle, x, y);
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
    applyPlatformDebug(debug);
    applyPlatformMaximized(maximized);

    if (iconImage != null) {
      applyPlatformIcon(iconImage);
    }
    if (cursorImage != null) {
      applyPlatformCursor(cursorImage, (int) cursorHotspot.x(), (int) cursorHotspot.y());
    }
  }

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
    if (handle != NULL) {
      glfwSetWindowAttrib(handle, GLFW_FOCUSED, focused ? GLFW_TRUE : GLFW_FALSE);
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
    if (handle != NULL) {
      glfwSwapInterval(vsync ? 1 : 0);
    }
  }

  @Override
  protected void applyPlatformDebug(boolean debug) {
    if (handle != NULL && debug) {
      glfwSetWindowAttrib(handle, GLFW_CONTEXT_DEBUG, GLFW_TRUE);
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
    callbacks[CB_FRAMEBUFFER_SIZE] =
        (GLFWFramebufferSizeCallbackI) (win, w, h) -> dispatchInputEvent(new ResizeEvent(w, h));
    glfwSetFramebufferSizeCallback(handle, (GLFWFramebufferSizeCallbackI) callbacks[CB_FRAMEBUFFER_SIZE]);

    // Key
    callbacks[CB_KEY] = (GLFWKeyCallbackI) (win, key, scancode, action, mods) -> {
      KeyCode code = glfwToKeyCode(key);
      if (code != null) {
        dispatchInputEvent(new KeyEvent(code, glfwToKeyAction(action), mods));
      }
    };
    glfwSetKeyCallback(handle, (GLFWKeyCallbackI) callbacks[CB_KEY]);

    // Char
    callbacks[CB_CHAR] = (GLFWCharCallbackI) (win, codepoint) -> dispatchInputEvent(new CharEvent(codepoint));
    glfwSetCharCallback(handle, (GLFWCharCallbackI) callbacks[CB_CHAR]);

    // Cursor position
    callbacks[CB_CURSOR_POS] = (GLFWCursorPosCallbackI) (win, cx, cy) -> dispatchInputEvent(new MouseMoveEvent(cx, cy));
    glfwSetCursorPosCallback(handle, (GLFWCursorPosCallbackI) callbacks[CB_CURSOR_POS]);

    // Mouse button
    callbacks[CB_MOUSE_BUTTON] = (GLFWMouseButtonCallbackI) (win, button, action, mods) -> {
      KeyCode mb = KeyCode.fromMouseId(button);
      if (mb != null) {
        dispatchInputEvent(new MouseButtonEvent(mb, glfwToKeyAction(action), cursorX, cursorY, mods));
      }
    };
    glfwSetMouseButtonCallback(handle, (GLFWMouseButtonCallbackI) callbacks[CB_MOUSE_BUTTON]);

    // Scroll
    callbacks[CB_SCROLL] =
        (GLFWScrollCallbackI) (win, xOffset, yOffset) -> dispatchInputEvent(new ScrollEvent(xOffset, yOffset, cursorX
            , cursorY));
    glfwSetScrollCallback(handle, (GLFWScrollCallbackI) callbacks[CB_SCROLL]);

    // Cursor enter
    callbacks[CB_CURSOR_ENTER] =
        (GLFWCursorEnterCallbackI) (win, entered) -> dispatchInputEvent(new CursorEnterEvent(entered));
    glfwSetCursorEnterCallback(handle, (GLFWCursorEnterCallbackI) callbacks[CB_CURSOR_ENTER]);

    // Window close
    callbacks[CB_WINDOW_CLOSE] = (GLFWWindowCloseCallbackI) win -> closeRequested = true;
    glfwSetWindowCloseCallback(handle, (GLFWWindowCloseCallbackI) callbacks[CB_WINDOW_CLOSE]);

    // Window focus
    callbacks[CB_WINDOW_FOCUS] =
        (GLFWWindowFocusCallbackI) (win, focused) -> dispatchInputEvent(new FocusEvent(focused));
    glfwSetWindowFocusCallback(handle, (GLFWWindowFocusCallbackI) callbacks[CB_WINDOW_FOCUS]);

    // Window iconify
    callbacks[CB_WINDOW_ICONIFY] =
        (GLFWWindowIconifyCallbackI) (win, iconified) -> dispatchInputEvent(new IconifyEvent(iconified));
    glfwSetWindowIconifyCallback(handle, (GLFWWindowIconifyCallbackI) callbacks[CB_WINDOW_ICONIFY]);

    // Window maximize
    callbacks[CB_WINDOW_MAXIMIZE] =
        (GLFWWindowMaximizeCallbackI) (win, maximized) -> dispatchInputEvent(new MaximizeEvent(maximized));
    glfwSetWindowMaximizeCallback(handle, (GLFWWindowMaximizeCallbackI) callbacks[CB_WINDOW_MAXIMIZE]);

    // Window move
    callbacks[CB_WINDOW_MOVE] = (GLFWWindowPosCallbackI) (win, xpos, ypos) -> dispatchInputEvent(new MoveEvent(xpos,
        ypos));
    glfwSetWindowPosCallback(handle, (GLFWWindowPosCallbackI) callbacks[CB_WINDOW_MOVE]);

    // File drop
    callbacks[CB_DROP] = (GLFWDropCallbackI) (win, count, names) -> {
      String[] paths = new String[count];
      for (int i = 0; i < count; i++) {
        long pointer = MemoryUtil.memGetAddress(names + (long) i * Long.BYTES);
        paths[i] = MemoryUtil.memUTF8(pointer);
      }
      dispatchInputEvent(new FileDropEvent(paths));
    };
    glfwSetDropCallback(handle, (GLFWDropCallbackI) callbacks[CB_DROP]);
  }
}
