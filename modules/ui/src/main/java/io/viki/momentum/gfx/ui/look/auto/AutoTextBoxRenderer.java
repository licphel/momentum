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

package io.viki.momentum.gfx.ui.look.auto;

import io.viki.momentum.gfx.ui.present.ElementRenderer;
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.ui.element.TextBox;
import io.viki.momentum.gfx.ui.present.TextBoxContent;
import io.viki.momentum.gfx.ui.present.TextBoxPresentation;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

/** Shared automatic textbox skin. Use on the owning UI thread, as with its controls. */
public final class AutoTextBoxRenderer implements ElementRenderer, TextBoxPresentation {
  /** Shared renderer used by the automatic look. */
  public static final AutoTextBoxRenderer INSTANCE = new AutoTextBoxRenderer();
  private final TextBoxContent content = new TextBoxContent(AutoRendererSupport.TEXT_FORMAT);

  private AutoTextBoxRenderer() {
  }

  @Override
  public void render(Graphics graphics, Element element, Rectangle area) {
    // Preserve the existing automatic frame policy for scroll-pane hosted editors.
    TextBox textBox = (TextBox) element;
    if (textBox.enclosingScrollPaneForRender() == null) {
      AutoRendererSupport.drawTextBoxBackground(graphics, area, textBox.enabled(), textBox.focused());
    }
    this.content.render(graphics, textBox, area);
  }

  @Override
  public int hitIndex(TextBox textBox, float x, float y) {
    return this.content.hitIndex(textBox, x, y);
  }

  @Override
  public void scrollCursorIntoView(TextBox textBox) {
    this.content.scrollCursorIntoView(textBox);
  }
}
