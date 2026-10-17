package io.viki.momentum.input;

/**
 * Pointer, physical buttons and wheel; confined to the owning snapshot thread.
 */
public final class MouseState {
  private final Runnable changed;
  private final KeyboardState keyboard;
  private final ScrollState scroll = new ScrollState(false);
  private int down;
  private int pressed;
  private int released;
  private double x;
  private double y;

  MouseState(KeyboardState keyboard, Runnable changed) {
    this.keyboard = keyboard;
    this.changed = changed;
  }

  /**
   * Returns the current pointer x coordinate.
   *
   * @return pointer x coordinate
   */
  public double x() {
    return x;
  }

  /**
   * Returns the current pointer y coordinate.
   *
   * @return pointer y coordinate
   */
  public double y() {
    return y;
  }

  /**
   * Returns the wheel state.
   *
   * @return relative scroll state
   */
  public Scroller scroll() {
    return scroll;
  }

  /**
   * Tests whether a mouse button is held.
   *
   * @param button button to query
   * @return whether the button is held
   */
  public boolean isDown(MouseButton button) {
    return (down & 1 << button.ordinal()) != 0;
  }

  /**
   * Tests whether a mouse button was pressed during the current frame.
   *
   * @param button button to query
   * @return whether the button transitioned to held
   */
  public boolean isTransitioned(MouseButton button) {
    return (pressed & 1 << button.ordinal()) != 0;
  }

  /**
   * Tests whether a mouse button was released during the current frame.
   *
   * @param button button to query
   * @return whether the button transitioned to released
   */
  public boolean isReleased(MouseButton button) {
    return (released & 1 << button.ordinal()) != 0;
  }

  /**
   * Applies a pointer movement.
   *
   * @param x pointer x coordinate
   * @param y pointer y coordinate
   */
  public void onMove(double x, double y) {
    this.x = x;
    this.y = y;
  }

  /**
   * Applies a platform mouse-button event.
   *
   * @param button changed button
   * @param action event action
   * @param modifiers modifier bitmask reported with the event
   */
  public void onButtonChange(MouseButton button, KeyAction action, int modifiers) {
    int bit = 1 << button.ordinal();
    if (action == KeyAction.PRESS && (down & bit) == 0) {
      pressed |= bit;
    }
    if (action == KeyAction.RELEASE && (down & bit) != 0) {
      released |= bit;
    }
    down = action == KeyAction.RELEASE ? down & ~bit : down | bit;
    keyboard.updateLockMods(modifiers);
    changed.run();
  }

  /**
   * Adds a wheel impulse.
   *
   * @param dx horizontal wheel change
   * @param dy vertical wheel change
   */
  public void onScroll(double dx, double dy) {
    scroll.add(dx, dy);
    changed.run();
  }

  /** Clears transient button and wheel state for the next frame. */
  public void clearFrameState() {
    pressed = released = 0;
    scroll.clearFrameState();
  }

  /** Releases all buttons and clears wheel state. */
  public void reset() {
    released |= down;
    down = 0;
    scroll.reset();
    changed.run();
  }
}
