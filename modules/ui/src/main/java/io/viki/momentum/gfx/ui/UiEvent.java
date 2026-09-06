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

import io.viki.momentum.gfx.input.KeyAction;
import io.viki.momentum.gfx.input.KeyCode;
import io.viki.momentum.math.Vector2;

/** Immutable, thread-safe input events dispatched in the current element's local coordinates. */
public sealed interface UiEvent permits UiEvent.Positioned, UiEvent.Key, UiEvent.Char {
  /** An event carrying a position translated as it travels down the element tree. */
  sealed interface Positioned extends UiEvent permits UiEvent.MouseMove, UiEvent.MouseButton, UiEvent.Scroll {
    /** Returns the pointer position carried by this event. */
    Vector2 position();

    /** Returns this event at another position while preserving its kind and payload. */
    Positioned withPosition(Vector2 position);

    /** Returns this event translated by the given local-space offset. */
    default Positioned translated(float x, float y) {
      return withPosition(new Vector2(position().x() + x, position().y() + y));
    }
  }

  /** Pointer movement event. */
  record MouseMove(Vector2 position) implements Positioned {
    @Override
    public MouseMove withPosition(Vector2 value) {
      return new MouseMove(value);
    }
  }

  /** Primary pointer-button event. */
  record MouseButton(Vector2 position, KeyCode button, KeyAction action, int modifiers) implements Positioned {
    @Override
    public MouseButton withPosition(Vector2 value) {
      return new MouseButton(value, button, action, modifiers);
    }
  }

  /** Pointer-wheel event. */
  record Scroll(Vector2 position, double dx, double dy) implements Positioned {
    @Override
    public Scroll withPosition(Vector2 value) {
      return new Scroll(value, dx, dy);
    }
  }

  /** Keyboard transition event. */
  record Key(KeyCode code, KeyAction action, int modifiers) implements UiEvent {
  }

  /** Unicode text-input event. */
  record Char(int codepoint) implements UiEvent {
  }
}
