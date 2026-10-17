package io.viki.momentum.input;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * A compiled control path. Immutable and thread-safe; reading a snapshot requires its owner thread.
 * Paths are serialization identifiers, not display text. Values are signed axes or button strengths.
 */
public final class ControlPath {
  private static final KeyCode[] KEYS = KeyCode.values();
  private static final GamepadButton[] BUTTONS = GamepadButton.values();
  private static final GamepadAxis[] AXES = GamepadAxis.values();
  private final Device device;
  private final int index;
  private final int direction;
  private final String path;
  private final String channel;

  private ControlPath(Device device, int index, int direction, String path, String channel) {
    this.device = device;
    this.index = index;
    this.direction = direction;
    this.path = path;
    this.channel = channel;
  }

  /**
   * Parses a serialized control path.
   *
   * @param path serialized control path
   * @return the compiled path
   * @throws IllegalArgumentException if the path is unsupported or malformed
   */
  public static ControlPath parse(String path) {
    if (path.startsWith("<Keyboard>/")) {
      String name = path.substring(11);
      if (name.length() == 1 && Character.isDigit(name.charAt(0))) {
        name = "DIGIT_" + name;
      }
      KeyCode key = KeyCode.valueOf(snake(name));
      return new ControlPath(Device.KEYBOARD, key.ordinal(), 0, "<Keyboard>/" + key.name(), "key/" + key.name());
    }
    if (path.startsWith("<Mouse>/")) {
      String name = path.substring(8);
      MouseButton key = switch (name) {
        case "leftButton" -> MouseButton.LEFT;
        case "rightButton" -> MouseButton.RIGHT;
        case "middleButton" -> MouseButton.MIDDLE;
        case "button4" -> MouseButton.BUTTON_4;
        case "button5" -> MouseButton.BUTTON_5;
        case "button6" -> MouseButton.BUTTON_6;
        case "button7" -> MouseButton.BUTTON_7;
        case "button8" -> MouseButton.BUTTON_8;
        default -> null;
      };
      if (key != null) {
        return new ControlPath(Device.MOUSE, key.ordinal(), 0, path, "mouse/button/" + key.name());
      }
      if (name.startsWith("scroll/")) {
        return directional(Device.SCROLL, name.substring(7), "<Mouse>/scroll");
      }
    }
    if (path.startsWith("<Gamepad>/")) {
      String name = path.substring(10);
      GamepadButton button = switch (name) {
        case "buttonSouth" -> GamepadButton.A;
        case "buttonEast" -> GamepadButton.B;
        case "buttonWest" -> GamepadButton.X;
        case "buttonNorth" -> GamepadButton.Y;
        default -> button(name);
      };
      if (button != null) {
        return new ControlPath(Device.GAMEPAD_BUTTON, button.ordinal(), 0,
            "<Gamepad>/" + button.name(), "pad/button/" + button.name());
      }
      if (name.startsWith("leftStick/")) {
        return directional(Device.GAMEPAD_AXIS, name.substring(10), "<Gamepad>/leftStick");
      }
      if (name.startsWith("rightStick/")) {
        return directional(Device.GAMEPAD_AXIS, name.substring(11), "<Gamepad>/rightStick");
      }
      GamepadAxis axis = switch (name) {
        case "leftTrigger" -> GamepadAxis.LEFT_TRIGGER;
        case "rightTrigger" -> GamepadAxis.RIGHT_TRIGGER;
        default -> null;
      };
      if (axis != null) {
        return new ControlPath(Device.GAMEPAD_AXIS, axis.ordinal(), 0, path, "pad/axis/" + axis.name());
      }
    }
    if (path.startsWith("<Joystick>/")) {
      String[] parts = path.substring(11).split("/");
      if (parts.length < 2) {
        throw new IllegalArgumentException("Joystick path requires a control index: " + path);
      }
      int index = Integer.parseInt(parts[1]);
      if (index < 0) {
        throw new IllegalArgumentException("Negative joystick control index: " + path);
      }
      if (parts[0].equals("button") && parts.length == 2) {
        return new ControlPath(Device.JOYSTICK_BUTTON, index, 0, path, "joystick/button/" + index);
      }
      if (parts[0].equals("axis") && parts.length == 3) {
        int direction = switch (parts[2]) {
          case "positive" -> 1;
          case "negative" -> -1;
          default -> throw new IllegalArgumentException("Unknown joystick axis direction: " + path);
        };
        return new ControlPath(Device.JOYSTICK_AXIS, index, direction, path, "joystick/axis/" + index);
      }
      if (parts[0].equals("hat") && parts.length == 3) {
        int direction = switch (parts[2]) {
          case "up" -> JoystickState.HAT_UP;
          case "right" -> JoystickState.HAT_RIGHT;
          case "down" -> JoystickState.HAT_DOWN;
          case "left" -> JoystickState.HAT_LEFT;
          default -> throw new IllegalArgumentException("Unknown joystick hat direction: " + path);
        };
        return new ControlPath(Device.JOYSTICK_HAT, index, direction, path, "joystick/hat/" + index + "/" + direction);
      }
    }
    throw new IllegalArgumentException("Unsupported input control path: " + path);
  }

  /**
   * Compiles a directional axis path and assigns its sign and physical channel.
   *
   * @param device source device
   * @param direction named direction or neutral axis component
   * @param prefix canonical path prefix
   * @return compiled directional path
   */
  private static ControlPath directional(Device device, String direction, String prefix) {
    int sign = switch (direction) {
      case "left", "down" -> -1;
      case "right", "up" -> 1;
      case "x", "y" -> 0;
      default -> throw new IllegalArgumentException("Unknown axis direction: " + prefix + "/" + direction);
    };
    boolean horizontal = direction.equals("left") || direction.equals("right") || direction.equals("x");
    int index = device == Device.SCROLL ? (horizontal ? 0 : 1)
        : prefix.endsWith("leftStick") ? (horizontal ? GamepadAxis.LEFT_X.ordinal() : GamepadAxis.LEFT_Y.ordinal())
        : (horizontal ? GamepadAxis.RIGHT_X.ordinal() : GamepadAxis.RIGHT_Y.ordinal());
    return new ControlPath(device, index, sign, prefix + "/" + direction,
        device == Device.SCROLL ? "scroll/" + index : "pad/axis/" + AXES[index].name());
  }

  private static @Nullable GamepadButton button(String name) {
    try {
      return GamepadButton.valueOf(snake(name));
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private static String snake(String value) {
    return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
  }

  /**
   * Reads this control's current value from an input snapshot.
   *
   * @param input input snapshot to read
   * @return button strength, axis value, or scroll delta represented by this path
   */
  public float value(InputSnapshot input) {
    double value = switch (device) {
      case KEYBOARD -> input.keyboard().isDown(KEYS[index]) ? 1 : 0;
      case MOUSE -> input.mouse().isDown(MouseButton.fromIndex(index)) ? 1 : 0;
      case GAMEPAD_BUTTON -> input.gamepad().connected() && input.gamepad().isDown(BUTTONS[index]) ? 1 : 0;
      case GAMEPAD_AXIS -> input.gamepad().connected() ? input.gamepad().axis(AXES[index]) : 0;
      case SCROLL -> index == 0 ? input.mouse().scroll().deltaX() : input.mouse().scroll().deltaY();
      case JOYSTICK_BUTTON -> input.joystick().isDown(index) ? 1 : 0;
      case JOYSTICK_AXIS -> input.joystick().axis(index);
      case JOYSTICK_HAT -> (input.joystick().hat(index) & direction) != 0 ? 1 : 0;
    };
    return (float) (direction == 0 || device == Device.JOYSTICK_HAT ? value : Math.max(0, value * direction));
  }

  /**
   * Tests whether this control is active.
   *
   * <p>Digital sources use their actual key state; analog sources use the supplied hysteresis threshold.
   *
   * @param input input snapshot to read
   * @param threshold minimum absolute analog value for activation
   * @return whether the control is active
   */
  public boolean isDown(InputSnapshot input, float threshold) {
    return switch (device) {
      case KEYBOARD -> input.keyboard().isDown(KEYS[index]);
      case MOUSE -> input.mouse().isDown(MouseButton.fromIndex(index));
      case GAMEPAD_BUTTON -> input.gamepad().connected() && input.gamepad().isDown(BUTTONS[index]);
      case JOYSTICK_BUTTON -> input.joystick().isDown(index);
      case JOYSTICK_HAT -> (input.joystick().hat(index) & direction) != 0;
      default -> {
        float value = Math.abs(value(input));
        yield value > 0 && value >= threshold;
      }
    };
  }

  /**
   * Returns the canonical serialized path.
   *
   * @return canonical control path
   */
  public String path() {
    return path;
  }

  /**
   * Identifies the physical channel for conflict consumption; opposite axis directions share it.
   *
   * @return physical channel identifier
   */
  public String channel() {
    return channel;
  }

  /**
   * Returns a stable localization key; applications may instead render a device-specific glyph.
   *
   * @return localization key for this control path
   */
  public String translationKey() {
    return "input.control." + path.replace("<", "").replace(">", "").replace('/', '.').toLowerCase(Locale.ROOT);
  }

  private enum Device {
    KEYBOARD,
    MOUSE,
    GAMEPAD_BUTTON,
    GAMEPAD_AXIS,
    SCROLL,
    JOYSTICK_BUTTON,
    JOYSTICK_AXIS,
    JOYSTICK_HAT
  }
}
