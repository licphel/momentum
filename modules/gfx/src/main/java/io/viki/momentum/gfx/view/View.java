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

import io.viki.momentum.event.EventBus;
import io.viki.momentum.gfx.Device;
import io.viki.momentum.input.InputSnapshot;
import io.viki.momentum.math.Vector2;

/**
 * Provides lifecycle, input event publication, and common size controls for a display surface.
 *
 * <p>Implementations must invoke lifecycle methods from one rendering thread. This class is not
 * thread-safe.
 */
public abstract class View implements AutoCloseable {
  /** Default display width in physical pixels. */
  public static final int DEFAULT_WIDTH = 800;
  /** Default display height in physical pixels. */
  public static final int DEFAULT_HEIGHT = 450;

  private final EventBus eventBus = new EventBus();
  private final InputSnapshot snapshot = new InputSnapshot();
  protected int width = DEFAULT_WIDTH;
  protected int height = DEFAULT_HEIGHT;
  protected boolean vsync = true;
  protected boolean initialized;
  protected boolean debug;

  /** Creates a display with the default size of 800 by 450 physical pixels. */
  protected View() {
  }

  /**
   * Returns the event bus used by this display.
   *
   * @return display event bus
   */
  public final EventBus eventBus() {
    return eventBus;
  }

  /**
   * Returns the per-frame input snapshot for this display.
   *
   * @return mutable input snapshot owned by the display
   */
  public final InputSnapshot snapshot() {
    return snapshot;
  }

  /**
   * Returns the current display width in physical pixels.
   *
   * @return display width
   */
  public final int getWidth() {
    return width;
  }

  /**
   * Sets the display width in physical pixels.
   *
   * @param width new display width
   */
  public final void setWidth(int width) {
    this.width = width;
    if (initialized) {
      applyPlatformSize(width, height);
    }
  }

  /**
   * Returns the current display height in physical pixels.
   *
   * @return display height
   */
  public final int getHeight() {
    return height;
  }

  /**
   * Sets the display height in physical pixels.
   *
   * @param height new display height
   */
  public final void setHeight(int height) {
    this.height = height;
    if (initialized) {
      applyPlatformSize(width, height);
    }
  }

  /**
   * Returns the current display size in physical pixels.
   *
   * @return width-and-height vector
   */
  public final Vector2 getSize() {
    return new Vector2(width, height);
  }

  /**
   * Sets the display size in physical pixels.
   *
   * @param size new width-and-height vector
   */
  public final void setSize(Vector2 size) {
    width = (int) size.x();
    height = (int) size.y();
    if (initialized) {
      applyPlatformSize(width, height);
    }
  }

  /**
   * Returns the coordinate-space size used by positional input events.
   *
   * @return input coordinate-space size
   */
  public Vector2 getInputSize() {
    return getSize();
  }

  /**
   * Returns whether frame presentation waits for the display refresh interval.
   *
   * @return {@code true} when vertical synchronization is enabled
   */
  public final boolean isVsync() {
    return vsync;
  }

  /**
   * Sets whether frame presentation waits for the display refresh interval.
   *
   * @param vsync {@code true} to enable vertical synchronization
   */
  public final void setVsync(boolean vsync) {
    this.vsync = vsync;
    if (initialized) {
      applyPlatformVsync(vsync);
    }
  }

  /**
   * Sets whether the OpenGL context requests debug output.
   *
   * @param debug {@code true} to request a debug context
   * @throws IllegalStateException if the display has already been initialized
   */
  public void setDebug(boolean debug) {
    if (initialized) {
      throw new IllegalStateException("GLFW debug context must be configured before display initialization");
    }
    this.debug = debug;
  }

  /**
   * Returns whether the OpenGL context requests debug output.
   *
   * @return {@code true} when debug output was requested
   */
  public boolean isDebug() {
    return debug;
  }

  /**
   * Returns an opaque native handle or context binding for the graphics device.
   *
   * @return backend-specific native binding
   */
  public abstract Object procAddress();

  /**
   * Creates the native display and applies configured values.
   *
   * @throws RuntimeException if native display creation fails
   */
  public final void initialize() {
    if (initialized) {
      return;
    }
    onInitialize();
    initialized = true;
  }

  /**
   * Returns whether the platform has requested that this display close.
   *
   * @return {@code true} when the display should close
   */
  public abstract boolean shouldClose();

  /** Polls native events and dispatches translated input events. */
  public final void pollEvents() {
    onPollEvents();
  }

  /**
   * Presents the most recently rendered frame.
   *
   * <p>The call must run in the rendering context, normally through {@link Device#submit(Runnable)}.
   */
  public abstract void present();

  @Override
  public final void close() {
    if (!initialized) {
      return;
    }
    initialized = false;
    eventBus.clear();
    onClose();
  }

  /** Creates the native display and any associated rendering surface. */
  protected abstract void onInitialize();

  /** Releases native display resources. */
  protected abstract void onClose();

  /** Polls and translates pending native events. */
  protected abstract void onPollEvents();

  /** Applies a size change to the native display. */
  protected abstract void applyPlatformSize(int width, int height);

  /** Applies the vertical-synchronization state to the native display. */
  protected abstract void applyPlatformVsync(boolean vsync);
}
