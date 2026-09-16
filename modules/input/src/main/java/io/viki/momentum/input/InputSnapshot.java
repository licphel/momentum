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

package io.viki.momentum.input;

import io.viki.momentum.input.event.KeyEvent;
import io.viki.momentum.input.event.MouseButtonEvent;

import java.util.*;

/**
 * Per-frame pollable input state for game-fmt queries.
 *
 * <p>{@code InputSnapshot} accumulates keyboard, mouse, and scroll state as the platform integration
 * receives input. Key press state persists across frames — a held key
 * remains {@link KeyAction#PRESS} until the platform reports a release.
 *
 * <p>For higher-level key binding support, use {@link #key(KeyCode)} to create
 * a {@link Key} instance with persistent press tracking, transition detection, and modifier-aware queries.
 *
 * <p>This class is not thread-safe. All methods must be called from the rendering
 * thread.
 */
public final class InputSnapshot {
  /** Lock-key bits, tracked from event modifier bitmasks as they are toggles rather than held keys. */
  private static final int LOCK_MODS = InputModifiers.CAPS_LOCK | InputModifiers.NUM_LOCK;

  private final EnumMap<KeyCode, KeyAction> keyStates = new EnumMap<>(KeyCode.class);
  private final EnumMap<KeyCode, Integer> keyMods = new EnumMap<>(KeyCode.class);
  private final Map<KeyCode, Key> keys = new EnumMap<>(KeyCode.class);
  private double cursorX;
  private double cursorY;
  private double scrollX;
  private double scrollY;
  private int currentMods;
  /** Lock-key state (Caps Lock, Num Lock) from the most recent event. */
  private int lockMods;

  /**
   * Returns the current action state of the given key.
   *
   * <p>State persists across frames: a held key returns {@link KeyAction#PRESS}
   * or {@link KeyAction#REPEAT} until the platform reports a release.
   *
   * @param code the key to query
   * @return the action state; defaults to {@link KeyAction#RELEASE} if the key has not been recorded
   */
  public KeyAction get(KeyCode code) {
    return keyStates.getOrDefault(code, KeyAction.RELEASE);
  }

  /**
   * Returns whether the given key is currently held down.
   *
   * <p>This is a convenience for {@code get(code) != KeyAction.RELEASE}.
   *
   * @param code the key to query
   * @return {@code true} if the key is pressed or repeating
   */
  public boolean isDown(KeyCode code) {
    return keyStates.getOrDefault(code, KeyAction.RELEASE) != KeyAction.RELEASE;
  }

  /**
   * Returns the modifier bitmask last recorded for the given key.
   *
   * <p>This reflects the modifiers at the moment of the key's own event. For
   * the modifiers currently held down, use {@link #mods()}.
   *
   * @param code the key to query
   * @return the modifier bitmask, or {@code 0} if not recorded
   */
  public int getMods(KeyCode code) {
    return keyMods.getOrDefault(code, 0);
  }

  /**
   * Returns the modifier bitmask of the modifier keys currently held down,
   * including the Caps Lock and Num Lock toggle states.
   *
   * <p>Held modifiers are derived from the held state of the modifier keys
   * themselves, so a modifier pressed after another key is reflected here
   * immediately, regardless of when that other key was pressed. The lock-key
   * bits are taken from the most recent event's modifier bitmask.
   *
   * @return the currently held modifier bitmask
   */
  public int mods() {
    return currentMods;
  }

  /**
   * Returns the current cursor X position in screen coordinates.
   *
   * @return the cursor X position
   */
  public double cursorX() {
    return cursorX;
  }

  /**
   * Returns the current cursor Y position in screen coordinates.
   *
   * @return the cursor Y position
   */
  public double cursorY() {
    return cursorY;
  }

  /**
   * Returns the accumulated horizontal scroll delta since the last frame.
   *
   * @return the horizontal scroll delta
   */
  public double scrollDeltaX() {
    return scrollX;
  }

  /**
   * Returns the accumulated vertical scroll delta since the last frame.
   *
   * @return the vertical scroll delta
   */
  public double scrollDeltaY() {
    return scrollY;
  }

  /**
   * Creates a new {@link Key} bound to the given physical key code and registers it for per-frame updates.
   *
   * <p>Each call returns a new instance — multiple keys may bind to the same
   * physical key simultaneously.
   *
   * @param code the physical key code to bind
   * @return a newly created {@code Key}
   */
  public Key key(KeyCode code) {
    return keys.computeIfAbsent(code, k -> new Key(k, this));
  }

  /**
   * Updates the keyboard state from a key event.
   *
   * <p>Platform integrations call this method when a key event is received. It records the key
   * action and modifier bitmask for subsequent polling, then forwards the event to every registered
   * {@link Key} whose bound code matches the event.
   *
   * @param event the key event to apply
   */
  public void applyKeyEvent(KeyEvent event) {
    applyKey(event.code(), event.action(), event.modifiers());
  }

  /**
   * Updates the keyboard state without constructing an input event object.
   *
   * @param code the physical key whose state changed
   * @param action the key action to record
   * @param modifiers the modifier bitmask reported with the action
   */
  public void applyKey(KeyCode code, KeyAction action, int modifiers) {
    applyKeyState(code, action, modifiers);
  }

  /**
   * Updates the cursor position from a mouse move.
   *
   * <p>Platform integrations call this method when a cursor-move notification is received so the
   * position can be queried through {@link #cursorX()} and {@link #cursorY()}.
   *
   * @param x the cursor X position in screen coordinates
   * @param y the cursor Y position in screen coordinates
   */
  public void applyMouseMove(double x, double y) {
    cursorX = x;
    cursorY = y;
  }

  /**
   * Updates the mouse button state from a mouse button event.
   *
   * <p>Platform integrations call this method when a mouse-button notification is received. It
   * records the button action and cursor position, then forwards the event to every registered
   * {@link Key} whose bound code matches the event.
   *
   * @param event the mouse button event to apply
   */
  public void applyMouseButton(MouseButtonEvent event) {
    applyMouseButton(event.button(), event.action(), event.x(), event.y(), event.modifiers());
  }

  /**
   * Updates mouse-button state without constructing an input event object.
   *
   * @param button the mouse button whose state changed
   * @param action the button action to record
   * @param x the cursor X position in screen coordinates
   * @param y the cursor Y position in screen coordinates
   * @param modifiers the modifier bitmask reported with the action
   */
  public void applyMouseButton(KeyCode button, KeyAction action, double x, double y,
                               int modifiers) {
    cursorX = x;
    cursorY = y;
    applyKeyState(button, action, modifiers);
  }

  private void applyKeyState(KeyCode code, KeyAction action, int modifiers) {
    keyStates.put(code, action);
    keyMods.put(code, modifiers);
    for (Key key : keys.values()) {
      if (key.code() == code) {
        key.apply(action, modifiers);
      }
    }
    lockMods = modifiers & LOCK_MODS;
    updateMods();
  }

  /**
   * Recomputes the held modifier mask from the states of the modifier keys.
   */
  private void updateMods() {
    int m = 0;
    if (isDown(KeyCode.LEFT_SHIFT) || isDown(KeyCode.RIGHT_SHIFT)) {
      m |= InputModifiers.SHIFT;
    }
    if (isDown(KeyCode.LEFT_CONTROL) || isDown(KeyCode.RIGHT_CONTROL)) {
      m |= InputModifiers.CONTROL;
    }
    if (isDown(KeyCode.LEFT_ALT) || isDown(KeyCode.RIGHT_ALT)) {
      m |= InputModifiers.ALT;
    }
    if (isDown(KeyCode.LEFT_SUPER) || isDown(KeyCode.RIGHT_SUPER)) {
      m |= InputModifiers.SUPER;
    }
    currentMods = m | lockMods;
  }

  /**
   * Accumulates scroll deltas from a scroll event.
   *
   * <p>Platform integrations call this method when a scroll notification is received. It
   * accumulates deltas for subsequent polling via {@link #scrollDeltaX()} and {@link #scrollDeltaY()}.
   *
   * @param dx the horizontal scroll delta
   * @param dy the vertical scroll delta
   */
  public void applyScroll(double dx, double dy) {
    scrollX += dx;
    scrollY += dy;
  }

  /**
   * Resets transient per-frame state.
   *
   * <p>Call at the end of each frame. Resets scroll accumulators to zero and
   * clears per-frame transition flags on all registered {@link Key} instances. Key press state is <em>not</em> modified
   * — held keys remain in their current state until the platform reports a release.
   */
  public void clearFrameState() {
    scrollX = 0;
    scrollY = 0;

    for (Key key : keys.values()) {
      key.endFrame();
    }
  }

  /** Clears held input state, for example when the owning view loses focus. */
  public void clearInputState() {
    keyStates.clear();
    keyMods.clear();
    currentMods = InputModifiers.NONE;
    lockMods = InputModifiers.NONE;
    for (Key key : keys.values()) {
      key.apply(KeyAction.RELEASE, InputModifiers.NONE);
    }
  }
}
