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

import io.viki.momentum.gfx.ui.render.ElementRenderer;
import io.viki.momentum.gfx.ui.render.ScrollBarRenderer;
import io.viki.momentum.input.KeyAction;
import io.viki.momentum.input.KeyCode;
import io.viki.momentum.math.shape.Rectangle;
import org.jspecify.annotations.Nullable;

import java.util.function.DoubleConsumer;

/**
 * Represents a reusable horizontal or vertical scrollbar with pointer, wheel, and keyboard input.
 *
 * <p>The scrollbar maps a numeric range to a thumb position, clamps updates to that range, and
 * reports user-driven changes through an optional callback. It is mutable and not thread-safe.
 */
public final class ScrollBar extends Element {
  private final Orientation orientation;
  private double minimum;
  private double maximum;
  private double pageSize;
  private double value;
  private double step = 24.0;
  private boolean hovered;
  private boolean focused;
  private boolean dragging;
  private boolean focusable = true;
  private float minimumThumbSize = 12.0F;
  private float dragOffset;
  private @Nullable DoubleConsumer onChanged;

  /**
   * Creates a scrollbar with an initially empty range.
   *
   * @param bounds the scrollbar's local bounds
   * @param orientation the axis represented by the scrollbar
   */
  public ScrollBar(Rectangle bounds, Orientation orientation) {
    super(bounds);
    this.orientation = orientation;
  }

  /**
   * Returns the axis represented by this scrollbar.
   *
   * @return scrollbar orientation
   */
  public Orientation orientation() {
    return orientation;
  }

  /**
   * Returns the lower end of the scrollbar range.
   *
   * @return minimum range value
   */
  public double minimum() {
    return minimum;
  }

  /**
   * Returns the upper end of the scrollbar range.
   *
   * @return maximum range value
   */
  public double maximum() {
    return maximum;
  }

  /**
   * Returns the visible page extent used to size the thumb.
   *
   * @return visible page size
   */
  public double pageSize() {
    return pageSize;
  }

  /**
   * Sets the range and visible page extent.
   *
   * @param minimum the lower range bound
   * @param maximum the upper range bound
   * @param pageSize the visible page extent
   * @throws IllegalArgumentException if any value is invalid
   */
  public void setRange(double minimum, double maximum, double pageSize) {
    if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || maximum < minimum
        || !Double.isFinite(pageSize) || pageSize < 0.0) {
      throw new IllegalArgumentException("Invalid scrollbar range: " + minimum + ".." + maximum
          + ", page " + pageSize);
    }
    this.minimum = minimum;
    this.maximum = maximum;
    this.pageSize = pageSize;
    updateValue(value, false);
  }

  /**
   * Returns the current clamped range value.
   *
   * @return current scrollbar value
   */
  public double value() {
    return value;
  }

  /**
   * Sets the range value without notifying the change listener.
   *
   * @param value the candidate range value
   * @throws IllegalArgumentException if {@code value} is not finite
   */
  public void setValue(double value) {
    updateValue(value, false);
  }

  /**
   * Returns the increment used by wheel and keyboard navigation.
   *
   * @return positive navigation step
   */
  public double step() {
    return step;
  }

  /**
   * Sets the wheel and keyboard increment.
   *
   * @param value the finite, positive increment
   * @throws IllegalArgumentException if {@code value} is not finite and positive
   */
  public void setStep(double value) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException("Scrollbar step must be finite and positive: " + value);
    }
    step = value;
  }

  /**
   * Installs or removes the listener notified after user-driven changes.
   *
   * @param value the listener, or {@code null} to remove it
   */
  public void setOnChanged(@Nullable DoubleConsumer value) {
    onChanged = value;
  }

  /**
   * Reports whether the pointer is currently over the scrollbar.
   *
   * @return whether the scrollbar is hovered
   */
  public boolean hovered() {
    return hovered;
  }

  /**
   * Reports whether the scrollbar currently owns keyboard focus.
   *
   * @return whether the scrollbar is focused
   */
  public boolean focused() {
    return focused;
  }

  /**
   * Reports whether a pointer drag is currently moving the thumb.
   *
   * @return whether the scrollbar is being dragged
   */
  public boolean dragging() {
    return dragging;
  }

  /**
   * Enables or disables keyboard focus for this scrollbar.
   *
   * @param value whether the scrollbar should accept focus
   */
  public void setFocusable(boolean value) {
    focusable = value;
    if (!value) {
      focused = false;
    }
  }

  /**
   * Returns the minimum rendered thumb size.
   *
   * @return minimum thumb size in logical units
   */
  public float minimumThumbSize() {
    return minimumThumbSize;
  }

  /**
   * Sets the minimum rendered thumb size.
   *
   * @param value the finite, non-negative minimum size
   * @throws IllegalArgumentException if {@code value} is negative or not finite
   */
  public void setMinimumThumbSize(float value) {
    if (!Float.isFinite(value) || value < 0.0F) {
      throw new IllegalArgumentException("Scrollbar minimum thumb size must be finite and non-negative: "
          + value);
    }
    minimumThumbSize = value;
  }

  @Override
  protected ElementRenderer defaultRenderer() {
    return ScrollBarRenderer.INSTANCE;
  }

  @Override
  public boolean onMouseMove(float x, float y) {
    if (dragging) {
      updateFromPointer(axis(x, y) - dragOffset);
    }
    return true;
  }

  @Override
  public boolean onMouseButton(float x, float y, KeyCode button, KeyAction action, int modifiers) {
    if (button != KeyCode.MOUSE_LEFT || action == KeyAction.REPEAT) {
      return false;
    }
    float pointer = axis(x, y);
    Rectangle localThumb = localThumb();
    float thumbStart = orientation == Orientation.HORIZONTAL
        ? localThumb.minX() : localThumb.minY();
    float thumbEnd = orientation == Orientation.HORIZONTAL
        ? localThumb.maxX() : localThumb.maxY();
    if (action == KeyAction.PRESS) {
      dragging = true;
      if (pointer >= thumbStart && pointer <= thumbEnd) {
        dragOffset = pointer - thumbStart;
      } else {
        dragOffset = thumbLength() * 0.5F;
        updateFromPointer(pointer - dragOffset);
      }
    } else {
      dragging = false;
    }
    return true;
  }

  @Override
  public boolean onScroll(float x, float y, double deltaX, double deltaY) {
    double delta = orientation == Orientation.VERTICAL ? deltaY : deltaX;
    if (delta == 0.0) {
      delta = orientation == Orientation.VERTICAL ? deltaX : deltaY;
    }
    if (delta != 0.0) {
      updateValue(value - Math.copySign(step, delta), true);
    }
    return true;
  }

  @Override
  public boolean onKey(KeyCode key, KeyAction action, int modifiers) {
    if (action == KeyAction.RELEASE) {
      return false;
    }
    if (key == KeyCode.PAGE_UP) {
      updateValue(value - pageSize, true);
      return true;
    }
    if (key == KeyCode.PAGE_DOWN) {
      updateValue(value + pageSize, true);
      return true;
    }
    if (key == KeyCode.HOME) {
      updateValue(minimum, true);
      return true;
    }
    if (key == KeyCode.END) {
      updateValue(maximum, true);
      return true;
    }
    boolean decrease = key == KeyCode.LEFT && orientation == Orientation.HORIZONTAL
        || key == KeyCode.UP && orientation == Orientation.VERTICAL;
    boolean increase = key == KeyCode.RIGHT && orientation == Orientation.HORIZONTAL
        || key == KeyCode.DOWN && orientation == Orientation.VERTICAL;
    if (decrease || increase) {
      updateValue(value + (increase ? step : -step), true);
      return true;
    }
    return false;
  }

  @Override
  public void onPointerCancel(KeyCode button) {
    dragging = false;
  }

  @Override
  public void onPointerEnter() {
    hovered = true;
  }

  @Override
  public void onPointerExit() {
    hovered = false;
  }

  @Override
  public boolean acceptsFocus() {
    return focusable;
  }

  @Override
  public void onFocusChanged(boolean value) {
    focused = value;
  }

  /**
   * Returns the thumb bounds translated into the supplied render area.
   *
   * @param area the scrollbar's absolute render area
   * @return the absolute thumb bounds
   */
  public Rectangle thumbBounds(Rectangle area) {
    Rectangle local = localThumb();
    return local.translate(area.minX(), area.minY());
  }

  private Rectangle localThumb() {
    float length = thumbLength();
    float travel = Math.max(0.0F, axisLength() - length);
    float position = travel * normalizedValue();
    return orientation == Orientation.HORIZONTAL
        ? Rectangle.of(position, 0.0F, length, bounds().height())
        : Rectangle.of(0.0F, position, bounds().width(), length);
  }

  private float thumbLength() {
    double extent = maximum - minimum;
    double fraction = extent == 0.0 ? 1.0 : pageSize / (pageSize + extent);
    return Math.min(axisLength(), Math.max(minimumThumbSize, (float) (axisLength() * fraction)));
  }

  private void updateFromPointer(float thumbStart) {
    float travel = axisLength() - thumbLength();
    double normalized = travel <= 0.0F ? 0.0 : Math.clamp(thumbStart / travel, 0.0F, 1.0F);
    updateValue(minimum + normalized * (maximum - minimum), true);
  }

  private void updateValue(double candidate, boolean notify) {
    if (!Double.isFinite(candidate)) {
      throw new IllegalArgumentException("Scrollbar value must be finite: " + candidate);
    }
    double clamped = Math.clamp(candidate, minimum, maximum);
    if (Double.compare(value, clamped) == 0) {
      return;
    }
    value = clamped;
    if (notify && onChanged != null) {
      onChanged.accept(value);
    }
  }

  private float normalizedValue() {
    return maximum == minimum ? 0.0F : (float) ((value - minimum) / (maximum - minimum));
  }

  private float axis(float x, float y) {
    return orientation == Orientation.HORIZONTAL ? x : y;
  }

  private float axisLength() {
    return orientation == Orientation.HORIZONTAL ? bounds().width() : bounds().height();
  }

  /**
   * Describes the axis along which a scrollbar moves.
   *
   * <p>The orientation also determines which pointer, wheel, and keyboard coordinates are used.
   */
  public enum Orientation {
    /** Scrolls along the horizontal axis. */
    HORIZONTAL,
    /** Scrolls along the vertical axis. */
    VERTICAL
  }
}
