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

package io.viki.momentum.gfx.ui.element;

import io.viki.momentum.input.KeyAction;
import io.viki.momentum.input.KeyCode;
import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.gfx.ui.render.ElementRenderer;
import io.viki.momentum.gfx.ui.render.ScrollPaneRenderer;

/**
 * Provides a clipped viewport that equips one content element with automatic scrollbars.
 *
 * <p>The pane keeps its scrollbars outside the content clip, supports optional horizontal
 * scrolling, and updates the content position as either bar changes. Its mutable state is not
 * thread-safe.
 */
public final class ScrollPane extends Element {
  private final Element content;
  private final Viewport viewport;
  private final ScrollBar horizontalBar;
  private final ScrollBar verticalBar;
  private float contentWidth;
  private float contentHeight;
  private float viewportWidth;
  private float viewportHeight;
  private float barThickness = 4.0F;
  private float barGap = 2.0F;
  private boolean horizontalScrollEnabled = true;

  /**
   * Creates a scroll pane around the supplied content element.
   *
   * @param bounds the pane's local bounds
   * @param content the element to display and scroll
   */
  public ScrollPane(Rectangle bounds, Element content) {
    super(bounds);
    this.content = content;
    contentWidth = content.bounds().width();
    contentHeight = content.bounds().height();
    viewport = new Viewport(content);
    horizontalBar = new ScrollBar(Rectangle.ZERO, ScrollBar.Orientation.HORIZONTAL);
    verticalBar = new ScrollBar(Rectangle.ZERO, ScrollBar.Orientation.VERTICAL);
    horizontalBar.setOnChanged(value -> positionContent());
    verticalBar.setOnChanged(value -> positionContent());
    // Keep the bars in the normal child layer so they stay inside their window's z-order.
    // The viewport wrapper supplies clipping only to the scrolling content.
    addChild(viewport);
    addChild(horizontalBar);
    addChild(verticalBar);
    updateLayout();
  }

  /**
   * Returns the element displayed inside the clipped viewport.
   *
   * @return wrapped content element
   */
  public Element content() {
    return content;
  }

  /**
   * Returns the horizontal scrollbar owned by this pane.
   *
   * @return horizontal scrollbar, which may be hidden when content fits
   */
  public ScrollBar horizontalBar() {
    return horizontalBar;
  }

  /**
   * Returns the vertical scrollbar owned by this pane.
   *
   * @return vertical scrollbar, which may be hidden when content fits
   */
  public ScrollBar verticalBar() {
    return verticalBar;
  }

  @Override
  protected ElementRenderer defaultRenderer() {
    return ScrollPaneRenderer.INSTANCE;
  }

  /**
   * Sets the logical content dimensions used to determine scrollbar visibility.
   *
   * @param width the content width
   * @param height the content height
   * @throws IllegalArgumentException if a dimension is negative or not finite
   */
  public void setContentSize(float width, float height) {
    if (!Float.isFinite(width) || !Float.isFinite(height) || width < 0.0F || height < 0.0F) {
      throw new IllegalArgumentException("Scroll content size must be finite and non-negative: "
          + width + "x" + height);
    }
    if (contentWidth == width && contentHeight == height) {
      return;
    }
    contentWidth = width;
    contentHeight = height;
    updateLayout();
  }

  /**
   * Returns the visible content width after scrollbar allocation.
   *
   * @return viewport width in logical units
   */
  public float viewportWidth() {
    return viewportWidth;
  }

  /**
   * Returns the visible content height after scrollbar allocation.
   *
   * @return viewport height in logical units
   */
  public float viewportHeight() {
    return viewportHeight;
  }

  /**
   * Reports whether horizontal scrolling is allowed for this pane.
   *
   * @return whether horizontal scrolling is enabled
   */
  public boolean horizontalScrollEnabled() {
    return horizontalScrollEnabled;
  }

  /**
   * Enables or disables horizontal scrolling and recalculates the layout.
   *
   * @param value whether horizontal scrolling should be available
   */
  public void setHorizontalScrollEnabled(boolean value) {
    horizontalScrollEnabled = value;
    updateLayout();
  }

  /**
   * Returns the scrollbar thickness reserved by the pane.
   *
   * @return scrollbar thickness in logical units
   */
  public float barThickness() {
    return barThickness;
  }

  /**
   * Sets the scrollbar thickness and recalculates the layout.
   *
   * @param value the finite, non-negative thickness
   * @throws IllegalArgumentException if {@code value} is negative or not finite
   */
  public void setBarThickness(float value) {
    if (!Float.isFinite(value) || value < 0.0F) {
      throw new IllegalArgumentException("Scroll bar thickness must be finite and non-negative: "
          + value);
    }
    barThickness = value;
    updateLayout();
  }

  /**
   * Returns the gap between content and scrollbar tracks.
   *
   * @return scrollbar gap in logical units
   */
  public float barGap() {
    return barGap;
  }

  /**
   * Sets the gap between content and scrollbar tracks.
   *
   * @param value the finite, non-negative gap
   * @throws IllegalArgumentException if {@code value} is negative or not finite
   */
  public void setBarGap(float value) {
    if (!Float.isFinite(value) || value < 0.0F) {
      throw new IllegalArgumentException("Scroll bar gap must be finite and non-negative: " + value);
    }
    barGap = value;
    updateLayout();
  }

  /**
   * Scrolls just far enough to reveal a descendant region.
   *
   * @param descendant the descendant containing the local region
   * @param localArea the region expressed in descendant-local coordinates
   * @throws IllegalArgumentException if the descendant is not inside this pane
   */
  public void scrollToVisible(Element descendant, Rectangle localArea) {
    Rectangle contentArea = localArea;
    Element current = descendant;
    while (current != content) {
      Element parent = current.parent();
      if (parent == null) {
        throw new IllegalArgumentException("Element is not inside this ScrollPane");
      }
      contentArea = contentArea.translate(current.bounds().minX(), current.bounds().minY());
      current = parent;
    }
    if (horizontalBar.visible()) {
      horizontalBar.setValue(reveal(horizontalBar.value(), viewportWidth,
          contentArea.minX(), contentArea.maxX()));
    }
    if (verticalBar.visible()) {
      verticalBar.setValue(reveal(verticalBar.value(), viewportHeight,
          contentArea.minY(), contentArea.maxY()));
    }
    positionContent();
  }

  @Override
  public void setBounds(Rectangle value) {
    super.setBounds(value);
    updateLayout();
  }

  @Override
  public boolean onMouseMove(float x, float y) {
    return true;
  }

  @Override
  public boolean onScroll(float x, float y, double deltaX, double deltaY) {
    if (verticalBar.visible() && deltaY != 0.0) {
      verticalBar.setValue(verticalBar.value() - Math.copySign(verticalBar.step(), deltaY));
      positionContent();
      return true;
    }
    if (horizontalBar.visible() && deltaX != 0.0) {
      horizontalBar.setValue(horizontalBar.value() - Math.copySign(horizontalBar.step(), deltaX));
      positionContent();
      return true;
    }
    return false;
  }

  @Override
  public boolean onKey(KeyCode key, KeyAction action, int modifiers) {
    if (action == KeyAction.RELEASE || !verticalBar.visible()) {
      return false;
    }
    if (key == KeyCode.PAGE_UP || key == KeyCode.PAGE_DOWN) {
      double direction = key == KeyCode.PAGE_UP ? -1.0 : 1.0;
      verticalBar.setValue(verticalBar.value() + direction * viewportHeight);
      positionContent();
      return true;
    }
    return false;
  }

  @Override
  public boolean acceptsFocus() {
    return true;
  }

  private void updateLayout() {
    float thickness = barThickness();
    float gap = barGap();
    float barExtent = thickness + gap;
    boolean allowHorizontal = horizontalScrollEnabled
        && (!(content instanceof TextBox textBox) || !textBox.wrapText());
    boolean horizontal = false;
    boolean vertical = false;
    for (int i = 0; i < 2; i++) {
      horizontal = allowHorizontal
          && contentWidth > bounds().width() - (vertical ? barExtent : 0.0F);
      vertical = contentHeight > bounds().height() - (horizontal ? barExtent : 0.0F);
    }
    viewportWidth = Math.max(0.0F, bounds().width() - (vertical ? barExtent : 0.0F));
    viewportHeight = Math.max(0.0F, bounds().height() - (horizontal ? barExtent : 0.0F));

    viewport.setBounds(Rectangle.of(0.0F, 0.0F, viewportWidth, viewportHeight));
    horizontalBar.setVisible(horizontal);
    verticalBar.setVisible(vertical);
    horizontalBar.setBounds(Rectangle.of(0.0F, viewportHeight + gap, viewportWidth, thickness));
    verticalBar.setBounds(Rectangle.of(viewportWidth + gap, 0.0F, thickness, viewportHeight));
    horizontalBar.setRange(0.0,
        horizontal ? Math.max(0.0F, contentWidth - viewportWidth) : 0.0, viewportWidth);
    verticalBar.setRange(0.0,
        vertical ? Math.max(0.0F, contentHeight - viewportHeight) : 0.0, viewportHeight);
    positionContent();
  }

  private void positionContent() {
    float layoutWidth = horizontalBar.visible() ? contentWidth : viewportWidth;
    content.setBounds(Rectangle.of((float) -horizontalBar.value(), (float) -verticalBar.value(), layoutWidth, contentHeight));
  }

  void contentLayoutChanged() {
    updateLayout();
  }

  private static double reveal(double offset, float viewportSize, float minimum, float maximum) {
    if (minimum < offset) {
      return minimum;
    }
    if (maximum > offset + viewportSize) {
      return maximum - viewportSize;
    }
    return offset;
  }

  /** Content-only clipping layer; scrollbars deliberately remain siblings outside this clip. */
  private static final class Viewport extends Element {
    private Viewport(Element content) {
      super(Rectangle.ZERO);
      addChild(content);
    }

    @Override
    protected Rectangle childrenClip(Rectangle area) {
      return area;
    }
  }
}
