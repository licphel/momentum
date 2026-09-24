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

package io.viki.momentum.gfx.ui;

import io.viki.momentum.input.KeyAction;
import io.viki.momentum.input.KeyCode;

/**
 * Receives direct input callbacks for one UI element.
 *
 * <p>Every callback is optional and defaults to returning {@code false}, allowing the input
 * router to continue bubbling an event toward parent elements. Implementations should keep their
 * state on the owning UI thread and return {@code true} only after consuming the event.
 */
public interface InputListener {
  /**
   * Handles pointer movement in element-local coordinates.
   *
   * @param x the local pointer X coordinate
   * @param y the local pointer Y coordinate
   * @return whether the event was consumed
   */
  default boolean onMouseMove(float x, float y) {
    return false;
  }

  /**
   * Handles a mouse-button transition in element-local coordinates.
   *
   * @param x the local pointer X coordinate
   * @param y the local pointer Y coordinate
   * @param button the button that changed
   * @param action the button action
   * @param modifiers the active keyboard modifier mask
   * @return whether the event was consumed
   */
  default boolean onMouseButton(float x, float y, KeyCode button, KeyAction action, int modifiers) {
    return false;
  }

  /**
   * Handles a pointer-wheel event in element-local coordinates.
   *
   * @param x the local pointer X coordinate
   * @param y the local pointer Y coordinate
   * @param deltaX the horizontal scroll amount
   * @param deltaY the vertical scroll amount
   * @return whether the event was consumed
   */
  default boolean onScroll(float x, float y, double deltaX, double deltaY) {
    return false;
  }

  /**
   * Handles a keyboard transition delivered to the element.
   *
   * @param key the key that changed
   * @param action the key action
   * @param modifiers the active keyboard modifier mask
   * @return whether the event was consumed
   */
  default boolean onKey(KeyCode key, KeyAction action, int modifiers) {
    return false;
  }

  /**
   * Handles a Unicode character input event.
   *
   * @param codepoint the Unicode code point received
   * @return whether the event was consumed
   */
  default boolean onCharacter(int codepoint) {
    return false;
  }

  /** Notifies the element that the pointer entered its bounds. */
  default void onPointerEnter() {
  }

  /** Notifies the element that the pointer left its bounds. */
  default void onPointerExit() {
  }

  /**
   * Cancels a pointer capture.
   *
   * @param button the button whose capture was canceled
   */
  default void onPointerCancel(KeyCode button) {
  }

  /**
   * Cancels a captured keyboard key that has no matching release event.
   *
   * @param key the key whose capture was canceled
   */
  default void onKeyCancel(KeyCode key) {
  }

  /**
   * Indicates whether the element can receive keyboard focus.
   *
   * @return whether the element accepts focus
   */
  default boolean acceptsFocus() {
    return false;
  }

  /**
   * Notifies the element that its focus state changed.
   *
   * @param focused whether the element is now focused
   */
  default void onFocusChanged(boolean focused) {
  }
}
