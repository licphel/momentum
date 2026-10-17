package io.viki.momentum.input;

import java.util.Arrays;

/**
 * Raw joystick controls; device-local indices need a device profile before they have gameplay meaning.
 * Owner-thread only.
 */
public final class JoystickState {
  /** Bit set when a hat points up. */
  public static final int HAT_UP = 1;
  /** Bit set when a hat points right. */
  public static final int HAT_RIGHT = 2;
  /** Bit set when a hat points down. */
  public static final int HAT_DOWN = 4;
  /** Bit set when a hat points left. */
  public static final int HAT_LEFT = 8;
  private final Runnable changed;
  private String name = "";
  private String guid = "";
  private boolean connected;
  private float[] axes = new float[0];
  private boolean[] buttons = new boolean[0];
  private boolean[] pressed = new boolean[0];
  private boolean[] released = new boolean[0];
  private int[] hats = new int[0];

  /** Creates a disconnected joystick state without a change callback. */
  public JoystickState() {
    this(() -> {
    });
  }

  JoystickState(Runnable changed) {
    this.changed = changed;
  }

  /**
   * Returns whether this joystick is connected.
   *
   * @return whether controls are currently available
   */
  public boolean connected() {
    return connected;
  }

  /**
   * Returns the reported device name.
   *
   * @return device name, or the empty string when unavailable
   */
  public String name() {
    return name;
  }

  /**
   * Returns the reported device GUID.
   *
   * @return device GUID, or the empty string when unavailable
   */
  public String guid() {
    return guid;
  }

  /**
   * Returns the number of raw axes.
   *
   * @return axis count
   */
  public int axisCount() {
    return axes.length;
  }

  /**
   * Returns the number of raw buttons.
   *
   * @return button count
   */
  public int buttonCount() {
    return buttons.length;
  }

  /**
   * Returns the number of hat controls.
   *
   * @return hat count
   */
  public int hatCount() {
    return hats.length;
  }

  /**
   * Connects the joystick and allocates its raw controls.
   *
   * @param name reported device name
   * @param guid reported device GUID
   * @param axisCount number of axes
   * @param buttonCount number of buttons
   * @param hatCount number of hats
   * @throws IllegalArgumentException if a control count is negative
   */
  public void connect(String name, String guid, int axisCount, int buttonCount, int hatCount) {
    if (axisCount < 0 || buttonCount < 0 || hatCount < 0) {
      throw new IllegalArgumentException("Negative joystick control count");
    }
    this.name = name;
    this.guid = guid;
    if (axes.length != axisCount) {
      axes = new float[axisCount];
    }
    if (buttons.length != buttonCount) {
      buttons = new boolean[buttonCount];
      pressed = new boolean[buttonCount];
      released = new boolean[buttonCount];
    }
    if (hats.length != hatCount) {
      hats = new int[hatCount];
    }
    connected = true;
    changed.run();
  }

  /**
   * Reads a raw axis value.
   *
   * @param index axis index
   * @return axis value, or zero when disconnected or out of range
   */
  public float axis(int index) {
    return connected && index >= 0 && index < axes.length ? axes[index] : 0;
  }

  /**
   * Tests whether a raw button is held.
   *
   * @param index button index
   * @return whether the button is held
   */
  public boolean isDown(int index) {
    return connected && index >= 0 && index < buttons.length && buttons[index];
  }

  /**
   * Reads a hat direction bitmask.
   *
   * @param index hat index
   * @return direction bitmask, or zero when disconnected or out of range
   */
  public int hat(int index) {
    return connected && index >= 0 && index < hats.length ? hats[index] : 0;
  }

  /**
   * Updates a raw axis value.
   *
   * @param index axis index
   * @param value axis value
   */
  public void setAxis(int index, float value) {
    if (axes[index] == value) {
      return;
    }
    axes[index] = value;
    changed.run();
  }

  /**
   * Tests whether a raw button was pressed during the current frame.
   *
   * @param index button index
   * @return whether the button transitioned to held
   */
  public boolean isTransitioned(int index) {
    return index >= 0 && index < pressed.length && pressed[index];
  }

  /**
   * Tests whether a raw button was released during the current frame.
   *
   * @param index button index
   * @return whether the button transitioned to released
   */
  public boolean isReleased(int index) {
    return index >= 0 && index < released.length && released[index];
  }

  /**
   * Updates a raw button state.
   *
   * @param index button index
   * @param held whether the button is held
   */
  public void setButton(int index, boolean held) {
    if (buttons[index] == held) {
      return;
    }
    if (held && !buttons[index]) {
      pressed[index] = true;
    }
    if (!held && buttons[index]) {
      released[index] = true;
    }
    buttons[index] = held;
    changed.run();
  }

  /**
   * Updates a hat direction bitmask.
   *
   * @param index hat index
   * @param directions combination of {@link #HAT_UP}, {@link #HAT_RIGHT}, {@link #HAT_DOWN}, and {@link #HAT_LEFT}
   */
  public void setHat(int index, int directions) {
    if (hats[index] == directions) {
      return;
    }
    hats[index] = directions;
    changed.run();
  }

  /** Disconnects the joystick and clears all controls. */
  public void reset() {
    boolean wasConnected = connected;
    for (int i = 0; i < buttons.length; i++) {
      released[i] |= buttons[i];
    }
    connected = false;
    Arrays.fill(axes, 0);
    Arrays.fill(buttons, false);
    Arrays.fill(hats, 0);
    if (wasConnected) {
      changed.run();
    }
  }

  /** Clears transient press and release state for the next frame. */
  public void clearFrameState() {
    Arrays.fill(pressed, false);
    Arrays.fill(released, false);
  }
}
