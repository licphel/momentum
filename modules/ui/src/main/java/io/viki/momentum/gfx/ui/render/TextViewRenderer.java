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

package io.viki.momentum.gfx.ui.render;

import io.viki.momentum.gfx.text.MutableText;
import io.viki.momentum.gfx.text.Text;
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.ui.element.TextView;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

/** Renders a cached rich-text layout for a {@link TextView}. */
public final class TextViewRenderer implements ElementRenderer {
  private static final float PADDING = 0.0F;
  private MutableText layout;
  private Text source;
  private float layoutWidth = Float.NaN;
  private boolean layoutWrap;

  /** Creates an empty text-view renderer cache. */
  public TextViewRenderer() {
    layout = new MutableText();
    source = layout;
  }

  /** Invalidates the cached rich-text layout. */
  public void invalidate() {
    layoutWidth = Float.NaN;
  }

  @Override
  public void render(Graphics graphics, Element element, Rectangle area) {
    TextView view = (TextView) element;
    MutableText component = layout(view, area.width());
    graphics.pushScissor(area);
    try {
      graphics.drawText(component, area.minX() + PADDING, area.minY() + PADDING);
    } finally {
      graphics.popScissor();
    }
  }

  private MutableText layout(TextView view, float width) {
    Text value = view.text();
    float maxWidth = view.wrapText() ? Math.max(1.0F, width - PADDING * 2.0F)
        : Float.MAX_VALUE;
    if (source == value && Float.compare(layoutWidth, maxWidth) == 0
        && layoutWrap == view.wrapText()) {
      return layout;
    }
    layout = new MutableText().append(value).maxWidth(maxWidth)
        .justify(view.wrapText()).flipY(true);
    source = value;
    layoutWidth = maxWidth;
    layoutWrap = view.wrapText();
    return layout;
  }
}
