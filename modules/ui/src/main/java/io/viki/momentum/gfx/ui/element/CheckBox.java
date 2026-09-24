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
import io.viki.momentum.gfx.ui.render.CheckBoxRenderer;
import io.viki.momentum.input.KeyAction;
import io.viki.momentum.input.KeyCode;
import io.viki.momentum.math.shape.Rectangle;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Represents a toggleable checkbox with pointer, keyboard, hover, and focus state.
 *
 * <p>The control exposes a change callback for committed toggles and leaves presentation to its
 * renderer. Its mutable state is intended for one owning UI thread.
 */
public final class CheckBox extends Element {
  private static final float DEFAULT_CHECK_MARK_INSET = 0.10F;

  private String label;
  private boolean checked;
  private boolean enabled = true;
  private boolean hovered;
  private boolean focused;
  private boolean pressed;
  private float boxSize = 11.0F;
  private float checkMarkInset = DEFAULT_CHECK_MARK_INSET;
  private @Nullable Consumer<Boolean> onChanged;

  /**
   * Creates a checkbox with the supplied label, initial value, and bounds.
   *
   * @param bounds the checkbox's local bounds
   * @param label the text displayed beside the box
   * @param checked the initial checked state
   */
  public CheckBox(Rectangle bounds, String label, boolean checked) {
    super(bounds);
    this.label = label;
    this.checked = checked;
  }

  /**
   * Reports the current checked state.
   *
   * <p>Programmatic changes do not invoke the change listener unless the user activates the
   * control.
   *
   * @return whether the checkbox is checked
   */
  public boolean checked() {
    return checked;
  }

  /**
   * Changes the checked state without invoking the change callback.
   *
   * @param value the new checked state
   */
  public void setChecked(boolean value) {
    setChecked(value, false);
  }

  /**
   * Returns the label displayed beside the checkbox.
   *
   * @return current checkbox label
   */
  public String label() {
    return label;
  }

  /**
   * Reports whether the pointer is currently over the checkbox.
   *
   * @return whether the checkbox is hovered
   */
  public boolean hovered() {
    return hovered;
  }

  /**
   * Reports whether an activation input is currently held.
   *
   * @return whether the checkbox is pressed
   */
  public boolean pressed() {
    return pressed;
  }

  /**
   * Reports whether the checkbox currently owns keyboard focus.
   *
   * @return whether the checkbox is focused
   */
  public boolean focused() {
    return focused;
  }

  /**
   * Returns the box size supplied to the renderer.
   *
   * @return rendered checkbox box size
   */
  public float boxSizeForRender() {
    return boxSize();
  }

  /**
   * Returns the inset used to size the checked mark inside the box.
   *
   * @return checked-mark inset as a fraction of the box size
   */
  public float checkMarkInsetForRender() {
    return checkMarkInset;
  }

  /**
   * Sets the rendered checkbox box size.
   *
   * @param value the finite, non-negative box size
   * @throws IllegalArgumentException if {@code value} is negative or not finite
   */
  public void setBoxSize(float value) {
    if (!Float.isFinite(value) || value < 0.0F) {
      throw new IllegalArgumentException("Checkbox box size must be finite and non-negative: "
          + value);
    }
    boxSize = value;
  }

  /**
   * Sets the fractional inset of the checked mark.
   *
   * @param value the inset in the inclusive range {@code [0, 0.5]}
   * @throws IllegalArgumentException if the value is outside the supported range
   */
  public void setCheckMarkInset(float value) {
    if (!Float.isFinite(value) || value < 0.0F || value > 0.5F) {
      throw new IllegalArgumentException("Checkbox check mark inset must be in [0, 0.5]: "
          + value);
    }
    checkMarkInset = value;
  }

  @Override
  protected ElementRenderer defaultRenderer() {
    return CheckBoxRenderer.INSTANCE;
  }

  /**
   * Changes the label displayed beside the checkbox.
   *
   * @param value the new label
   */
  public void setLabel(String value) {
    label = value;
  }

  /**
   * Reports whether this checkbox accepts pointer and keyboard activation.
   *
   * @return whether the checkbox is enabled
   */
  public boolean enabled() {
    return enabled;
  }

  /**
   * Enables or disables input handling.
   *
   * @param value whether input should be accepted
   */
  public void setEnabled(boolean value) {
    enabled = value;
    if (!value) {
      pressed = false;
      hovered = false;
    }
  }

  /**
   * Installs or removes the listener notified after a user-committed toggle.
   *
   * @param value the listener, or {@code null} to remove it
   */
  public void setOnChanged(@Nullable Consumer<Boolean> value) {
    onChanged = value;
  }

  @Override
  public boolean onMouseMove(float x, float y) {
    return enabled;
  }

  @Override
  public boolean onMouseButton(float x, float y, KeyCode button, KeyAction action, int modifiers) {
    if (!enabled || button != KeyCode.MOUSE_LEFT || action == KeyAction.REPEAT) {
      return false;
    }
    if (action == KeyAction.PRESS) {
      pressed = true;
    } else if (pressed) {
      pressed = false;
      if (containsLocal(x, y)) {
        setChecked(!checked, true);
      }
    }
    return true;
  }

  @Override
  public boolean onKey(KeyCode key, KeyAction action, int modifiers) {
    if (!enabled || (key != KeyCode.SPACE && key != KeyCode.ENTER && key != KeyCode.KP_ENTER)) {
      return false;
    }
    if (action == KeyAction.PRESS) {
      pressed = true;
    } else if (action == KeyAction.RELEASE && pressed) {
      pressed = false;
      setChecked(!checked, true);
    }
    return true;
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
  public void onPointerCancel(KeyCode button) {
    pressed = false;
  }

  @Override
  public void onKeyCancel(KeyCode key) {
    pressed = false;
  }

  @Override
  public boolean acceptsFocus() {
    return enabled;
  }

  @Override
  public void onFocusChanged(boolean value) {
    focused = value;
  }

  private float boxSize() {
    return boxSize;
  }

  private void setChecked(boolean value, boolean notify) {
    if (checked == value) {
      return;
    }
    checked = value;
    if (notify && onChanged != null) {
      onChanged.accept(value);
    }
  }
}
