package io.viki.momentum.util;

/**
 * Fixed-timestep main loop with interpolated rendering.
 *
 * <p>Logic advances at a constant rate ({@code tps}), while rendering runs
 * as fast as the frame limiter allows. The render pass receives
 * {@link #partialTicks()} to interpolate between the previous and current
 * tick state.
 *
 * <p>This class is a process-wide singleton bound to the render thread.
 * It must only be driven from a single thread.
 */
public final class Loop {
  /** Maximum number of catch-up ticks per frame before the loop is considered "slow". */
  private static final int MAX_CATCH_UP_TICKS = 5;

  /** Stats are recomputed at this interval, in nanoseconds. */
  private static final long STATS_INTERVAL_NANOS = 500_000_000L;

  /** Busy-wait is used only for sleeps shorter than this, to avoid OS scheduler jitter. */
  private static final long SPIN_THRESHOLD_NANOS = 100_000L;

  /** Leave scheduler headroom instead of sleeping all the way to the deadline. */
  private static final long SLEEP_HEADROOM_NANOS = 2_000_000L;

  /** Bound each sleep so a loaded scheduler cannot amplify a long sleep request. */
  private static final long MAX_SLEEP_NANOS = 5_000_000L;

  private static volatile boolean stopped;
  private static int maxTps;

  private static long tickCount;
  private static boolean hasTicked;
  private static boolean runningSlowly;

  private static float delta;
  private static float partialTicks;
  private static float tickTime;
  private static float frameTime;
  private static float realFrameTime;
  private static volatile int currentFps;
  private static volatile int currentTps;

  private Loop() {
  }

  /**
   * Runs the main loop until {@link #stop()} is called, with no frame rate limit.
   *
   * @param maxTps the fixed logic tick rate
   * @param tick   the logic update, invoked once per tick step
   * @param draw   the render pass, invoked once per frame
   */
  public static void launch(int maxTps, Runnable tick, Runnable draw) {
    launch(maxTps, 0, tick, draw);
  }

  /**
   * Runs the main loop until {@link #stop()} is called.
   *
   * <p>{@code tick} fires at a fixed rate of {@code maxTps} per second,
   * catching up at most {@value #MAX_CATCH_UP_TICKS} ticks per frame. If the
   * schedule still falls behind, excess whole tick intervals are discarded
   * without resetting its fractional phase. {@code draw} runs
   * once per frame, with {@link #partialTicks()} holding the interpolation
   * between the last two ticks.
   *
   * @param maxTps the fixed logic tick rate
   * @param maxFps maximum frames per second (0 = unlimited)
   * @param tick   the logic update, invoked once per tick step
   * @param draw   the render pass, invoked once per frame
   */
  public static void launch(int maxTps, int maxFps, Runnable tick, Runnable draw) {
    if (maxTps <= 0) {
      throw new IllegalArgumentException("maxTps must be positive");
    }
    if (maxFps < 0) {
      throw new IllegalArgumentException("maxFps must not be negative");
    }

    double tickLength = 1_000_000_000.0 / maxTps;
    double frameLength = maxFps > 0 ? 1_000_000_000.0 / maxFps : 0.0;

    Loop.maxTps = maxTps;
    Loop.delta = (float) (1.0 / maxTps);
    stopped = false;
    tickTime = 0F;
    frameTime = 0F;
    tickCount = 0L;

    long frameCounter = 0L;
    long lastTickCountSnapshot = 0L;
    long lastStatsNanos = System.nanoTime();
    long previousFrameNanos = lastStatsNanos;

    double nextTickNanos = System.nanoTime();
    double nextFrameNanos = nextTickNanos;

    while (!stopped) {
      long frameStartNanos = System.nanoTime();

      // Real elapsed time since the previous frame, in seconds.
      realFrameTime = (float) ((frameStartNanos - previousFrameNanos) / 1_000_000_000.0);
      previousFrameNanos = frameStartNanos;

      // Catch up on ticks.
      hasTicked = false;
      int loops = 0;
      while (frameStartNanos >= nextTickNanos && loops < MAX_CATCH_UP_TICKS) {
        hasTicked = true;
        nextTickNanos += tickLength;
        tickCount++;
        tickTime = (float) ((double) tickCount / maxTps);
        loops++;
        tick.run();
      }

      // If we hit the catch-up limit, we are running slowly.
      runningSlowly = frameStartNanos >= nextTickNanos;
      if (runningSlowly) {
        // Drop only whole intervals; resetting to "now" caused interpolation
        // to jump to 1 and then back to 0 without an intervening tick.
        nextTickNanos += (Math.floor((frameStartNanos - nextTickNanos) / tickLength) + 1) * tickLength;
      }

      // Interpolation between the previous and next tick.
      // Include time spent updating, rather than rendering with a stale frame-start timestamp.
      double partial = (System.nanoTime() + tickLength - nextTickNanos) / tickLength;
      partialTicks = (float) Math.max(0.0, Math.min(partial, 1.0));

      // Frame time for rendering: accumulated tick time plus the interpolated step.
      frameTime = (float) ((tickCount + (double) partialTicks) / maxTps);

      draw.run();
      frameCounter++;

      // Frame rate limiting.
      if (maxFps > 0) {
        nextFrameNanos += frameLength;
        long now = System.nanoTime();
        double sleepNanos = nextFrameNanos - now;

        if (sleepNanos > 0) {
          sleepPrecise(sleepNanos);
        } else {
          // We are behind schedule. Do not accumulate debt.
          nextFrameNanos = System.nanoTime();
        }
      }

      // Stats update.
      long nowNanos = System.nanoTime();
      long elapsedNanos = nowNanos - lastStatsNanos;
      if (elapsedNanos >= STATS_INTERVAL_NANOS) {
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        currentFps = (int) (frameCounter / elapsedSeconds);
        long ticksSinceLast = tickCount - lastTickCountSnapshot;
        currentTps = (int) (ticksSinceLast / elapsedSeconds);
        frameCounter = 0L;
        lastTickCountSnapshot = tickCount;
        lastStatsNanos = nowNanos;
      }
    }
  }

  /**
   * Sleeps for the requested duration, using busy-wait for very short sleeps
   * to avoid OS scheduler jitter and {@link Thread#sleep} for longer ones.
   */
  @SuppressWarnings("BusyWait")
  private static void sleepPrecise(double nanos) {
    long target = System.nanoTime() + (long) nanos;
    long remaining;
    // Like Starbound's Windows limiter, recheck the deadline between short
    // sleeps and yield near it. Only the final tiny tail actively spins.
    while (!stopped && (remaining = target - System.nanoTime()) > 0) {
      if (remaining <= SPIN_THRESHOLD_NANOS) {
        Thread.onSpinWait();
      } else if (remaining <= SLEEP_HEADROOM_NANOS) {
        Thread.yield();
      } else {
        long sleepNanos = Math.min(MAX_SLEEP_NANOS, remaining - SLEEP_HEADROOM_NANOS);
        try {
          Thread.sleep(sleepNanos / 1_000_000L, (int) (sleepNanos % 1_000_000L));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          stopped = true;
        }
      }
    }
  }

  /** Stops the main loop after the current frame. */
  public static void stop() {
    stopped = true;
  }

  /**
   * Whether {@link #stop()} has been requested.
   *
   * @return whether stop has been requested
   */
  public static boolean stopped() {
    return stopped;
  }

  /**
   * The fixed tick step in seconds. This is a constant for a given
   * {@code maxTps}; it is not the time elapsed since the last tick.
   *
   * @return the fixed time step
   */
  public static float delta() {
    return delta;
  }

  /**
   * The render interpolation between the previous and current tick, in
   * {@code [0, 1]}.
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
   * Interpolated frame time in seconds, for animation and shaders. Equals the
   * accumulated tick time plus {@code partialTicks() * delta()}.
   *
   * @return interpolated frame time in seconds
   */
  public static float frameTime() {
    return frameTime;
  }

  /**
   * Real elapsed time since the previous frame, in seconds. Use this for
   * animations that should follow real time rather than logic time.
   *
   * @return real frame time in seconds
   */
  public static float realFrameTime() {
    return realFrameTime;
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
   * Whether at least one tick ran before the current draw.
   *
   * @return whether at least one tick ran before the current draw
   */
  public static boolean hasTicked() {
    return hasTicked;
  }

  /**
   * Whether the loop hit its catch-up limit during the current frame.
   * Games can use this to degrade gracefully (skip particles, lower AI
   * frequency, etc.).
   *
   * @return whether the loop is running slowly
   */
  public static boolean isRunningSlowly() {
    return runningSlowly;
  }

  /**
   * The current frames per second (updated every 0.5 seconds).
   *
   * @return the current FPS
   */
  public static int fps() {
    return currentFps;
  }

  /**
   * The current ticks per second (updated every 0.5 seconds).
   *
   * @return the current TPS
   */
  public static int tps() {
    return currentTps;
  }

  /**
   * Linear interpolation between {@code a} and {@code b} by {@code t}.
   *
   * @param a the value at {@code t = 0}
   * @param b the value at {@code t = 1}
   * @param t the interpolation factor
   * @return {@code a + (b - a) * t}
   */
  public static float lerp(float a, float b, float t) {
    return a + (b - a) * t;
  }

  /**
   * Linear interpolation between {@code a} and {@code b} by the current
   * {@link #partialTicks()}.
   *
   * @param a the value at {@code t = 0}
   * @param b the value at {@code t = 1}
   * @return {@code a + (b - a) * partialTicks()}
   */
  public static float lerp(float a, float b) {
    return a + (b - a) * partialTicks;
  }
}
