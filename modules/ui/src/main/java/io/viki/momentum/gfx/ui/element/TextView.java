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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.viki.momentum.gfx.ui.element;

import io.viki.momentum.gfx.text.Text;
import io.viki.momentum.gfx.ui.render.ElementRenderer;
import io.viki.momentum.gfx.ui.render.TextViewRenderer;
import io.viki.momentum.math.shape.Rectangle;

import java.util.Objects;

/**
 * Displays a rich {@link Text} component without accepting pointer or keyboard input.
 *
 * <p>A text view has no background or border of its own. Its bounds are used as the clipping
 * region and text origin, so it can be placed directly inside a window or another container.
 * The element is mutable and is intended for one owning UI thread.
 */
public final class TextView extends Element {
  private final TextViewRenderer renderCache = new TextViewRenderer();
  private Text text;
  private boolean wrapText;

  /**
   * Creates a rich text view.
   *
   * @param bounds the view's local bounds
   * @param text the rich content to display
   */
  public TextView(Rectangle bounds, Text text) {
    super(bounds);
    this.text = Objects.requireNonNull(text, "text");
  }

  /**
   * Returns the rich content currently displayed by this view.
   *
   * @return current rich content
   */
  public Text text() {
    return text;
  }

  /**
   * Replaces the rich content and invalidates its cached layout.
   *
   * @param value new rich content
   */
  public void setText(Text value) {
    Text checked = Objects.requireNonNull(value, "value");
    if (text != checked) {
      text = checked;
      renderCache.invalidate();
    }
  }

  /**
   * Reports whether long lines are wrapped to the view width.
   *
   * @return whether wrapping is enabled
   */
  public boolean wrapText() {
    return wrapText;
  }

  /**
   * Enables or disables width-constrained line wrapping.
   *
   * @param value whether long lines should wrap
   */
  public void setWrapText(boolean value) {
    if (wrapText != value) {
      wrapText = value;
      renderCache.invalidate();
    }
  }

  @Override
  protected ElementRenderer defaultRenderer() {
    return renderCache;
  }
}
