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

import io.viki.momentum.gfx.text.TextFormat;
import io.viki.momentum.gfx.text.raster.LayoutGlyph;
import io.viki.momentum.gfx.text.raster.LayoutRun;
import io.viki.momentum.gfx.text.raster.Raster;
import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.ui.element.ScrollPane;
import io.viki.momentum.gfx.ui.element.TextBox;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;
import org.jspecify.annotations.Nullable;

/**
 * Shared editor presentation and hit testing, independent of the control's background skin.
 * Instances are immutable and may be shared by controls on their owning UI thread.
 */
public final class TextBoxContent implements ElementRenderer, TextBoxPresentation {
  private static final float PADDING = 4.0F;
  private static final Color FOREGROUND = new Color(0.93F, 0.94F, 0.97F, 0.96F);
  private static final Color MUTED = new Color(0.70F, 0.73F, 0.78F, 0.70F);
  private static final Color SELECTION = new Color(0.2F, 0.45F, 0.85F, 0.55F);
  private final TextFormat format;

  /**
   * Creates editor presentation using the supplied text style.
   *
   * @param format style used consistently for display, caret metrics, and pointer mapping
   */
  public TextBoxContent(TextFormat format) {
    this.format = format;
  }

  private static float offsetFor(LayoutRun run, int index) {
    int localIndex = Math.clamp(index, 0, visibleLineLength(run.text()));
    LayoutGlyph[] glyphs = run.glyphs();
    float last = 0.0F;
    for (LayoutGlyph glyph : glyphs) {
      if (localIndex <= glyph.start()) {
        return glyph.x();
      }
      if (localIndex < glyph.end()) {
        int span = Math.max(1, glyph.end() - glyph.start());
        return glyph.x() + glyph.w() * (localIndex - glyph.start()) / span;
      }
      last = Math.max(last, glyph.x() + glyph.w());
    }
    return last;
  }

  private static float layoutWidth(TextBox textBox, float controlWidth, float padding) {
    ScrollPane scrollPane = textBox.enclosingScrollPaneForRender();
    float visibleWidth = scrollPane == null ? controlWidth : scrollPane.viewportWidth();
    return Math.max(1.0F, visibleWidth - padding * 2.0F);
  }

  private static int visibleLineLength(String value) {
    return value.endsWith("\n") ? value.length() - 1 : value.length();
  }

  @Override
  public void render(Graphics graphics, Element element, Rectangle absoluteBounds) {
    render(graphics, (TextBox) element, absoluteBounds);
  }

  /**
   * Renders one text box at its absolute bounds.
   *
   * <p>The renderer keeps rasterized layouts cached and invalidates them only when text,
   * formatting, or the effective content width changes. Selection, caret, and glyphs are clipped
   * to the control area.
   *
   * @param graphics graphics context receiving the control
   * @param textBox  text box whose state and editor are rendered
   * @param area     absolute destination bounds
   */
  public void render(Graphics graphics, TextBox textBox, Rectangle area) {
    // Build or reuse the actual and placeholder text layouts.
    TextFormat format = this.format;
    float padding = PADDING;
    float contentWidth = layoutWidth(textBox, area.width(), padding);
    String value = textBox.text();
    boolean showingPlaceholder = value.isEmpty() && !textBox.focused();
    String rendered = showingPlaceholder ? textBox.placeholderForRender() : value;
    Color textColor = showingPlaceholder ? MUTED : FOREGROUND;
    TextFormat renderedFormat = format.tint(textColor);
    TextBox.Layout actual = textBox.actualLayoutForRender(
        value.isEmpty() ? format : renderedFormat, contentWidth);
    TextBox.Layout display = rendered.equals(value) ? actual
        : textBox.displayLayoutForRender(rendered, renderedFormat, contentWidth);
    // Update scroll content dimensions from the raster bounds.
    updateContentSize(textBox, actual.raster(), padding);

    // Clip every text-box visual, including selection and caret, to its content area.
    graphics.pushScissor(area);
    try {
      // Draw selection highlights before glyphs so text remains readable.
      if (!value.isEmpty()) {
        drawSelection(graphics, textBox, area, padding, actual.raster());
      }
      // Draw the cached text component at the content origin.
      graphics.drawText(display.component(), area.minX() + padding, area.minY() + padding);
      // Draw the insertion caret on top of the text when editing.
      if (textBox.focused() && textBox.editor().editable() && textBox.enabled()) {
        drawCaret(graphics, textBox, area, padding, actual.raster());
      }
    } finally {
      graphics.popScissor();
    }
  }

  /**
   * Returns the UTF-16 index nearest a pointer position in the text box.
   *
   * @param textBox text box whose layout is queried
   * @param x       pointer x coordinate in the text box's local space
   * @param y       pointer y coordinate in the text box's local space
   * @return clamped UTF-16 insertion index
   */
  public int hitIndex(TextBox textBox, float x, float y) {
    if (textBox.text().isEmpty()) {
      return 0;
    }
    TextFormat renderedFormat = this.format.tint(FOREGROUND);
    float padding = PADDING;
    Raster raster = textBox.actualLayoutForRender(renderedFormat,
        layoutWidth(textBox, textBox.bounds().width(), padding)).raster();
    LayoutRun[] runs = raster.runs();
    if (textBox.text().endsWith("\n") && runs.length > 0) {
      LayoutRun last = runs[runs.length - 1];
      if (y - padding >= last.lineTop() + last.lineHeight()) {
        return textBox.text().length();
      }
    }
    int hit = raster.hitTest(x - padding, y - padding);
    return hit < 0 ? textBox.text().length()
        : Math.clamp(hit, 0, textBox.text().length());
  }

  /**
   * Scrolls the containing pane until the text box's caret is visible.
   *
   * <p>If the text box is not hosted by a {@link ScrollPane}, this method does nothing.
   *
   * @param textBox text box whose caret should be revealed
   */
  public void scrollCursorIntoView(TextBox textBox) {
    ScrollPane scrollPane = textBox.enclosingScrollPaneForRender();
    if (scrollPane == null) {
      return;
    }
    float padding = PADDING;
    float x = padding;
    float y = padding;
    float height = emptyLineHeight(textBox.bounds().height(), padding);
    TextFormat renderedFormat = this.format.tint(FOREGROUND);
    Raster raster = textBox.actualLayoutForRender(renderedFormat,
        layoutWidth(textBox, textBox.bounds().width(), padding)).raster();
    updateContentSize(textBox, raster, padding);
    LayoutRun run = runAtCursor(textBox, raster);
    if (run != null) {
      int localIndex = Math.clamp(textBox.cursorIndex() - run.textStart(),
          0, visibleLineLength(run.text()));
      x += offsetFor(run, localIndex);
      y += caretTop(run);
      height = caretHeight();
    }
    scrollPane.scrollToVisible(textBox, Rectangle.of(x, y, 1.0F, height));
  }

  private void drawSelection(Graphics graphics, TextBox textBox, Rectangle area, float padding,
                             Raster raster) {
    if (textBox.selectionStart() == textBox.selectionEnd()) {
      return;
    }
    graphics.setTint(SELECTION);
    for (LayoutRun run : raster.runs()) {
      int runStart = run.textStart();
      int runEnd = runStart + visibleLineLength(run.text());
      int start = Math.max(textBox.selectionStart(), runStart);
      int end = Math.min(textBox.selectionEnd(), runEnd);
      if (start >= end) {
        continue;
      }
      float left = offsetFor(run, start - runStart);
      float right = offsetFor(run, end - runStart);
      graphics.drawRectangle(Rectangle.of(area.minX() + padding + left,
          area.minY() + padding + run.lineTop(), Math.max(1.0F, right - left),
          run.lineHeight()));
    }
    graphics.setTint(Color.WHITE);
  }

  private void drawCaret(Graphics graphics, TextBox textBox, Rectangle area, float padding,
                         Raster raster) {
    float offset = 0.0F;
    float top = area.minY() + padding;
    float height = emptyLineHeight(area.height(), padding);
    LayoutRun run = runAtCursor(textBox, raster);
    if (run != null) {
      int localIndex = Math.clamp(textBox.cursorIndex() - run.textStart(),
          0, visibleLineLength(run.text()));
      offset = offsetFor(run, localIndex);
      top += caretTop(run);
      height = caretHeight();
    }
    graphics.setTint(FOREGROUND);
    float caretX = area.minX() + padding + offset;
    graphics.drawLine(caretX, top, caretX, top + height);
    graphics.setTint(Color.WHITE);
  }

  private @Nullable LayoutRun runAtCursor(TextBox textBox, Raster raster) {
    LayoutRun[] runs = raster.runs();
    for (int i = 0; i < runs.length; i++) {
      LayoutRun run = runs[i];
      int end = run.textStart() + visibleLineLength(run.text());
      if (textBox.cursorIndex() <= end || i == runs.length - 1) {
        return run;
      }
    }
    return null;
  }

  private void updateContentSize(TextBox textBox, Raster raster, float padding) {
    ScrollPane scrollPane = textBox.enclosingScrollPaneForRender();
    if (scrollPane == null) {
      return;
    }
    float textWidth = Math.max(0.0F, raster.bounds().maxX());
    float textHeight = Math.max(0.0F, raster.bounds().maxY());
    for (LayoutRun run : raster.runs()) {
      textHeight = Math.max(textHeight, run.lineTop() + run.lineHeight());
      for (LayoutGlyph glyph : run.glyphs()) {
        textWidth = Math.max(textWidth, glyph.x() + glyph.w());
      }
    }
    float width = textBox.editor().wrapText()
        ? scrollPane.viewportWidth()
        : Math.max(Math.max(textBox.minimumContentWidthForRender(), scrollPane.viewportWidth()),
        textWidth + padding * 2.0F);
    float contentHeight = Math.max(Math.max(textBox.minimumContentHeightForRender(),
        scrollPane.viewportHeight()), textHeight + padding * 2.0F);
    scrollPane.setContentSize(width, contentHeight);
  }

  private float emptyLineHeight(float controlHeight, float padding) {
    float contentHeight = Math.max(1.0F, controlHeight - padding * 2.0F);
    return Math.min(caretHeight(), contentHeight);
  }

  private float caretTop(LayoutRun run) {
    float ascender = this.format.font().ascender();
    return run.lineY() - ascender;
  }

  private float caretHeight() {
    float ascender = this.format.font().ascender();
    float descender = this.format.font().descender();
    return Math.max(1.0F, ascender - descender);
  }
}
