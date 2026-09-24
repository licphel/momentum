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
import io.viki.momentum.gfx.ui.render.SliderRenderer;
import io.viki.momentum.input.KeyAction;
import io.viki.momentum.input.KeyCode;
import io.viki.momentum.math.shape.Rectangle;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * Represents a horizontal slider for either numeric values or discrete text options.
 *
 * <p>Numeric sliders snap pointer, wheel, and keyboard changes to a configurable step; option
 * sliders map integral positions to immutable labels. The control is mutable and not thread-safe.
 */
public final class Slider extends Element {
  private final double minimum;
  private final double maximum;
  private final List<String> options;
  private double value;
  private double step;
  private boolean enabled = true;
  private boolean hovered;
  private boolean focused;
  private boolean dragging;
  private float trackThickness = 1.0F;
  private float thumbWidth = 2.0F;
  private float trackInset = 3.0F;
  private float valueGap = 4.0F;
  private @Nullable DoubleConsumer onChanged;
  private @Nullable IntConsumer onSelectionChanged;
  private @Nullable Function<String, String> valueFormatter;

  /**
   * Creates a numeric slider.
   *
   * @param bounds the slider's local bounds
   * @param minimum the inclusive numeric lower bound
   * @param maximum the exclusive numeric upper bound for range validation
   * @param value the initial numeric value
   * @throws IllegalArgumentException if the range is not finite and increasing
   */
  public Slider(Rectangle bounds, double minimum, double maximum, double value) {
    super(bounds);
    if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum >= maximum) {
      throw new IllegalArgumentException("Slider range must be finite and increasing: "
          + minimum + ".." + maximum);
    }
    this.minimum = minimum;
    this.maximum = maximum;
    options = List.of();
    step = (maximum - minimum) / 100.0;
    this.value = snap(value);
  }

  /**
   * Creates a discrete option slider.
   *
   * @param bounds the slider's local bounds
   * @param options the non-empty option labels
   * @param selectedIndex the initial option index
   * @throws IllegalArgumentException if {@code options} is empty
   * @throws IndexOutOfBoundsException if {@code selectedIndex} is invalid
   */
  public Slider(Rectangle bounds, List<String> options, int selectedIndex) {
    super(bounds);
    if (options.isEmpty()) {
      throw new IllegalArgumentException("Option slider requires at least one option");
    }
    this.options = List.copyOf(options);
    minimum = 0.0;
    maximum = options.size() - 1.0;
    step = 1.0;
    if (selectedIndex < 0 || selectedIndex >= options.size()) {
      throw new IndexOutOfBoundsException("Selected option index out of range: " + selectedIndex);
    }
    value = selectedIndex;
  }

  /**
   * Returns the lower end of the slider's value domain.
   *
   * @return numeric lower bound, or zero for an option slider
   */
  public double minimum() {
    return minimum;
  }

  /**
   * Returns the upper end of the slider's value domain.
   *
   * @return numeric upper bound, or the last option position
   */
  public double maximum() {
    return maximum;
  }

  /**
   * Returns the current numeric value or option position.
   *
   * @return current slider value
   */
  public double value() {
    return value;
  }

  /**
   * Sets the value without notifying change listeners.
   *
   * @param newValue the candidate numeric or option-position value
   * @throws IllegalArgumentException if {@code newValue} is not finite
   */
  public void setValue(double newValue) {
    updateValue(newValue, false);
  }

  /**
   * Returns the snap increment used by numeric slider movement.
   *
   * @return slider step
   */
  public double step() {
    return step;
  }

  /**
   * Sets the numeric snap increment.
   *
   * @param newStep the finite, positive step
   * @throws IllegalStateException if this is an option slider
   * @throws IllegalArgumentException if {@code newStep} is not finite and positive
   */
  public void setStep(double newStep) {
    if (isOptionSlider()) {
      throw new IllegalStateException("Option sliders always use one option per step");
    }
    if (!Double.isFinite(newStep) || newStep <= 0.0) {
      throw new IllegalArgumentException("Slider step must be finite and positive: " + newStep);
    }
    step = newStep;
    updateValue(value, false);
  }

  /**
   * Reports whether this slider represents discrete text options.
   *
   * @return whether this is an option slider
   */
  public boolean isOptionSlider() {
    return !options.isEmpty();
  }

  /**
   * Returns the immutable option list represented by this slider.
   *
   * @return option labels, or an empty list for numeric sliders
   */
  public List<String> options() {
    return options;
  }

  /**
   * Returns the selected option index.
   *
   * @return the selected option index
   * @throws IllegalStateException if this is a numeric slider
   */
  public int selectedIndex() {
    ensureOptionSlider();
    return (int) value;
  }

  /**
   * Returns the selected option label.
   *
   * @return the selected option text
   * @throws IllegalStateException if this is a numeric slider
   */
  public String selectedOption() {
    return options.get(selectedIndex());
  }

  /**
   * Selects an option without notifying the selection listener.
   *
   * @param index the option index
   * @throws IllegalStateException if this is a numeric slider
   * @throws IndexOutOfBoundsException if {@code index} is invalid
   */
  public void setSelectedIndex(int index) {
    ensureOptionSlider();
    if (index < 0 || index >= options.size()) {
      throw new IndexOutOfBoundsException("Selected option index out of range: " + index);
    }
    updateValue(index, false);
  }

  /**
   * Reports whether this slider accepts pointer, wheel, and keyboard input.
   *
   * @return whether the slider is enabled
   */
  public boolean enabled() {
    return enabled;
  }

  /**
   * Enables or disables input handling.
   *
   * @param value whether the slider should accept input
   */
  public void setEnabled(boolean value) {
    enabled = value;
    if (!value) {
      dragging = false;
    }
  }

  /**
   * Installs or removes the numeric value listener.
   *
   * @param value the listener, or {@code null} to remove it
   */
  public void setOnChanged(@Nullable DoubleConsumer value) {
    onChanged = value;
  }

  /**
   * Installs or removes the discrete selection listener.
   *
   * @param value the listener, or {@code null} to remove it
   */
  public void setOnSelectionChanged(@Nullable IntConsumer value) {
    onSelectionChanged = value;
  }

  /**
   * Installs or removes the displayed-value formatter.
   *
   * @param value the formatter, or {@code null} to use the raw value
   */
  public void setValueFormatter(@Nullable Function<String, String> value) {
    valueFormatter = value;
  }

  /**
   * Sets the rendered track thickness.
   *
   * @param value the finite, non-negative thickness
   * @throws IllegalArgumentException if the value is invalid
   */
  public void setTrackThickness(float value) {
    trackThickness = requireNonNegative("Slider track thickness", value);
  }

  /**
   * Sets the rendered thumb width.
   *
   * @param value the finite, non-negative width
   * @throws IllegalArgumentException if the value is invalid
   */
  public void setThumbWidth(float value) {
    thumbWidth = requireNonNegative("Slider thumb width", value);
  }

  /**
   * Sets the inset between the slider bounds and track.
   *
   * @param value the finite, non-negative inset
   * @throws IllegalArgumentException if the value is invalid
   */
  public void setTrackInset(float value) {
    trackInset = requireNonNegative("Slider track inset", value);
  }

  /**
   * Sets the gap between the track and formatted value text.
   *
   * @param value the finite, non-negative gap
   * @throws IllegalArgumentException if the value is invalid
   */
  public void setValueGap(float value) {
    valueGap = requireNonNegative("Slider value gap", value);
  }

  /**
   * Reports whether the pointer is currently over the slider.
   *
   * @return whether the slider is hovered
   */
  public boolean hovered() {
    return hovered;
  }

  /**
   * Reports whether the slider thumb is currently being dragged.
   *
   * @return whether the slider is dragging
   */
  public boolean dragging() {
    return dragging;
  }

  /**
   * Reports whether the slider currently owns keyboard focus.
   *
   * @return whether the slider is focused
   */
  public boolean focused() {
    return focused;
  }

  /**
   * Returns the current normalized position for rendering.
   *
   * @return value in the inclusive range {@code [0, 1]}
   */
  public float normalizedValueForRender() {
    return normalizedValue();
  }

  /**
   * Returns the configured track thickness for rendering.
   *
   * @return track thickness in logical units
   */
  public float trackThicknessForRender() {
    return trackThickness;
  }

  /**
   * Returns the configured thumb width for rendering.
   *
   * @return thumb width in logical units
   */
  public float thumbWidthForRender() {
    return thumbWidth;
  }

  /**
   * Returns the gap between the track and its formatted value label.
   *
   * @return value-label gap in logical units
   */
  public float valueGapForRender() {
    return valueGap;
  }

  /**
   * Returns the track area after applying the configured inset.
   *
   * @param area the slider render area
   * @return the inset track area
   */
  public Rectangle trackAreaForRender(Rectangle area) {
    return trackArea(area);
  }

  @Override
  protected ElementRenderer defaultRenderer() {
    return SliderRenderer.INSTANCE;
  }

  @Override
  public boolean onMouseMove(float x, float y) {
    if (enabled && dragging) {
      updateFromPointer(x);
    }
    return enabled;
  }

  @Override
  public boolean onMouseButton(float x, float y, KeyCode button, KeyAction action, int modifiers) {
    if (!enabled || button != KeyCode.MOUSE_LEFT) {
      return false;
    }
    if (action == KeyAction.PRESS) {
      dragging = true;
      updateFromPointer(x);
      return true;
    }
    if (action == KeyAction.RELEASE) {
      dragging = false;
      // The press and subsequent move events already determine the value. Do not sample the
      // release coordinates again: a captured pointer may be released outside this element.
      return true;
    }
    return false;
  }

  @Override
  public boolean onScroll(float x, float y, double deltaX, double deltaY) {
    if (!enabled) {
      return false;
    }
    double delta = deltaY != 0.0 ? deltaY : deltaX;
    if (delta == 0.0) {
      return true;
    }
    updateValue(value - Math.copySign(step, delta), true);
    return true;
  }

  @Override
  public boolean onKey(KeyCode key, KeyAction action, int modifiers) {
    if (!enabled || (action != KeyAction.PRESS && action != KeyAction.REPEAT)) {
      return false;
    }
    if (key == KeyCode.LEFT || key == KeyCode.DOWN) {
      updateValue(value - step, true);
      return true;
    }
    if (key == KeyCode.RIGHT || key == KeyCode.UP) {
      updateValue(value + step, true);
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
    return enabled;
  }

  @Override
  public void onFocusChanged(boolean value) {
    focused = value;
  }

  private void updateFromPointer(float x) {
    Rectangle track = trackArea(Rectangle.of(0.0F, 0.0F, bounds().width(), bounds().height()));
    double ratio = track.width() == 0.0F || maximum == minimum ? 0.0
        : (x - track.minX()) / track.width();
    updateValue(minimum + Math.clamp(ratio, 0.0, 1.0) * (maximum - minimum), true);
  }

  private Rectangle trackArea(Rectangle area) {
    float inset = trackInset;
    float effectiveInset = Math.min(inset, area.width() * 0.5F);
    float available = Math.max(1.0F, area.width() - effectiveInset * 2.0F);
    return Rectangle.of(area.minX() + effectiveInset, area.minY(), available, area.height());
  }

  private void updateValue(double newValue, boolean notify) {
    double clamped = snap(newValue);
    if (Double.compare(value, clamped) == 0) {
      return;
    }
    value = clamped;
    if (notify && onChanged != null) {
      onChanged.accept(value);
    }
    if (notify && isOptionSlider()) {
      if (onSelectionChanged != null) {
        onSelectionChanged.accept((int) value);
      }
    }
  }

  private double snap(double candidate) {
    if (!Double.isFinite(candidate)) {
      throw new IllegalArgumentException("Slider value must be finite: " + candidate);
    }
    double snapped = minimum + Math.round((candidate - minimum) / step) * step;
    return Math.clamp(snapped, minimum, maximum);
  }

  private float normalizedValue() {
    return maximum == minimum ? 0.0F : (float) ((value - minimum) / (maximum - minimum));
  }

  /**
   * Returns the value formatted for display.
   *
   * @return the raw or formatted numeric/option value
   * @throws IllegalArgumentException if a formatter returns {@code null}
   */
  public String displayValue() {
    String raw = isOptionSlider() ? selectedOption() : Double.toString(value);
    Function<String, String> formatter = valueFormatter;
    if (formatter == null) {
      return raw;
    }
    String formatted = formatter.apply(raw);
    if (formatted == null) {
      throw new IllegalArgumentException("Slider value formatter returned null");
    }
    return formatted;
  }

  private void ensureOptionSlider() {
    if (!isOptionSlider()) {
      throw new IllegalStateException("This slider contains numeric values, not text options");
    }
  }

  private static float requireNonNegative(String name, float value) {
    if (!Float.isFinite(value) || value < 0.0F) {
      throw new IllegalArgumentException(name + " must be finite and non-negative: " + value);
    }
    return value;
  }
}
