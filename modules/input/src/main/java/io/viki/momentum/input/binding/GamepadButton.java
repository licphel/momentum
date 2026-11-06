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

package io.viki.momentum.input.binding;

/**
 * Standard gamepad controls, independent of native button indices.
 */
public enum GamepadButton {
  /** South face button. */
  A,
  /** East face button. */
  B,
  /** West face button. */
  X,
  /** North face button. */
  Y,
  /** Left shoulder button. */
  LEFT_BUMPER,
  /** Right shoulder button. */
  RIGHT_BUMPER,
  /** Back or select button. */
  BACK,
  /** Start or menu button. */
  START,
  /** Guide or home button. */
  GUIDE,
  /** Left stick click. */
  LEFT_STICK,
  /** Right stick click. */
  RIGHT_STICK,
  /** Up direction on the directional pad. */
  DPAD_UP,
  /** Right direction on the directional pad. */
  DPAD_RIGHT,
  /** Down direction on the directional pad. */
  DPAD_DOWN,
  /** Left direction on the directional pad. */
  DPAD_LEFT
}
