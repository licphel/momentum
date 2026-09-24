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

package io.viki.momentum.gfx.ui.render;

import io.viki.momentum.gfx.text.Literal;
import io.viki.momentum.gfx.text.MutableText;
import io.viki.momentum.gfx.text.TextFormat;
import io.viki.momentum.gfx.text.raster.LayoutGlyph;
import io.viki.momentum.gfx.text.raster.LayoutRun;
import io.viki.momentum.gfx.text.raster.Raster;
import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.ui.HeadlessTextEditor;
import io.viki.momentum.gfx.ui.element.ScrollPane;
import io.viki.momentum.gfx.ui.element.TextBox;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;
import org.jspecify.annotations.Nullable;

/**
 * Renderer and layout cache for a {@link TextBox}.
 *
 * <p>The cache belongs to one TextBox instance. Keeping it here means text rasterization happens
 * only after text, formatting, wrapping width, or control size changes; input and editor state
 * remain in {@link HeadlessTextEditor} and {@code TextBox}.
 */
public final class TextBoxRenderer implements ElementRenderer {
  private @Nullable CachedLayout actualLayout;
  private @Nullable CachedLayout displayLayout;

  @Override
  public void render(Graphics graphics, Element element,
                     Rectangle absoluteBounds) {
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
   * @param textBox text box whose state and editor are rendered
   * @param area absolute destination bounds
   */
  public void render(Graphics graphics, TextBox textBox, Rectangle area) {
    // Draw the textbox frame only when the textbox is not hosted by a ScrollPane.
    if (textBox.enclosingScrollPaneForRender() == null) {
      RendererSupport.drawTextBoxBackground(graphics, area, textBox.enabled(),
          textBox.focused());
    }
    // Build or reuse the actual and placeholder text layouts.
    TextFormat format = RendererSupport.TEXT_FORMAT;
    float padding = RendererSupport.PADDING;
    float contentWidth = layoutWidth(textBox, area.width(), padding);
    String value = textBox.text();
    boolean showingPlaceholder = value.isEmpty() && !textBox.focused();
    String rendered = showingPlaceholder ? textBox.placeholderForRender() : value;
    Color textColor = showingPlaceholder ? RendererSupport.MUTED : RendererSupport.FOREGROUND;
    TextFormat renderedFormat = format.tint(textColor);
    CachedLayout actual = ensureActualLayout(textBox, value.isEmpty() ? format : renderedFormat,
        contentWidth);
    CachedLayout display = rendered.equals(value) ? actual
        : ensureDisplayLayout(textBox, rendered, renderedFormat, contentWidth);
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
   * Invalidates both cached text layouts.
   *
   * <p>The next render or hit test rebuilds the layouts from the current text-box state.
   */
  public void invalidate() {
    actualLayout = null;
    displayLayout = null;
  }

  /**
   * Returns the UTF-16 index nearest a pointer position in the text box.
   *
   * @param textBox text box whose layout is queried
   * @param x pointer x coordinate in the text box's local space
   * @param y pointer y coordinate in the text box's local space
   * @return clamped UTF-16 insertion index
   */
  public int hitIndex(TextBox textBox, float x, float y) {
    if (textBox.text().isEmpty()) {
      return 0;
    }
    TextFormat renderedFormat = RendererSupport.TEXT_FORMAT.tint(RendererSupport.FOREGROUND);
    float padding = RendererSupport.PADDING;
    Raster raster = ensureActualLayout(textBox, renderedFormat,
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
    float padding = RendererSupport.PADDING;
    float x = padding;
    float y = padding;
    float height = Math.max(1.0F, textBox.bounds().height() - padding * 2.0F);
    TextFormat renderedFormat = RendererSupport.TEXT_FORMAT.tint(RendererSupport.FOREGROUND);
    Raster raster = ensureActualLayout(textBox, renderedFormat,
        layoutWidth(textBox, textBox.bounds().width(), padding)).raster();
    updateContentSize(textBox, raster, padding);
    LayoutRun run = runAtCursor(textBox, raster);
    if (run != null) {
      int localIndex = Math.clamp(textBox.cursorIndex() - run.textStart(),
          0, visibleLineLength(run.text()));
      x += offsetFor(run, localIndex);
      y += run.lineTop();
      height = run.lineHeight();
    }
    scrollPane.scrollToVisible(textBox, Rectangle.of(x, y, 1.0F, height));
  }

  private CachedLayout ensureActualLayout(TextBox textBox, TextFormat format, float width) {
    return ensureLayout(textBox, actualLayout, textBox.text(), format, width, true);
  }

  private CachedLayout ensureDisplayLayout(TextBox textBox, String value, TextFormat format,
                                           float width) {
    return ensureLayout(textBox, displayLayout, value, format, width, false);
  }

  private CachedLayout ensureLayout(TextBox textBox, @Nullable CachedLayout cached, String value,
                                    TextFormat format, float width, boolean actual) {
    float layoutWidth = textBox.editor().wrapText() ? Math.max(1.0F, width) : Float.MAX_VALUE;
    if (cached != null && cached.value().equals(value) && cached.format().equals(format)
        && Float.compare(cached.width(), layoutWidth) == 0) {
      return cached;
    }
    MutableText component = new MutableText().append(Literal.of(value).with(format))
        .maxWidth(layoutWidth).justify(textBox.editor().wrapText()).flipY(true);
    CachedLayout replacement = new CachedLayout(value, format, layoutWidth, component,
        component.raster());
    if (actual) {
      actualLayout = replacement;
    } else {
      displayLayout = replacement;
    }
    return replacement;
  }

  private void drawSelection(Graphics graphics, TextBox textBox, Rectangle area, float padding,
                             Raster raster) {
    if (textBox.selectionStart() == textBox.selectionEnd()) {
      return;
    }
    graphics.setTint(RendererSupport.SELECTION);
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
    float height = Math.max(1.0F, area.height() - padding * 2.0F);
    LayoutRun run = runAtCursor(textBox, raster);
    if (run != null) {
      int localIndex = Math.clamp(textBox.cursorIndex() - run.textStart(),
          0, visibleLineLength(run.text()));
      offset = offsetFor(run, localIndex);
      top += run.lineTop();
      height = run.lineHeight();
    }
    graphics.setTint(RendererSupport.FOREGROUND);
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

  private static float layoutWidth(TextBox textBox, float controlWidth, float padding) {
    ScrollPane scrollPane = textBox.enclosingScrollPaneForRender();
    float visibleWidth = scrollPane == null ? controlWidth : scrollPane.viewportWidth();
    return Math.max(1.0F, visibleWidth - padding * 2.0F);
  }

  private static int visibleLineLength(String value) {
    return value.endsWith("\n") ? value.length() - 1 : value.length();
  }

  private record CachedLayout(String value, TextFormat format, float width,
                              MutableText component, Raster raster) {
    private CachedLayout {
      if (!Float.isFinite(width) && width != Float.MAX_VALUE) {
        throw new IllegalArgumentException("Text layout width must be finite or Float.MAX_VALUE: "
            + width);
      }
    }
  }
}
