package io.viki.momentum.input;

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
   * @param input current input used to ignore controls already held
   */
  public InputRebind(BindingDefinition original, InputSnapshot input) {
    this.original = original;
    controls = controls(input);
    blocked = new boolean[controls.size()];
    for (int i = 0; i < blocked.length; i++) {
      blocked[i] = Math.abs(controls.get(i).value(input)) >= original.pressPoint();
    }
  }

  /** Enumerates mapped controls and raw joystick controls available for capture. */
  private static List<ControlPath> controls(InputSnapshot input) {
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
    for (String stick : new String[] {"leftStick", "rightStick"}) {
      for (String direction : new String[] {"left", "right", "up", "down"}) {
        controls.add(ControlPath.parse("<Gamepad>/" + stick + "/" + direction));
      }
    }
    controls.add(ControlPath.parse("<Gamepad>/leftTrigger"));
    controls.add(ControlPath.parse("<Gamepad>/rightTrigger"));
    for (String direction : new String[] {"left", "right", "up", "down"}) {
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
   * Returns whether capture has completed or was cancelled.
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
    for (ControlPath control : captured.values()) {
      if (Math.abs(control.value(input)) <= original.releasePoint()) {
        finished = true;
        return Optional.of(new BindingDefinition(original.id(), captured.values().stream().map(ControlPath::path).toList(),
            original.pressPoint(), original.releasePoint(), original.modifiers()));
      }
    }
    for (int i = 0; i < blocked.length; i++) {
      ControlPath control = controls.get(i);
      float strength = Math.abs(control.value(input));
      if (blocked[i]) {
        if (strength <= original.releasePoint()) {
          blocked[i] = false;
        }
      } else if (strength >= original.pressPoint()) {
        captured.putIfAbsent(control.channel(), control);
      }
    }
    return Optional.empty();
  }
}
