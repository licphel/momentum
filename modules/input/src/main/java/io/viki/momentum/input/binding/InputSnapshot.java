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

import io.viki.momentum.input.Key;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Instance-owned raw device state.
 *
 * <p>All access belongs to the owning input thread.
 */
public final class InputSnapshot {
  /** Number of raw joystick slots maintained by this snapshot. */
  public static final int JOYSTICK_SLOTS = 16;
  private final Map<KeyCode, Key> keys = new EnumMap<>(KeyCode.class);
  private final Map<MouseButton, Key> mouseKeys = new EnumMap<>(MouseButton.class);
  private final Map<GamepadButton, Key> padKeys = new EnumMap<>(GamepadButton.class);
  private final Map<Long, Key> joystickKeys = new HashMap<>();
  private final Map<Integer, JoystickState> joysticks = new HashMap<>();
  private final JoystickState emptyJoystick = new JoystickState();
  private final KeyboardState keyboard = new KeyboardState(this::deviceChanged);
  private final MouseState mouse = new MouseState(keyboard, this::deviceChanged);
  private final GamepadState gamepad = new GamepadState(this::deviceChanged);
  private int selectedJoystick = -1;
  private int activeJoystick = -1;

  /** Creates an input snapshot with empty device state and no bindings. */
  public InputSnapshot() {
  }

  /**
   * Validates a raw joystick slot, allowing {@code -1} as the no-device sentinel.
   *
   * @param slot slot to validate
   * @throws IllegalArgumentException if the slot is outside the supported range
   */
  private static void checkJoystickSlot(int slot) {
    if (slot < -1 || slot >= JOYSTICK_SLOTS) {
      throw new IllegalArgumentException("Joystick slot out of range: " + slot);
    }
  }

  /**
   * Returns a live view of a raw joystick button.
   *
   * @param slot  joystick slot to query
   * @param index button index within the slot
   * @return button state view
   * @throws IllegalArgumentException if the slot or index is invalid
   */
  public Key button(int slot, int index) {
    checkJoystickSlot(slot);
    if (slot < 0 || index < 0) {
      throw new IllegalArgumentException("Invalid joystick button: " + slot + "/" + index);
    }
    long id = ((long) slot << 32) | (index & 0xffffffffL);
    return joystickKeys.computeIfAbsent(id, ignored -> new Key() {
      @Override
      public float value() {
        return isDown() ? 1 : 0;
      }

      @Override
      public boolean isDown() {
        return joystick(slot).isDown(index);
      }

      @Override
      public boolean transitioned() {
        return joystick(slot).isTransitioned(index);
      }

      @Override
      public boolean repeated() {
        return false;
      }

      @Override
      public boolean released() {
        return joystick(slot).isReleased(index);
      }
    });
  }

  /**
   * Returns a live view of a keyboard key.
   *
   * @param code key to query
   * @return keyboard key state view
   */
  public Key key(KeyCode code) {
    return keys.computeIfAbsent(code, key -> new Key() {
      @Override
      public float value() {
        return isDown() ? 1 : 0;
      }

      @Override
      public boolean isDown() {
        return keyboard.isDown(key);
      }

      @Override
      public boolean transitioned() {
        return keyboard.isTransitioned(key);
      }

      @Override
      public boolean repeated() {
        return keyboard.isRepeated(key);
      }

      @Override
      public boolean released() {
        return keyboard.isReleased(key);
      }
    });
  }

  /**
   * Returns a live view of a mouse button.
   *
   * @param code button to query
   * @return mouse button state view
   */
  public Key button(MouseButton code) {
    return mouseKeys.computeIfAbsent(code, key -> new Key() {
      @Override
      public float value() {
        return isDown() ? 1 : 0;
      }

      @Override
      public boolean isDown() {
        return mouse.isDown(key);
      }

      @Override
      public boolean transitioned() {
        return mouse.isTransitioned(key);
      }

      @Override
      public boolean repeated() {
        return false;
      }

      @Override
      public boolean released() {
        return mouse.isReleased(key);
      }
    });
  }

  /**
   * Returns a live view of a mapped gamepad button.
   *
   * @param code button to query
   * @return gamepad button state view
   */
  public Key button(GamepadButton code) {
    return padKeys.computeIfAbsent(code, key -> new Key() {
      @Override
      public float value() {
        return isDown() ? 1 : 0;
      }

      @Override
      public boolean isDown() {
        return gamepad.isDown(key);
      }

      @Override
      public boolean transitioned() {
        return gamepad.isTransitioned(key);
      }

      @Override
      public boolean repeated() {
        return false;
      }

      @Override
      public boolean released() {
        return gamepad.isReleased(key);
      }
    });
  }

  /**
   * Returns the keyboard state.
   *
   * @return keyboard state owned by this snapshot
   */
  public KeyboardState keyboard() {
    return keyboard;
  }

  /**
   * Returns the mouse state.
   *
   * @return mouse state owned by this snapshot
   */
  public MouseState mouse() {
    return mouse;
  }

  /**
   * Returns the mapped gamepad state.
   *
   * @return gamepad state owned by this snapshot
   */
  public GamepadState gamepad() {
    return gamepad;
  }

  /**
   * Returns a raw joystick slot.
   *
   * @param slot joystick slot from {@code 0} through {@link #JOYSTICK_SLOTS} exclusive
   * @return joystick state for the slot
   */
  public JoystickState joystick(int slot) {
    checkJoystickSlot(slot);
    if (slot < 0) {
      return emptyJoystick;
    }
    return joysticks.computeIfAbsent(slot, ignored -> new JoystickState());
  }

  /**
   * Returns the selected active joystick, or an empty state when none is active.
   *
   * @return active joystick state
   */
  public JoystickState joystick() {
    return joystick(activeJoystick);
  }

  private void deviceChanged() {
  }

  /**
   * Selects the raw joystick slot for future use.
   *
   * @param slot slot, or {@code -1} to select no slot
   * @throws IllegalArgumentException if the slot is outside the valid range
   */
  public void selectJoystick(int slot) {
    checkJoystickSlot(slot);
    selectedJoystick = slot;
  }

  /**
   * Returns the selected raw joystick slot.
   *
   * @return selected slot, or {@code -1} when none is selected
   */
  public int selectedJoystick() {
    return selectedJoystick;
  }

  /**
   * Returns the raw joystick slot currently used by bindings.
   *
   * @return active slot, or {@code -1} when no raw joystick is active
   */
  public int activeJoystick() {
    return activeJoystick;
  }

  /**
   * Sets the raw joystick slot used by bindings.
   *
   * @param slot slot, or {@code -1} to disable raw joystick input
   * @throws IllegalArgumentException if the slot is outside the valid range
   */
  public void setActiveJoystick(int slot) {
    checkJoystickSlot(slot);
    if (activeJoystick == slot) {
      return;
    }
    activeJoystick = slot;
    gamepad.reset();
    deviceChanged();
  }

  /** Clears transient press, release, and repeat state for the next frame. */
  public void clearFrameState() {
    keyboard.clearFrameState();
    mouse.clearFrameState();
    gamepad.clearFrameState();
    for (JoystickState joystick : joysticks.values()) {
      joystick.clearFrameState();
    }
  }

  /** Clears all device state and disables the active raw joystick. */
  public void clearInputState() {
    keyboard.reset();
    mouse.reset();
    gamepad.reset();
    for (JoystickState joystick : joysticks.values()) {
      joystick.reset();
    }
    activeJoystick = -1;
    deviceChanged();
  }

  /**
   * Clears input state when the owning window loses focus.
   *
   * @param focused whether the input window is focused
   */
  public void onFocusChange(boolean focused) {
    if (!focused) {
      clearInputState();
    }
  }
}
