package io.viki.momentum.input;

/** Mutable owner-thread input state. Relative units are wheel ticks; stick positions are normalized. */
public final class ScrollState implements Scroller {
  private final boolean persistent;
  private double x;
  private double y;
  private double deltaX;
  private double deltaY;

  /**
   * Creates a scroll or stick state.
   *
   * @param persistent whether changes update a retained position
   */
  public ScrollState(boolean persistent) {
    this.persistent = persistent;
  }

  /**
   * Returns the accumulated horizontal change for this frame.
   *
   * @return horizontal delta
   */
  @Override
  public double deltaX() {
    return deltaX;
  }

  /**
   * Returns the accumulated vertical change for this frame.
   *
   * @return vertical delta
   */
  @Override
  public double deltaY() {
    return deltaY;
  }

  /**
   * Returns the retained horizontal position.
   *
   * @return horizontal position
   */
  @Override
  public double x() {
    return x;
  }

  /**
   * Returns the retained vertical position.
   *
   * @return vertical position
   */
  @Override
  public double y() {
    return y;
  }

  /**
   * Reports whether this state retains positions between frames.
   *
   * @return whether positions persist
   */
  @Override
  public boolean persistent() {
    return persistent;
  }

  /**
   * Accumulates a relative impulse; persistent sources also advance their positions.
   *
   * @param dx horizontal change
   * @param dy vertical change
   */
  public void add(double dx, double dy) {
    deltaX += dx;
    deltaY += dy;
    if (persistent) {
      x += dx;
      y += dy;
    }
  }

  /**
   * Updates a persistent position, accumulating its actual change.
   *
   * @param x new horizontal position
   * @param y new vertical position
   * @throws IllegalStateException if this state is relative-only
   */
  public void setPosition(double x, double y) {
    if (!persistent) {
      throw new IllegalStateException("A relative scroller has no retained position");
    }
    add(x - this.x, y - this.y);
  }

  /** Clears changes without discarding a held axis position. */
  public void clearFrameState() {
    deltaX = deltaY = 0;
  }

  /** Clears positions and changes, for focus loss or device removal. */
  public void reset() {
    x = y = deltaX = deltaY = 0;
  }
}
