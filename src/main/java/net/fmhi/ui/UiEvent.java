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

package net.fmhi.ui;

/**
 * Input events dispatched through the UI widget tree.
 *
 * <p>All coordinates are in logical UI units after camera unprojection from physical screen
 * pixels. Events are sealed — the set of subtypes is closed at compile time.
 *
 * @see Widget#handleEvent(UiEvent)
 */
public sealed interface UiEvent permits UiEvent.MouseMove, UiEvent.MouseButton, UiEvent.Scroll, UiEvent.Key,
                                        UiEvent.Char {
  /**
   * The mouse cursor moved to a new logical position.
   *
   * @param x the logical X coordinate
   * @param y the logical Y coordinate
   */
  record MouseMove(float x, float y) implements UiEvent {
  }

  /**
   * A mouse button was pressed or released.
   *
   * @param x       the logical X coordinate at the time of the event
   * @param y       the logical Y coordinate at the time of the event
   * @param button  the mouse button index — 0 is the primary button, 1 is secondary, 2 is middle
   * @param pressed {@code true} if the button was pressed, {@code false} if released
   */
  record MouseButton(float x, float y, int button, boolean pressed) implements UiEvent {
    /**
     * Creates a mouse button event.
     *
     * @param x       the logical X coordinate at the time of the event
     * @param y       the logical Y coordinate at the time of the event
     * @param button  the mouse button index — 0 is the primary button, 1 is secondary, 2 is middle
     * @param pressed {@code true} if the button was pressed, {@code false} if released
     * @throws IllegalArgumentException if {@code button} is outside the range {@code [0, 7]}
     */
    public MouseButton {
      if (button < 0 || button > 7) {
        throw new IllegalArgumentException("button must be in [0,7], got " + button);
      }
    }
  }

  /**
   * The mouse wheel was scrolled.
   *
   * @param x  the logical X coordinate of the cursor at the time of the event
   * @param y  the logical Y coordinate of the cursor at the time of the event
   * @param dx the horizontal scroll delta
   * @param dy the vertical scroll delta, where positive values indicate scrolling up
   */
  record Scroll(float x, float y, float dx, float dy) implements UiEvent {
  }

  /**
   * A keyboard key was pressed, repeated, or released.
   *
   * @param keycode the platform-scancode key identifier
   * @param pressed {@code true} if the key was pressed or is repeating, {@code false} if released
   * @param repeat  {@code true} if this event is a key-repeat from holding the key down
   */
  record Key(int keycode, boolean pressed, boolean repeat) implements UiEvent {
  }

  /**
   * A Unicode character was typed.
   *
   * @param c the typed character
   */
  record Char(char c) implements UiEvent {
  }
}
