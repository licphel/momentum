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

import io.viki.momentum.gfx.ui.element.TextBox;

/**
 * Geometry supplied by a textbox renderer. Input uses the same font and padding as rendering.
 * Implementations are used on the control's owning UI thread.
 */
public interface TextBoxPresentation {
  /**
   * Maps a pointer position to the nearest insertion point in the editor.
   *
   * @param textBox editor whose layout is queried
   * @param x       pointer X coordinate in textbox-local space
   * @param y       pointer Y coordinate in textbox-local space
   * @return UTF-16 insertion index
   */
  int hitIndex(TextBox textBox, float x, float y);

  /**
   * Keeps the current insertion point visible in the containing scroll pane.
   *
   * @param textBox editor whose insertion point should be revealed
   */
  void scrollCursorIntoView(TextBox textBox);
}
