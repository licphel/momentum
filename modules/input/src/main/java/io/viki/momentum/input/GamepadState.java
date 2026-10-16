package io.viki.momentum.input;

/** Pollable, directly injectable gamepad state. All access belongs to the owning input thread. */
public final class GamepadState {
  private final ScrollState left = new ScrollState(true);
  private final ScrollState right = new ScrollState(true);
  private boolean connected;
  private int down;
  private int pressed;
  private float leftTrigger;
  private float rightTrigger;
  private float previousLeftTrigger;
  private float previousRightTrigger;
  private float deadzone = 0.15F;

  public boolean connected() { return connected; }
  public Scroller leftStick() { return left; }
  public Scroller rightStick() { return right; }
  public boolean isDown(GamepadButton button) { return (down & 1 << button.ordinal()) != 0; }
  public boolean pressed(GamepadButton button) { return (pressed & 1 << button.ordinal()) != 0; }

  public void setConnected(boolean connected) {
    if (!connected) reset();
    this.connected = connected;
  }

  public void setDeadzone(float deadzone) {
    if (deadzone < 0 || deadzone >= 1) throw new IllegalArgumentException("Stick deadzone must be in [0,1)");
    this.deadzone = deadzone;
  }

  public void setButton(GamepadButton button, boolean held) {
    int bit = 1 << button.ordinal();
    if (held && (down & bit) == 0) pressed |= bit;
    down = held ? down | bit : down & ~bit;
  }

  public void setLeftStick(double x, double y) { setStick(left, x, y); }
  public void setRightStick(double x, double y) { setStick(right, x, y); }
  public void setTriggers(float left, float right) {
    leftTrigger = Math.clamp(left, 0F, 1F);
    rightTrigger = Math.clamp(right, 0F, 1F);
  }

  private void setStick(ScrollState stick, double x, double y) {
    double length = Math.hypot(x, y);
    if (length <= deadzone) { stick.setPosition(0, 0); return; }
    double scale = (Math.min(length, 1) - deadzone) / ((1 - deadzone) * length);
    stick.setPosition(x * scale, y * scale);
  }

  public double axis(GamepadAxis axis) {
    return switch (axis) {
      case LEFT_X -> left.x(); case LEFT_Y -> left.y();
      case RIGHT_X -> right.x(); case RIGHT_Y -> right.y();
      case LEFT_TRIGGER -> leftTrigger; case RIGHT_TRIGGER -> rightTrigger;
    };
  }

  public double previousAxis(GamepadAxis axis) {
    return switch (axis) {
      case LEFT_X -> left.x() - left.deltaX(); case LEFT_Y -> left.y() - left.deltaY();
      case RIGHT_X -> right.x() - right.deltaX(); case RIGHT_Y -> right.y() - right.deltaY();
      case LEFT_TRIGGER -> previousLeftTrigger; case RIGHT_TRIGGER -> previousRightTrigger;
    };
  }

  public void clearFrameState() {
    pressed = 0;
    left.clearFrameState(); right.clearFrameState();
    previousLeftTrigger = leftTrigger; previousRightTrigger = rightTrigger;
  }

  public void reset() {
    connected = false;
    down = pressed = 0;
    left.reset(); right.reset();
    leftTrigger = rightTrigger = previousLeftTrigger = previousRightTrigger = 0;
  }
}
