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

package io.viki.momentum.gfx.ui.present;

import io.viki.momentum.gfx.texture.Drawable2D;

/**
 * Immutable interaction-state artwork. Resources are borrowed and used on the UI thread.
 * State images need not have equal dimensions; matching artwork is the caller's responsibility.
 *
 * @param idle     idle artwork
 * @param hovered  hovered artwork
 * @param pressed  pressed artwork
 * @param disabled disabled artwork
 */
public record Artworks(Drawable2D idle, Drawable2D hovered,
                       Drawable2D pressed, Drawable2D disabled) {
  /**
   * Uses one drawable for every interaction state.
   *
   * @param drawable shared state artwork
   */
  public Artworks(Drawable2D drawable) {
    this(drawable, drawable, drawable, drawable);
  }

  /**
   * Returns the drawable corresponding to the current interaction state.
   *
   * @param enabled whether the control is enabled
   * @param hovered whether it is hovered or focused
   * @param pressed whether it is pressed
   * @return selected state artwork
   */
  public Drawable2D select(boolean enabled, boolean hovered, boolean pressed) {
    return !enabled ? disabled : pressed ? this.pressed : hovered ? this.hovered : idle;
  }
}
