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

package net.fmhi.ui;

/**
 * Describes how a widget is positioned and sized relative to its parent's content area.
 *
 * <p>Each axis is independently defined by two normalized anchor values in the range
 * {@code [0, 1]} and two pixel offsets. The anchor values select reference lines within the parent:
 * {@code minAnchorX}/{@code minAnchorY} for the widget's leading edge and {@code maxAnchorX}/{@code maxAnchorY} for its
 * trailing edge. The offset values are added after the anchor position is resolved from the parent size.
 *
 * <p>When the min and max anchors are equal the widget has a fixed size; when they differ the
 * widget stretches between the two anchor lines. Use the static factory methods for common configurations such as
 * pinning to a corner, centering, or filling the parent.
 *
 * @param minAnchorX the normalized parent position for the widget's left edge
 * @param minAnchorY the normalized parent position for the widget's top edge
 * @param maxAnchorX the normalized parent position for the widget's right edge
 * @param maxAnchorY the normalized parent position for the widget's bottom edge
 * @param offsetMinX the pixel offset added to the resolved left position
 * @param offsetMinY the pixel offset added to the resolved top position
 * @param offsetMaxX the pixel offset added to the resolved right position
 * @param offsetMaxY the pixel offset added to the resolved bottom position
 */
public record AnchorLayout(float minAnchorX, float minAnchorY, float maxAnchorX, float maxAnchorY, float offsetMinX,
                           float offsetMinY, float offsetMaxX, float offsetMaxY) {
  /**
   * Pins the widget to the top-left corner with a fixed size.
   *
   * @param x the X offset from the left edge of the parent
   * @param y the Y offset from the top edge of the parent
   * @param w the fixed width
   * @param h the fixed height
   * @return an anchor layout for the top-left corner
   */
  public static AnchorLayout topLeft(float x, float y, float w, float h) {
    return new AnchorLayout(0, 0, 0, 0, x, y, x + w, y + h);
  }

  /**
   * Pins the widget to the top-right corner with a fixed size.
   *
   * @param marginRight the margin inward from the right edge of the parent
   * @param marginTop   the margin from the top edge of the parent
   * @param w           the fixed width
   * @param h           the fixed height
   * @return an anchor layout for the top-right corner
   */
  public static AnchorLayout topRight(float marginRight, float marginTop, float w, float h) {
    return new AnchorLayout(1, 0, 1, 0, -marginRight - w, marginTop, -marginRight, marginTop + h);
  }

  /**
   * Pins the widget to the bottom-left corner with a fixed size.
   *
   * @param marginLeft   the margin from the left edge of the parent
   * @param marginBottom the margin inward from the bottom edge of the parent
   * @param w            the fixed width
   * @param h            the fixed height
   * @return an anchor layout for the bottom-left corner
   */
  public static AnchorLayout bottomLeft(float marginLeft, float marginBottom, float w, float h) {
    return new AnchorLayout(0, 1, 0, 1, marginLeft, -marginBottom - h, marginLeft + w, -marginBottom);
  }

  /**
   * Pins the widget to the bottom-right corner with a fixed size.
   *
   * @param marginRight  the margin inward from the right edge of the parent
   * @param marginBottom the margin inward from the bottom edge of the parent
   * @param w            the fixed width
   * @param h            the fixed height
   * @return an anchor layout for the bottom-right corner
   */
  public static AnchorLayout bottomRight(float marginRight, float marginBottom, float w, float h) {
    return new AnchorLayout(1, 1, 1, 1, -marginRight - w, -marginBottom - h, -marginRight, -marginBottom);
  }

  /**
   * Centers the widget inside the parent with a fixed size.
   *
   * @param w the fixed width
   * @param h the fixed height
   * @return an anchor layout centered in the parent
   */
  public static AnchorLayout center(float w, float h) {
    return new AnchorLayout(0.5F, 0.5F, 0.5F, 0.5F, -w / 2.0F, -h / 2.0F, w / 2.0F, h / 2.0F);
  }

  /**
   * Centers the widget horizontally and pins it to the top edge with a fixed size.
   *
   * @param marginTop the margin from the top edge of the parent
   * @param w         the fixed width
   * @param h         the fixed height
   * @return an anchor layout centered horizontally and pinned to the top
   */
  public static AnchorLayout topCenter(float marginTop, float w, float h) {
    return new AnchorLayout(0.5F, 0, 0.5F, 0, -w / 2.0F, marginTop, w / 2.0F, marginTop + h);
  }

  /**
   * Centers the widget horizontally and pins it to the bottom edge with a fixed size.
   *
   * @param marginBottom the margin inward from the bottom edge of the parent
   * @param w            the fixed width
   * @param h            the fixed height
   * @return an anchor layout centered horizontally and pinned to the bottom
   */
  public static AnchorLayout bottomCenter(float marginBottom, float w, float h) {
    return new AnchorLayout(0.5F, 1, 0.5F, 1, -w / 2.0F, -marginBottom - h, w / 2.0F, -marginBottom);
  }

  /**
   * Centers the widget vertically and pins it to the left edge with a fixed size.
   *
   * @param marginLeft the margin from the left edge of the parent
   * @param w          the fixed width
   * @param h          the fixed height
   * @return an anchor layout centered vertically and pinned to the left
   */
  public static AnchorLayout leftCenter(float marginLeft, float w, float h) {
    return new AnchorLayout(0, 0.5F, 0, 0.5F, marginLeft, -h / 2.0F, marginLeft + w, h / 2.0F);
  }

  /**
   * Centers the widget vertically and pins it to the right edge with a fixed size.
   *
   * @param marginRight the margin inward from the right edge of the parent
   * @param w           the fixed width
   * @param h           the fixed height
   * @return an anchor layout centered vertically and pinned to the right
   */
  public static AnchorLayout rightCenter(float marginRight, float w, float h) {
    return new AnchorLayout(1, 0.5F, 1, 0.5F, -marginRight - w, -h / 2.0F, -marginRight, h / 2.0F);
  }

  /**
   * Stretches the widget to fill the entire parent with a uniform margin on all sides.
   *
   * @param margin the margin in logical pixels on each side
   * @return an anchor layout that fills the parent with the given margin
   */
  public static AnchorLayout fill(float margin) {
    return new AnchorLayout(0, 0, 1, 1, margin, margin, -margin, -margin);
  }

  /**
   * Stretches the widget to fill the full parent width with a fixed height, pinned to the top.
   *
   * @param marginLeft  the margin from the left edge of the parent
   * @param marginRight the margin inward from the right edge of the parent
   * @param h           the fixed height
   * @return an anchor layout for a full-width bar at the top
   */
  public static AnchorLayout topStretch(float marginLeft, float marginRight, float h) {
    return new AnchorLayout(0, 0, 1, 0, marginLeft, 0, -marginRight, h);
  }

  /**
   * Stretches the widget to fill the full parent width with a fixed height, pinned to the bottom.
   *
   * @param marginLeft  the margin from the left edge of the parent
   * @param marginRight the margin inward from the right edge of the parent
   * @param h           the fixed height
   * @return an anchor layout for a full-width bar at the bottom
   */
  public static AnchorLayout bottomStretch(float marginLeft, float marginRight, float h) {
    return new AnchorLayout(0, 1, 1, 1, marginLeft, -h, -marginRight, 0);
  }

  /**
   * Stretches the widget to fill the full parent height with a fixed width, pinned to the left.
   *
   * @param marginTop    the margin from the top edge of the parent
   * @param marginBottom the margin inward from the bottom edge of the parent
   * @param w            the fixed width
   * @return an anchor layout for a full-height bar at the left
   */
  public static AnchorLayout leftStretch(float marginTop, float marginBottom, float w) {
    return new AnchorLayout(0, 0, 0, 1, 0, marginTop, w, -marginBottom);
  }

  /**
   * Stretches the widget to fill the full parent height with a fixed width, pinned to the right.
   *
   * @param marginTop    the margin from the top edge of the parent
   * @param marginBottom the margin inward from the bottom edge of the parent
   * @param w            the fixed width
   * @return an anchor layout for a full-height bar at the right
   */
  public static AnchorLayout rightStretch(float marginTop, float marginBottom, float w) {
    return new AnchorLayout(1, 0, 1, 1, -w, marginTop, 0, -marginBottom);
  }
}
