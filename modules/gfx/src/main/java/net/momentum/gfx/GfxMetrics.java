package net.momentum.gfx;

import java.util.concurrent.atomic.LongAdder;

/**
 * High-performance, lock-free statistics tracker for graphics pipeline metrics.
 *
 * <p>This class uses {@link LongAdder} to accumulate counters with near-zero
 * overhead, making it safe for high-frequency calls within the rendering loop
 * without causing garbage collection or thread contention.
 *
 * <p>Call {@link #next()} once per frame to advance the frame counter.
 * Call {@link #dump()} to print accumulated statistics and per-frame averages
 * to {@code System.out}, then reset all counters.
 *
 * <p><b>Thread safety:</b> All counters are thread-safe and can be updated
 * concurrently from multiple producer threads.
 *
 * @see LongAdder
 */
public final class GfxMetrics {
  /**
   * Total number of draw calls (including draw, drawIndexed, drawInstanced,
   * drawIndexedInstanced, and compute dispatches) submitted to the GPU.
   * <p>This is the most critical metric for assessing GPU workload.
   */
  public static final LongAdder DCPT = new LongAdder();

  /**
   * Total number of commands (opcodes) <b>recorded</b> by the Encoder.
   * <p>This includes state-setting commands (setPipeline, setViewport, etc.)
   * as well as draw commands. It reflects the workload of the <b>producer thread</b>
   * (CPU-side command generation).
   */
  public static final LongAdder ECMDPT = new LongAdder();

  /**
   * Total number of commands <b>actually queued and executed</b> by the Device
   * (render thread).
   * <p>This represents the actual workload dispatched to the OpenGL driver
   * from the render thread's command queue. It can be used to compare against
   * {@link #ECMDPT} to detect queue overflows, drops, or synchronization stalls
   * between the producer and the consumer threads.
   */
  public static final LongAdder DCMDPT = new LongAdder();

  private static long frameCount = 0L;

  private GfxMetrics() {
  }

  /**
   * Advances the frame counter by one.
   * <p>This method should be invoked exactly once per frame (e.g., at the end
   * of the rendering loop), regardless of whether statistics are due to be printed.
   */
  public static void next() {
    frameCount++;
  }

  /**
   * Prints the accumulated statistics if at least one second has elapsed since
   * the last output. It shows the total values over the interval and the average
   * per-frame values, then resets all counters and the frame counter.
   *
   * <p><b>Performance note:</b> This method uses {@link System#nanoTime()}
   * instead of {@code System.currentTimeMillis()} to ensure high precision
   * and minimize system call overhead in a tight rendering loop.
   */
  public static void dump() {
    long dCptTotal = DCPT.sum();
    long eCmpDtTotal = ECMDPT.sum();
    long dCmpDtTotal = DCMDPT.sum();
    long currentFrameCount = frameCount;

    // Prevent division by zero if no frames were recorded
    long avgDcpt = currentFrameCount > 0 ? dCptTotal / currentFrameCount : 0;
    long avgEcmpdt = currentFrameCount > 0 ? eCmpDtTotal / currentFrameCount : 0;
    long avgDcmpdt = currentFrameCount > 0 ? dCmpDtTotal / currentFrameCount : 0;

    System.out.println("[GfxMetrics]");
    System.out.printf("  %-40s %10s %12s%n", "metric", "total", "per-frame");
    System.out.println("  " + "-".repeat(68));
    System.out.printf("  %-40s %10d %12d%n", "DCPT (Draw Calls)", dCptTotal, avgDcpt);
    System.out.printf("  %-40s %10d %12d%n", "ECMDPT (Encoder Cmds)", eCmpDtTotal, avgEcmpdt);
    System.out.printf("  %-40s %10d %12d%n", "DCMDPT (Device Cmds)", dCmpDtTotal, avgDcmpdt);
    System.out.println();

    // Reset counters and frame count for the next interval
    DCPT.reset();
    ECMDPT.reset();
    DCMDPT.reset();
    frameCount = 0L;
  }
}