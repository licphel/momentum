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

import io.viki.momentum.gfx.text.Text;
import io.viki.momentum.gfx.util.impl.Graphics;

import java.util.List;

/**
 * Presentation of transient rich-text tooltips. Invoked after the element tree on the UI thread.
 * Input routing retains responsibility for hover detection and display delay.
 */
@FunctionalInterface
public interface TooltipRenderer {
  /**
   * Renders the rich-text entries at the requested tooltip origin.
   *
   * @param graphics graphics context receiving the tooltip
   * @param values   rich-text lines in display order
   * @param x        tooltip origin in logical coordinates
   * @param y        tooltip origin in logical coordinates
   */
  void render(Graphics graphics, List<Text> values, float x, float y);
}
