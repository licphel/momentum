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

import io.viki.momentum.gfx.text.MutableText;
import io.viki.momentum.gfx.text.Text;
import io.viki.momentum.gfx.ui.ElementRenderer;
import io.viki.momentum.gfx.ui.look.auto.TextViewRenderer;
import io.viki.momentum.math.shape.Rectangle;
import org.jspecify.annotations.Nullable;

/**
 * Displays a rich {@link Text} component without accepting pointer or keyboard input.
 *
 * <p>A text view has no background or border of its own. Its bounds are used as the clipping
 * region and text origin, so it can be placed directly inside a window or another container.
 * The element is mutable and is intended for one owning UI thread.
 */
public final class TextView extends Element {
  private Text text;
  private boolean wrapText;
  private @Nullable MutableText layout;
  private @Nullable Text layoutSource;
  private long layoutVersion;
  private float layoutWidth;
  private boolean layoutWrap;

  /**
   * Creates a rich text view.
   *
   * @param bounds the view's local bounds
   * @param text   the rich content to display
   */
  public TextView(Rectangle bounds, Text text) {
    super(bounds);
    this.text = text;
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
    if (text != value) {
      text = value;
      layout = null;
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
      layout = null;
    }
  }

  @Override
  protected ElementRenderer defaultRenderer() {
    return TextViewRenderer.INSTANCE;
  }

  /**
   * Creates a mutable text suitable for direct rendering.
   *
   * @param width the max width
   * @return a new, directly renderable text
   */
  public MutableText layoutForRender(float width) {
    long version = text instanceof MutableText mutable ? mutable.version() : 0L;
    if (layout != null && layoutSource == text && layoutVersion == version
        && layoutWidth == width && layoutWrap == wrapText) {
      return layout;
    }
    MutableText result = text instanceof MutableText mutable ? mutable.copy()
        : new MutableText().append(text);
    if (wrapText) {
      result.maxWidth(Math.min(result.maxWidth(), Math.max(1.0F, width)));
    }
    layout = result.flipY(true);
    layoutSource = text;
    layoutVersion = version;
    layoutWidth = width;
    layoutWrap = wrapText;
    return layout;
  }
}
