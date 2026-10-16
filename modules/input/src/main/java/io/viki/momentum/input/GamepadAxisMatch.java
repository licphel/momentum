package io.viki.momentum.input;

/** Owner-thread axis-to-button binding. A negative threshold selects the negative direction. */
public record GamepadAxisMatch(GamepadState state, GamepadAxis axis, float threshold) implements Transitable {
  public GamepadAxisMatch {
    if (threshold == 0 || threshold < -1 || threshold > 1) {
      throw new IllegalArgumentException("Axis threshold must be nonzero and within [-1,1]");
    }
  }
  private boolean matches(double value) { return threshold > 0 ? value >= threshold : value <= threshold; }
  @Override public boolean isDown() { return state.connected() && matches(state.axis(axis)); }
  @Override public boolean transitioned() { return isDown() && !matches(state.previousAxis(axis)); }
  @Override public boolean transitionedOrRepeated() { return transitioned(); }
}
