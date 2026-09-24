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
import io.viki.momentum.input.KeyBinding;
import io.viki.momentum.input.KeyCode;
import io.viki.momentum.input.KeyMatch;
import io.viki.momentum.input.InputSnapshot;
import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.gfx.ui.render.ButtonRenderer;
import io.viki.momentum.gfx.ui.render.ElementRenderer;
import org.jspecify.annotations.Nullable;


/**
 * Represents an activatable logical-space button with pointer and keyboard input handling.
 *
 * <p>The button tracks hover, focus, and pressed state and invokes an optional callback after a
 * completed activation. State is mutable and must be accessed from its owning UI thread.
 */
public final class Button extends Element {
  private State state = State.IDLE;
  private String label = "";
  private boolean enabled = true;
  private boolean hovered;
  private boolean focused;
  private boolean pointerPressed;
  private boolean keyboardPressed;
  private @Nullable KeyCode pointerKey;
  private @Nullable KeyCode keyboardKey;
  private @Nullable KeyBinding activationBinding;
  private @Nullable Runnable onClick;

  /**
   * Creates a button with the default label and the supplied bounds.
   *
   * @param bounds the button's local bounds
   */
  public Button(Rectangle bounds) {
    super(bounds);
  }
  /**
   * Returns the label presented by this button.
   *
   * <p>The value is read by the active renderer each time the button is drawn.
   *
   * @return current button label
   */
  public String label() {
    return label;
  }

  /**
   * Changes the text displayed by the button renderer.
   *
   * @param value the new label
   */
  public void setLabel(String value) {
    label = value;
  }

  /**
   * Creates the conventional mouse, Enter, and Space activation binding for one input snapshot.
   *
   * @param snapshot the input snapshot used to build transition matches
   * @return a binding that recognizes the standard button activation keys
   */
  public static KeyBinding makeDefaultActivationKeyBinding(InputSnapshot snapshot) {
    return new KeyBinding("button.activate",
        KeyMatch.of(snapshot.key(KeyCode.MOUSE_LEFT)),
        KeyMatch.of(snapshot.key(KeyCode.ENTER)),
        KeyMatch.of(snapshot.key(KeyCode.KP_ENTER)),
        KeyMatch.of(snapshot.key(KeyCode.SPACE)));
  }
  /**
   * Returns the interaction state currently exposed to the renderer.
   *
   * <p>Disabled buttons report {@link State#DISABLED} even when their stored state was previously
   * hovered or pressed.
   *
   * @return current logical button state
   */
  public State state() {
    return enabled ? state : State.DISABLED;
  }

  /**
   * Reports whether the button accepts activation input.
   *
   * @return whether the button is enabled
   */
  public boolean enabled() {
    return enabled;
  }

  /**
   * Reports whether the button currently owns keyboard focus.
   *
   * @return whether the button is focused
   */
  public boolean focused() {
    return focused;
  }

  @Override
  protected ElementRenderer defaultRenderer() {
    return ButtonRenderer.INSTANCE;
  }

  /**
   * Enables or disables this button.
   *
   * @param value whether input should be accepted
   */
  public void setEnabled(boolean value) {
    enabled = value;
    if (!value) {
      hovered = false;
      pointerPressed = false;
      keyboardPressed = false;
      pointerKey = null;
      keyboardKey = null;
    }
    refreshState();
  }

  /**
   * Installs or removes the callback invoked after a successful activation.
   *
   * @param value the callback, or {@code null} to remove it
   */
  public void setOnClick(@Nullable Runnable value) {
    onClick = value;
  }

  /**
   * Installs or removes a custom activation binding.
   *
   * @param value the binding, or {@code null} to use default activation behavior
   */
  public void setActivationBinding(@Nullable KeyBinding value) {
    activationBinding = value;
  }

  @Override
  public boolean onMouseMove(float x, float y) {
    return enabled;
  }

  @Override
  public boolean onMouseButton(float x, float y, KeyCode button, KeyAction action, int modifiers) {
    if (!enabled || button.mouseId() < 0 || action == KeyAction.REPEAT) {
      return false;
    }
    if (action == KeyAction.PRESS) {
      KeyBinding binding = activationBinding();
      if (binding == null
          ? button != KeyCode.MOUSE_LEFT
          : !binding.transitioned()) {
        return false;
      }
      pointerPressed = true;
      pointerKey = button;
      refreshState();
      return true;
    }
    if (!pointerPressed || pointerKey != button) {
      return false;
    }
    pointerPressed = false;
    pointerKey = null;
    boolean clicked = containsLocal(x, y);
    refreshState();
    runClick(clicked);
    return true;
  }

  @Override
  public boolean onKey(KeyCode key, KeyAction action, int modifiers) {
    if (!enabled) {
      return false;
    }
    if (action == KeyAction.PRESS) {
      KeyBinding binding = activationBinding();
      if (binding == null
          ? !isDefaultKeyboardActivation(key)
          : !binding.transitioned()) {
        return false;
      }
      keyboardPressed = true;
      keyboardKey = key;
      refreshState();
      return true;
    }
    if (!keyboardPressed || keyboardKey != key) {
      return false;
    }
    if (action == KeyAction.RELEASE) {
      keyboardPressed = false;
      keyboardKey = null;
      refreshState();
      runClick(true);
    }
    return true;
  }

  @Override
  public void onPointerEnter() {
    hovered = true;
    refreshState();
  }

  @Override
  public void onPointerExit() {
    hovered = false;
    refreshState();
  }

  @Override
  public void onPointerCancel(KeyCode button) {
    if (pointerKey == button) {
      pointerPressed = false;
      pointerKey = null;
      refreshState();
    }
  }

  @Override
  public boolean acceptsFocus() {
    return enabled;
  }

  @Override
  public void onFocusChanged(boolean focused) {
    this.focused = focused;
  }

  @Override
  public void onKeyCancel(KeyCode key) {
    if (keyboardKey == key) {
      keyboardPressed = false;
      keyboardKey = null;
      refreshState();
    }
  }

  private void refreshState() {
    if (pointerPressed || keyboardPressed) {
      state = State.PRESSED;
    } else {
      state = hovered ? State.HOVERED : State.IDLE;
    }
  }

  private void runClick(boolean clicked) {
    if (clicked && onClick != null) {
      onClick.run();
    }
  }

  private @Nullable KeyBinding activationBinding() {
    return activationBinding;
  }

  private static boolean isDefaultKeyboardActivation(KeyCode key) {
    return key == KeyCode.ENTER || key == KeyCode.KP_ENTER || key == KeyCode.SPACE;
  }

  /**
   * Describes the interaction state used to select a button's visual treatment.
   *
   * <p>The state reflects the combination of enablement, pointer hover, and activation input.
   */
  public enum State {
    /** The button is enabled and idle. */
    IDLE,
    /** The pointer is over the button. */
    HOVERED,
    /** An activation input is being held. */
    PRESSED,
    /** The button is disabled. */
    DISABLED
  }
}
