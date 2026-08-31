/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.momentum.util;

/**
 * General-purpose engine utilities, most notably a fixed-timestep main loop
 * with an interpolated render phase: logic advances at a constant rate while
 * rendering stays smooth between ticks.
 */
public final class Loop {
  /** Maximum number of catch-up ticks per frame. */
  private static final int MAX_LEAP = 5;

  private static volatile boolean stopped;
  private static int maxTps;
  private static long tickCount;
  private static boolean hasTicked;
  private static float delta;
  private static float partialTicks;
  private static float tickTime;
  private static float frameTime;
  private static volatile int currentFps;
  private static volatile int currentTps;

  private Loop() {
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
    launch(maxTps, 0, tick, draw);
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
   * @param maxFps maximum frames per second (0 = unlimited)
   * @param tick   the logic update, invoked once per tick step
   * @param draw   the render pass, invoked once per frame
   */
  @SuppressWarnings("BusyWait")
  public static void launch(int maxTps, int maxFps, Runnable tick, Runnable draw) {
    double tickLength = 1_000_000_000.0 / maxTps;
    double nextTick = System.nanoTime();

    Loop.maxTps = maxTps;
    stopped = false;
    long frameCounter = 0;
    long tickCounterSnapshot = 0;
    long lastStatsNano = System.nanoTime();
    currentFps = 0;
    currentTps = 0;

    // FPS limiting
    double frameLength = maxFps > 0 ? 1_000_000_000.0 / maxFps : 0;
    double nextFrame = System.nanoTime();

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

      frameCounter++;

      // Frame rate limiting - sleep if we're ahead of schedule
      if (maxFps > 0) {
        nextFrame += frameLength;
        long now = System.nanoTime();
        double sleepNanos = (long) (nextFrame - now);

        if (sleepNanos > 0) {
          // Use busy-wait for short sleeps (< 1ms) to avoid OS scheduling jitter
          if (sleepNanos < 1_000_000) {
            long start = System.nanoTime();
            while (System.nanoTime() - start < sleepNanos) {
              Thread.onSpinWait();
            }
          } else {
            try {
              // Convert to millis + nanos for precise sleeping
              long sleepMillis = (long) (sleepNanos / 1_000_000);
              int sleepNanosRemaining = (int) (sleepNanos % 1_000_000);
              Thread.sleep(sleepMillis, sleepNanosRemaining);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              break;
            }
          }
        } else {
          // If we're behind schedule, don't accumulate debt - just reset nextFrame
          // to now to prevent a cascade of missed frames
          nextFrame = System.nanoTime();
        }
      }

      long nowNano = System.nanoTime();
      long elapsedNano = nowNano - lastStatsNano;

      if (elapsedNano >= 500_000_000) {
        double elapsedSeconds = elapsedNano / 1_000_000_000.0;

        currentFps = (int) (frameCounter / elapsedSeconds);

        long currentTickCount = tickCount;
        long ticksSinceLastUpdate = currentTickCount - tickCounterSnapshot;
        currentTps = (int) (ticksSinceLastUpdate / elapsedSeconds);

        frameCounter = 0;
        tickCounterSnapshot = currentTickCount;
        lastStatsNano = nowNano;
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
   * Returns the current frames per second (updated every 0.5 seconds).
   *
   * @return the current FPS
   */
  public static int fps() {
    return currentFps;
  }

  /**
   * Returns the current ticks per second (updated every 0.5 seconds).
   *
   * @return the current TPS
   */
  public static int tps() {
    return currentTps;
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

  /**
   * Linear interpolation between {@code a} and {@code b} by {@code t}
   * ({@code 0} = {@code a}, {@code 1} = {@code b}). Intended for
   * render-time interpolation between the previous and the current tick
   * state with {@link #partialTicks()}.
   *
   * @param a the value at {@code t = 0}
   * @param b the value at {@code t = 1}
   * @return {@code a + (b - a) * t}
   */
  public static float lerp(float a, float b) {
    return a + (b - a) * partialTicks;
  }
}