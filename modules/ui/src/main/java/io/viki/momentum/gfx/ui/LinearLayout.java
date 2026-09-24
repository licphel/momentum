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

package io.viki.momentum.gfx.ui;

import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.math.shape.Rectangle;

import java.util.List;

/**
 * Immutable layout that places children consecutively along one axis.
 *
 * <p>Children retain their existing size along the primary axis while the perpendicular axis is
 * controlled by the selected alignment policy. The record is reusable across groups and is safe
 * to share because it contains no mutable layout state.
 *
 * @param axis               the direction in which children are ordered
 * @param crossAxisAlignment the alignment used on the perpendicular axis
 */
public record LinearLayout(Axis axis, CrossAxisAlignment crossAxisAlignment) implements Layout {
  /**
   * Creates a horizontally ordered layout that stretches children vertically.
   *
   * @return a horizontal layout with stretch alignment
   */
  public static LinearLayout horizontal() {
    return new LinearLayout(Axis.HORIZONTAL, CrossAxisAlignment.STRETCH);
  }

  /**
   * Creates a vertically ordered layout that stretches children horizontally.
   *
   * @return a vertical layout with stretch alignment
   */
  public static LinearLayout vertical() {
    return new LinearLayout(Axis.VERTICAL, CrossAxisAlignment.STRETCH);
  }

  
  @Override
  public Axis axis() {
    return axis;
  }

  
  @Override
  public CrossAxisAlignment crossAxisAlignment() {
    return crossAxisAlignment;
  }

  @Override
  public void arrange(Rectangle area, List<Element> children, float gap) {
    float cursor = axis == Axis.HORIZONTAL ? area.minX() : area.minY();
    for (Element child : children) {
      Rectangle preferred = child.bounds();
      if (axis == Axis.HORIZONTAL) {
        float height = crossSize(preferred.height(), area.height());
        float y = crossPosition(area.minY(), area.height(), height);
        child.setBounds(Rectangle.of(cursor, y, preferred.width(), height));
        cursor += preferred.width() + gap;
      } else {
        float width = crossSize(preferred.width(), area.width());
        float x = crossPosition(area.minX(), area.width(), width);
        child.setBounds(Rectangle.of(x, cursor, width, preferred.height()));
        cursor += preferred.height() + gap;
      }
    }
  }

  private float crossSize(float preferred, float available) {
    return crossAxisAlignment == CrossAxisAlignment.STRETCH ? available : preferred;
  }

  private float crossPosition(float start, float available, float childSize) {
    return switch (crossAxisAlignment) {
      case START, STRETCH -> start;
      case CENTER -> start + (available - childSize) * 0.5F;
      case END -> start + available - childSize;
    };
  }

  /**
   * Defines the primary direction in which a linear layout advances through its children.
   *
   * <p>The selected axis determines which child dimension contributes to the running layout
   * position.
   */
  public enum Axis {
    /** Places children from left to right. */
    HORIZONTAL,
    /** Places children from top to bottom. */
    VERTICAL
  }

  /**
   * Defines how each child is positioned across the axis perpendicular to the layout direction.
   *
   * <p>Stretch alignment changes the cross-axis size, while the other values preserve each
   * child's preferred size and change only its position.
   */
  public enum CrossAxisAlignment {
    /** Aligns each child with the start edge. */
    START,
    /** Centers each child across the available cross-axis space. */
    CENTER,
    /** Aligns each child with the end edge. */
    END,
    /** Expands each child to fill the available cross-axis space. */
    STRETCH
  }
}
