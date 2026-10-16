package io.viki.momentum.input;

/** Mutable owner-thread input state. Relative units are wheel ticks; stick positions are normalized. */
public final class ScrollState implements Scroller {
  private final boolean persistent;
  private double x;
  private double y;
  private double deltaX;
  private double deltaY;

  public ScrollState(boolean persistent) { this.persistent = persistent; }
  @Override public double x() { return x; }
  @Override public double y() { return y; }
  @Override public double deltaX() { return deltaX; }
  @Override public double deltaY() { return deltaY; }
  @Override public boolean persistent() { return persistent; }

  /** Accumulates a relative impulse; persistent sources also advance their positions. */
  public void add(double dx, double dy) {
    deltaX += dx;
    deltaY += dy;
    if (persistent) { x += dx; y += dy; }
  }

  /** Updates a persistent position, accumulating its actual change. */
  public void setPosition(double x, double y) {
    if (!persistent) throw new IllegalStateException("A relative scroller has no retained position");
    add(x - this.x, y - this.y);
  }

  /** Clears changes without discarding a held axis position. */
  public void clearFrameState() { deltaX = deltaY = 0; }
  /** Clears positions and changes, for focus loss or device removal. */
  public void reset() { x = y = deltaX = deltaY = 0; }
}
