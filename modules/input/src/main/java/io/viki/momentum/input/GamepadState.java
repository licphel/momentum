package io.viki.momentum.input;

/**
 * Pollable, directly injectable gamepad state. All access belongs to the owning input thread.
 */
public final class GamepadState {
  private final Runnable changed;
  private final ScrollState left = new ScrollState(true);
  private final ScrollState right = new ScrollState(true);
  private boolean connected;
  private int down;
  private int pressed;
  private int released;
  private float leftTrigger;
  private float rightTrigger;
  private float deadzone = 0.15F;

  /** Creates a disconnected gamepad state without a change callback. */
  public GamepadState() {
    this(() -> {
    });
  }

  GamepadState(Runnable changed) {
    this.changed = changed;
  }

  /**
   * Returns whether a gamepad is currently connected.
   *
   * @return whether the state represents a connected gamepad
   */
  public boolean connected() {
    return connected;
  }

  /**
   * Returns the normalized left stick state.
   *
   * @return left stick coordinates
   */
  public Scroller leftStick() {
    return left;
  }

  /**
   * Returns the normalized right stick state.
   *
   * @return right stick coordinates
   */
  public Scroller rightStick() {
    return right;
  }

  /**
   * Tests whether a button is held.
   *
   * @param button button to query
   * @return whether the button is held
   */
  public boolean isDown(GamepadButton button) {
    return (down & 1 << button.ordinal()) != 0;
  }

  /**
   * Tests whether a button was pressed during the current frame.
   *
   * @param button button to query
   * @return whether the button transitioned to held
   */
  public boolean isTransitioned(GamepadButton button) {
    return (pressed & 1 << button.ordinal()) != 0;
  }

  /**
   * Tests whether a button was released during the current frame.
   *
   * @param button button to query
   * @return whether the button transitioned to released
   */
  public boolean isReleased(GamepadButton button) {
    return (released & 1 << button.ordinal()) != 0;
  }

  /**
   * Updates the connection state and clears controls when disconnected.
   *
   * @param connected whether the gamepad is connected
   */
  public void setConnected(boolean connected) {
    if (!connected) {
      reset();
      return;
    }
    if (this.connected) {
      return;
    }
    this.connected = true;
    changed.run();
  }

  /**
   * Sets the radial deadzone applied to both sticks.
   *
   * @param deadzone deadzone in the range {@code [0, 1)}
   * @throws IllegalArgumentException if the deadzone is outside its valid range
   */
  public void setDeadzone(float deadzone) {
    if (deadzone < 0 || deadzone >= 1) {
      throw new IllegalArgumentException("Stick deadzone must be in [0,1)");
    }
    this.deadzone = deadzone;
  }

  /**
   * Injects a button state.
   *
   * @param button button to update
   * @param held whether the button is held
   */
  public void setButton(GamepadButton button, boolean held) {
    int bit = 1 << button.ordinal();
    if (((down & bit) != 0) == held) {
      return;
    }
    if (held && (down & bit) == 0) {
      pressed |= bit;
    }
    if (!held && (down & bit) != 0) {
      released |= bit;
    }
    down = held ? down | bit : down & ~bit;
    changed.run();
  }

  /**
   * Injects left stick coordinates.
   *
   * @param x horizontal coordinate
   * @param y vertical coordinate
   */
  public void setLeftStick(double x, double y) {
    setStick(left, x, y);
  }

  /**
   * Injects right stick coordinates.
   *
   * @param x horizontal coordinate
   * @param y vertical coordinate
   */
  public void setRightStick(double x, double y) {
    setStick(right, x, y);
  }

  /**
   * Injects analog trigger values, clamped to {@code [0, 1]}.
   *
   * @param left left trigger value
   * @param right right trigger value
   */
  public void setTriggers(float left, float right) {
    left = Math.clamp(left, 0F, 1F);
    right = Math.clamp(right, 0F, 1F);
    if (leftTrigger == left && rightTrigger == right) {
      return;
    }
    leftTrigger = left;
    rightTrigger = right;
    changed.run();
  }

  /** Applies radial deadzone shaping to a stick and notifies listeners on change. */
  private void setStick(ScrollState stick, double x, double y) {
    double oldX = stick.x(), oldY = stick.y();
    double length = Math.hypot(x, y);
    if (length <= deadzone) {
      stick.setPosition(0, 0);
    } else {
      double scale = (Math.min(length, 1) - deadzone) / ((1 - deadzone) * length);
      stick.setPosition(x * scale, y * scale);
    }
    if (oldX != stick.x() || oldY != stick.y()) {
      changed.run();
    }
  }

  /**
   * Reads a normalized axis value.
   *
   * @param axis axis to query
   * @return axis value
   */
  public double axis(GamepadAxis axis) {
    return switch (axis) {
      case LEFT_X -> left.x();
      case LEFT_Y -> left.y();
      case RIGHT_X -> right.x();
      case RIGHT_Y -> right.y();
      case LEFT_TRIGGER -> leftTrigger;
      case RIGHT_TRIGGER -> rightTrigger;
    };
  }

  /** Clears transient press and release state for the next frame. */
  public void clearFrameState() {
    pressed = released = 0;
    left.clearFrameState();
    right.clearFrameState();
  }

  /** Disconnects the gamepad and clears all button, stick, and trigger state. */
  public void reset() {
    boolean hadState = connected || down != 0 || left.x() != 0 || left.y() != 0
        || right.x() != 0 || right.y() != 0 || leftTrigger != 0 || rightTrigger != 0;
    connected = false;
    released |= down;
    down = 0;
    left.reset();
    right.reset();
    leftTrigger = rightTrigger = 0;
    if (hadState) {
      changed.run();
    }
  }
}
