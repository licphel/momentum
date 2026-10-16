package io.viki.momentum.input;

/** Owner-thread digital gamepad binding. */
public record GamepadButtonMatch(GamepadState state, GamepadButton button) implements Transitable {
  @Override public boolean isDown() { return state.connected() && state.isDown(button); }
  @Override public boolean transitioned() { return state.connected() && state.pressed(button); }
  @Override public boolean transitionedOrRepeated() { return transitioned(); }
}
