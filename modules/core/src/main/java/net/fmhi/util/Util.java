package net.fmhi.util;

/**
 * General-purpose engine utilities, most notably a fixed-timestep main loop
 * with an interpolated render phase: logic advances at a constant rate while
 * rendering stays smooth between ticks.
 */
public final class Util {
  /** Maximum number of catch-up ticks per frame. */
  private static final int MAX_LEAP = 3;

  private static volatile boolean stopped;
  private static int maxTps;
  private static long tickCount;
  private static boolean hasTicked;
  private static float delta;
  private static float partialTicks;
  private static float tickTime;
  private static float frameTime;

  private Util() {
  }

  /**
   * Runs the main loop until {@link #stop()} is called: {@code tick} fires
   * at a fixed rate of {@code maxTps} per second, catching up at most
   * {@value #MAX_LEAP} ticks per frame and resetting the schedule when the
   * loop falls far behind, then {@code draw} runs once per frame with
   * {@link #partialTicks()} holding the interpolation between the last two
   * ticks.
   *
   * @param maxTps the fixed logic tick rate
   * @param tick   the logic update, invoked once per tick step
   * @param draw   the render pass, invoked once per frame
   */
  public static void launch(int maxTps, Runnable tick, Runnable draw) {
    Util.maxTps = maxTps;
    double tickLength = 1_000_000_000.0 / maxTps;
    double nextTick = System.nanoTime();
    stopped = false;
    while (!stopped) {
      int loops = 0;
      hasTicked = false;

      // fell too far behind: reset the schedule instead of spiraling
      if (System.nanoTime() - nextTick > tickLength * 4) {
        nextTick = System.nanoTime();
      }

      while (System.nanoTime() > nextTick && loops < MAX_LEAP) {
        hasTicked = true;
        delta = 1F / maxTps;
        tickTime += delta;
        nextTick += tickLength;
        loops++;
        tickCount++;
        tick.run();
      }

      partialTicks = (float) ((System.nanoTime() + tickLength - nextTick) / tickLength);
      frameTime = tickTime + partialTicks * delta;
      draw.run();
    }
  }

  /** Stops the main loop after the current frame. */
  public static void stop() {
    stopped = true;
  }

  /**
   * Whether {@link #stop()} has been requested.
   *
   * @return if the app has stopped
   */
  public static boolean stopped() {
    return stopped;
  }

  /**
   * The fixed tick step in seconds.
   *
   * @return the fixed time step
   */
  public static float delta() {
    return delta;
  }

  /**
   * The render interpolation between the last two ticks, in {@code [0, 1)}.
   *
   * @return the render partial ticks
   */
  public static float partialTicks() {
    return partialTicks;
  }

  /**
   * Accumulated logic time in seconds (tick steps only).
   *
   * @return the logic tick time in seconds
   */
  public static float tickTime() {
    return tickTime;
  }

  /**
   * Interpolated frame time in seconds, for animation and shaders.
   *
   * @return frame time in seconds
   */
  public static float frameTime() {
    return frameTime;
  }

  /**
   * Total number of ticks executed since the loop started.
   *
   * @return the logic tick count
   */
  public static long tickCount() {
    return tickCount;
  }

  /**
   * Returns whether at least one tick ran before the current draw.
   *
   * @return whether at least one tick ran before the current draw
   */
  public static boolean hasTicked() {
    return hasTicked;
  }

  /**
   * Linear interpolation between {@code a} and {@code b} by {@code t}
   * ({@code 0} = {@code a}, {@code 1} = {@code b}). Intended for
   * render-time interpolation between the previous and the current tick
   * state with {@link #partialTicks()}.
   *
   * @param a the value at {@code t = 0}
   * @param b the value at {@code t = 1}
   * @param t the interpolation factor (not clamped)
   * @return {@code a + (b - a) * t}
   */
  public static float lerp(float a, float b, float t) {
    return a + (b - a) * t;
  }
}
