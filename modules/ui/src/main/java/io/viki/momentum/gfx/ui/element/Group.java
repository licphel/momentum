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

import io.viki.momentum.gfx.ui.Layout;
import io.viki.momentum.math.shape.Rectangle;

/**
 * Provides a container whose direct children are arranged by a configurable {@link Layout}.
 *
 * <p>Changing the bounds, layout, padding, gap, or child list immediately recalculates child
 * positions. The group is mutable and not thread-safe.
 */
public final class Group extends Element {
  private static final float DEFAULT_GAP = 4.0F;

  private Layout layout;
  private float padding;
  private float gap = DEFAULT_GAP;

  /**
   * Creates a group with the supplied bounds and layout strategy.
   *
   * @param bounds the group's local bounds
   * @param layout the strategy used to arrange direct children
   */
  public Group(Rectangle bounds, Layout layout) {
    super(bounds);
    this.layout = layout;
  }

  /**
   * Returns the strategy used to arrange direct children.
   *
   * @return current child layout strategy
   */
  public Layout layout() {
    return layout;
  }

  /**
   * Changes the child layout strategy and immediately rearranges children.
   *
   * @param value the new layout strategy
   */
  public void setLayout(Layout value) {
    layout = value;
    layoutChildren();
  }

  /**
   * Adds a direct child and rearranges the group.
   *
   * @param element the child to add
   */
  public void add(Element element) {
    addChild(element);
  }

  /**
   * Removes a direct child and rearranges the group when removal succeeds.
   *
   * @param element the child to remove
   * @return whether the child was attached to this group
   */
  public boolean remove(Element element) {
    return removeChild(element);
  }

  @Override
  public void addChild(Element child) {
    super.addChild(child);
    layoutChildren();
  }

  @Override
  public boolean removeChild(Element child) {
    boolean removed = super.removeChild(child);
    if (removed) {
      layoutChildren();
    }
    return removed;
  }

  @Override
  public void clearChildren() {
    super.clearChildren();
    layoutChildren();
  }

  @Override
  public void setBounds(Rectangle value) {
    super.setBounds(value);
    layoutChildren();
  }

  /**
   * Recalculates direct-child bounds using the current layout settings.
   */
  public void layoutChildren() {
    float width = Math.max(0.0F, bounds().width() - padding * 2.0F);
    float height = Math.max(0.0F, bounds().height() - padding * 2.0F);
    layout.arrange(Rectangle.of(padding, padding, width, height), children(), gap);
  }

  /**
   * Returns the inner padding applied before arranging children.
   *
   * @return padding in logical units
   */
  public float padding() {
    return padding;
  }

  /**
   * Sets the inner padding and rearranges children.
   *
   * @param value the finite, non-negative padding
   * @throws IllegalArgumentException if {@code value} is negative or not finite
   */
  public void setPadding(float value) {
    if (!Float.isFinite(value) || value < 0.0F) {
      throw new IllegalArgumentException("Group padding must be finite and non-negative: " + value);
    }
    padding = value;
    layoutChildren();
  }

  /**
   * Returns the spacing inserted between adjacent children.
   *
   * @return child gap in logical units
   */
  public float gap() {
    return gap;
  }

  /**
   * Sets the spacing between adjacent children and rearranges them.
   *
   * @param value the finite, non-negative gap
   * @throws IllegalArgumentException if {@code value} is negative or not finite
   */
  public void setGap(float value) {
    if (!Float.isFinite(value) || value < 0.0F) {
      throw new IllegalArgumentException("Group gap must be finite and non-negative: " + value);
    }
    gap = value;
    layoutChildren();
  }
}
