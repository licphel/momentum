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

package io.viki.momentum.input.binding;

import java.util.*;

/**
 * Owner-thread interactive binding capture. Existing held controls are ignored until released.
 * Collects a chord until any captured control is released; callers explicitly apply or discard the result.
 * Does not mutate the active profile and never treats a particular physical key as cancellation.
 */
public final class InputRebind {
  private final List<ControlPath> controls;
  private final BindingDefinition original;
  private final boolean[] blocked;
  private final Map<String, ControlPath> captured = new LinkedHashMap<>();
  private boolean finished;

  /**
   * Starts capturing a replacement for a binding.
   *
   * @param original binding whose thresholds and identifier are retained
   * @param input    current input used to ignore controls already held
   */
  public InputRebind(BindingDefinition original, InputSnapshot input) {
    this.original = original;
    boolean wheel = !original.controls().isEmpty() && ControlPath.parse(original.controls().get(0)).isWheel();
    controls = controls(input, wheel);
    blocked = new boolean[controls.size()];
    for (int i = 0; i < blocked.length; i++) {
      blocked[i] = Math.abs(controls.get(i).value(input)) >= original.pressPoint();
    }
  }

  /**
   * Enumerates mapped controls and raw joystick controls available for capture.
   *
   * @param input current snapshot used to inspect available joystick hardware
   * @param wheel whether the replacement may use two-dimensional wheel controls
   * @return immutable list of capturable controls
   */
  private static List<ControlPath> controls(InputSnapshot input, boolean wheel) {
    List<ControlPath> controls = new ArrayList<>();
    for (KeyCode key : KeyCode.values()) {
      controls.add(ControlPath.parse("<Keyboard>/" + key.name()));
    }
    for (String name : new String[] {"leftButton", "rightButton", "middleButton", "button4", "button5", "button6", "button7", "button8"}) {
      controls.add(ControlPath.parse("<Mouse>/" + name));
    }
    for (GamepadButton button : GamepadButton.values()) {
      controls.add(ControlPath.parse("<Gamepad>/" + button.name()));
    }
    if (wheel) {
      controls.add(ControlPath.parse("<Mouse>/scroll"));
      controls.add(ControlPath.parse("<Gamepad>/leftStick"));
      controls.add(ControlPath.parse("<Gamepad>/rightStick"));
    }
    for (String stick : wheel ? new String[0] : new String[] {"leftStick", "rightStick"}) {
      for (String direction : new String[] {"left", "right", "up", "down"}) {
        controls.add(ControlPath.parse("<Gamepad>/" + stick + "/" + direction));
      }
    }
    controls.add(ControlPath.parse("<Gamepad>/leftTrigger"));
    controls.add(ControlPath.parse("<Gamepad>/rightTrigger"));
    for (String direction : wheel ? new String[0] : new String[] {"left", "right", "up", "down"}) {
      controls.add(ControlPath.parse("<Mouse>/scroll/" + direction));
    }
    // Mapped pads already have meaningful controls; raw indices are only offered for unmapped hardware.
    if (!input.gamepad().connected()) {
      JoystickState joystick = input.joystick();
      for (int i = 0; i < joystick.buttonCount(); i++) {
        controls.add(ControlPath.parse("<Joystick>/button/" + i));
      }
      for (int i = 0; i < joystick.axisCount(); i++) {
        for (String direction : new String[] {"positive", "negative"}) {
          controls.add(ControlPath.parse("<Joystick>/axis/" + i + "/" + direction));
        }
      }
      for (int i = 0; i < joystick.hatCount(); i++) {
        for (String direction : new String[] {"left", "right", "up", "down"}) {
          controls.add(ControlPath.parse("<Joystick>/hat/" + i + "/" + direction));
        }
      }
    }
    return List.copyOf(controls);
  }

  /**
   * Returns whether capture has completed or was canceled.
   *
   * @return whether no more controls will be captured
   */
  public boolean finished() {
    return finished;
  }

  /** Cancels capture and discards all controls collected so far. */
  public void cancel() {
    finished = true;
    captured.clear();
  }

  /**
   * Advances capture using the current input state.
   *
   * @param input current input snapshot
   * @return the captured binding once complete, or an empty optional while capturing
   */
  public Optional<BindingDefinition> update(InputSnapshot input) {
    if (finished) {
      return Optional.empty();
    }
    for (int i = 0; i < blocked.length; i++) {
      ControlPath control = controls.get(i);
      float strength = Math.abs(control.value(input));
      if (blocked[i]) {
        if (strength <= original.releasePoint()) {
          blocked[i] = false;
        }
        continue;
      }
      if (strength >= original.pressPoint() || control.pressed(input)) {
        // A Wheel action has one two-dimensional source, optionally gated by buttons.
        if (control.isWheel() && captured.values().stream().anyMatch(value -> value.isWheel()
            && !value.channel().equals(control.channel()))) {
          continue;
        }
        captured.putIfAbsent(control.channel(), control);
      }
    }
    for (ControlPath control : captured.values()) {
      if (Math.abs(control.value(input)) <= original.releasePoint() || control.released(input)) {
        List<String> paths = captured.values().stream()
            .sorted(Comparator.comparing(value -> !value.isWheel())).map(ControlPath::path).toList();
        boolean requiresWheel = !original.controls().isEmpty()
            && ControlPath.parse(original.controls().get(0)).isWheel();
        if (requiresWheel && captured.values().stream().noneMatch(ControlPath::isWheel)) {
          continue;
        }
        finished = true;
        // Captured modifier keys already participate in the chord.
        return Optional.of(new BindingDefinition(original.id(), paths,
            original.pressPoint(), original.releasePoint(), InputModifiers.ANY));
      }
    }
    return Optional.empty();
  }
}
