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

package io.viki.momentum.gfx.view;

import io.viki.momentum.gfx.io.ImageInfo;
import io.viki.momentum.math.Vector2;
import org.jspecify.annotations.Nullable;

/**
 * Adds desktop-window controls to the platform-independent {@link View} contract.
 *
 * <p>Desktop implementations must provide native operations for the controls declared here. This
 * class is not thread-safe and expects all calls on the rendering thread.
 */
public abstract class DesktopView extends View {
  protected int x;
  protected int y;
  protected String title = "Momentum Framework";
  protected boolean decorated = true;
  protected boolean resizable = true;
  protected boolean maximized;
  protected boolean autoIconify = true;
  protected boolean floating;
  protected boolean focused;
  protected boolean visible;
  protected @Nullable ImageInfo icon;
  protected double cursorX;
  protected double cursorY;
  protected @Nullable ImageInfo cursorImage;
  protected Vector2 cursorHotspot = Vector2.ZERO;
  protected boolean cursorRelativeMode;
  protected String clipboardText = "";

  /** Creates a desktop display with standard window defaults. */
  protected DesktopView() {
  }

  /** Applies a window-position change to the native desktop platform. */
  protected abstract void applyPlatformPosition(int x, int y);

  /** Applies a title change to the native desktop platform. */
  protected abstract void applyPlatformTitle(String title);

  /** Applies a custom icon change to the native desktop platform. */
  protected abstract void applyPlatformIcon(@Nullable ImageInfo image);

  /** Applies a cursor-position change to the native desktop platform. */
  protected abstract void applyPlatformCursorPosition(double x, double y);

  /** Applies a custom cursor image and hotspot to the native desktop platform. */
  protected abstract void applyPlatformCursor(@Nullable ImageInfo image, int hotX, int hotY);

  /** Applies relative cursor mode to the native desktop platform. */
  protected abstract void applyPlatformCursorRelativeMode(boolean enabled);

  /** Writes clipboard text to the native desktop platform. */
  protected abstract void applyPlatformClipboard(String text);

  /** Reads clipboard text from the native desktop platform. */
  protected abstract String readPlatformClipboard();

  /** Applies the window-decoration state to the native desktop platform. */
  protected abstract void applyPlatformDecorated(boolean decorated);

  /** Applies the window-maximize state to the native desktop platform. */
  protected abstract void applyPlatformMaximized(boolean maximized);

  /** Applies the auto-iconify state to the native desktop platform. */
  protected abstract void applyPlatformAutoIconify(boolean autoIconify);

  /** Applies the floating-window state to the native desktop platform. */
  protected abstract void applyPlatformFloating(boolean floating);

  /** Applies a focus request to the native desktop platform. */
  protected abstract void applyPlatformFocused(boolean focused);

  /** Applies the visibility state to the native desktop platform. */
  protected abstract void applyPlatformVisible(boolean visible);

  /** Applies the resizable state to the native desktop platform. */
  protected abstract void applyPlatformResizable(boolean resizable);

  /**
   * Returns the current window position in screen coordinates.
   *
   * @return window position
   */
  public final Vector2 getPosition() {
    return new Vector2(x, y);
  }

  /**
   * Returns the current cursor position in window coordinates.
   *
   * @return cursor position
   */
  public final Vector2 getCursorPosition() {
    return new Vector2((float) cursorX, (float) cursorY);
  }

  /**
   * Sets the window position in screen coordinates.
   *
   * @param position new window position
   */
  public final void setPosition(Vector2 position) {
    x = (int) position.x();
    y = (int) position.y();
    if (initialized) {
      applyPlatformPosition(x, y);
    }
  }

  /**
   * Returns the current window title.
   *
   * @return window title
   */
  public final String getTitle() {
    return title;
  }

  /**
   * Sets the window title.
   *
   * @param title new window title
   */
  public final void setTitle(String title) {
    this.title = title;
    if (initialized) {
      applyPlatformTitle(title);
    }
  }

  /**
   * Returns whether the window has decorations.
   *
   * @return {@code true} when the title bar and borders are shown
   */
  public final boolean isDecorated() {
    return decorated;
  }

  /**
   * Sets whether the window has decorations.
   *
   * @param decorated {@code true} to show the title bar and borders
   */
  public final void setDecorated(boolean decorated) {
    this.decorated = decorated;
    if (initialized) {
      applyPlatformDecorated(decorated);
    }
  }

  /**
   * Returns whether the user can resize the window.
   *
   * @return {@code true} when resizing is enabled
   */
  public final boolean isResizable() {
    return resizable;
  }

  /**
   * Sets whether the user can resize the window.
   *
   * @param resizable {@code true} to enable resizing
   */
  public final void setResizable(boolean resizable) {
    this.resizable = resizable;
    if (initialized) {
      applyPlatformResizable(resizable);
    }
  }

  /**
   * Returns whether the window is maximized.
   *
   * @return {@code true} when maximized
   */
  public final boolean isMaximized() {
    return maximized;
  }

  /**
   * Sets whether the window is maximized.
   *
   * @param maximized {@code true} to maximize the window
   */
  public final void setMaximized(boolean maximized) {
    this.maximized = maximized;
    if (initialized) {
      applyPlatformMaximized(maximized);
    }
  }

  /**
   * Returns whether focus loss automatically iconifies the window.
   *
   * @return {@code true} when auto-iconify is enabled
   */
  public final boolean isAutoIconify() {
    return autoIconify;
  }

  /**
   * Sets whether focus loss automatically iconifies the window.
   *
   * @param autoIconify {@code true} to enable auto-iconify
   */
  public final void setAutoIconify(boolean autoIconify) {
    this.autoIconify = autoIconify;
    if (initialized) {
      applyPlatformAutoIconify(autoIconify);
    }
  }

  /**
   * Returns whether the window floats above other windows.
   *
   * @return {@code true} when floating is enabled
   */
  public final boolean isFloating() {
    return floating;
  }

  /**
   * Sets whether the window floats above other windows.
   *
   * @param floating {@code true} to enable floating
   */
  public final void setFloating(boolean floating) {
    this.floating = floating;
    if (initialized) {
      applyPlatformFloating(floating);
    }
  }

  /**
   * Returns whether the window currently has focus.
   *
   * @return {@code true} when focused
   */
  public final boolean isFocused() {
    return focused;
  }

  /**
   * Requests whether the window should have focus.
   *
   * @param focused {@code true} to request focus
   */
  public final void setFocused(boolean focused) {
    this.focused = focused;
    if (initialized) {
      applyPlatformFocused(focused);
    }
  }

  /**
   * Returns whether the window is visible.
   *
   * @return {@code true} when visible
   */
  public final boolean isVisible() {
    return visible;
  }

  /**
   * Sets whether the window is visible.
   *
   * @param visible {@code true} to show the window
   */
  public final void setVisible(boolean visible) {
    this.visible = visible;
    if (initialized) {
      applyPlatformVisible(visible);
    }
  }

  /**
   * Returns the configured custom window icon.
   *
   * @return custom icon, or {@code null} when none is configured
   */
  public final @Nullable ImageInfo getIcon() {
    return icon;
  }

  /**
   * Sets the custom window icon.
   *
   * @param icon custom icon, or {@code null} to clear it
   */
  public final void setIcon(@Nullable ImageInfo icon) {
    this.icon = icon;
    if (initialized) {
      applyPlatformIcon(icon);
    }
  }

  /**
   * Sets the cursor position in window coordinates.
   *
   * @param position new cursor position
   */
  public final void setCursorPosition(Vector2 position) {
    cursorX = position.x();
    cursorY = position.y();
    if (initialized) {
      applyPlatformCursorPosition(cursorX, cursorY);
    }
  }

  /**
   * Returns the configured custom cursor image.
   *
   * @return custom cursor image, or {@code null} for the system cursor
   */
  public final @Nullable ImageInfo getCursorImage() {
    return cursorImage;
  }

  /**
   * Sets the custom cursor image.
   *
   * @param image custom cursor image, or {@code null} for the system cursor
   */
  public final void setCursorImage(@Nullable ImageInfo image) {
    cursorImage = image;
    if (initialized) {
      applyPlatformCursor(cursorImage, (int) cursorHotspot.x(), (int) cursorHotspot.y());
    }
  }

  /**
   * Returns the custom cursor hotspot.
   *
   * @return cursor hotspot
   */
  public final Vector2 getCursorHotspot() {
    return cursorHotspot;
  }

  /**
   * Sets the custom cursor hotspot.
   *
   * @param hotspot new cursor hotspot
   */
  public final void setCursorHotspot(Vector2 hotspot) {
    cursorHotspot = hotspot;
    if (initialized) {
      applyPlatformCursor(cursorImage, (int) cursorHotspot.x(), (int) cursorHotspot.y());
    }
  }

  /**
   * Returns whether relative cursor motion is enabled.
   *
   * @return {@code true} when relative motion is enabled
   */
  public final boolean isCursorRelativeMode() {
    return cursorRelativeMode;
  }

  /**
   * Sets whether relative cursor motion is enabled.
   *
   * @param enabled {@code true} to enable relative motion
   */
  public final void setCursorRelativeMode(boolean enabled) {
    cursorRelativeMode = enabled;
    if (initialized) {
      applyPlatformCursorRelativeMode(enabled);
    }
  }

  /**
   * Returns the current system clipboard text.
   *
   * @return clipboard text
   */
  public final String getClipboardText() {
    if (initialized) {
      clipboardText = readPlatformClipboard();
    }
    return clipboardText;
  }

  /**
   * Sets the system clipboard text.
   *
   * @param text new clipboard text
   */
  public final void setClipboardText(String text) {
    clipboardText = text;
    if (initialized) {
      applyPlatformClipboard(text);
    }
  }
}
