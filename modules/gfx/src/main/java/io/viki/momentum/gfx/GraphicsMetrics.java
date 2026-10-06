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

package io.viki.momentum.gfx;

import java.util.concurrent.atomic.LongAdder;

/**
 * Collects aggregate rendering statistics for monitoring graphics workloads.
 *
 * <p>Call {@link #next()} once at each frame boundary to snapshot frame metrics. Call
 * {@link #dump()} to print interval totals and averages, then reset interval counters. Concurrent
 * counter increments are supported; frame advancement and reporting should be coordinated with
 * counter updates to keep snapshots consistent.
 */
public final class GraphicsMetrics {
  /** Number of draw and compute dispatch calls issued during the reporting interval. */
  public static final LongAdder Drawcalls = new LongAdder();
  /** Number of graphics commands recorded by encoders during the reporting interval. */
  public static final LongAdder EncoderSum = new LongAdder();
  /** Sum of pending device commands observed at execution start during the reporting interval. */
  public static final LongAdder DeviceQueueSize = new LongAdder();
  /** Actual buffer payload bytes uploaded during the reporting interval; excludes storage allocation. */
  public static final LongAdder BufferUploadBytes = new LongAdder();
  /** Number of executed payload upload batches, including allocations with initial data. */
  public static final LongAdder BufferUploadBatches = new LongAdder();

  private static final LongAdder pendingBufferUploadBatches = new LongAdder();
  private static volatile long lastFrameBufferUploadBatches;
  private static final LongAdder pendingBufferUploadBytes = new LongAdder();
  private static volatile long lastFrameBufferUploadBytes;
  private static long peakFrameBufferUploadBytes;
  private static long previousDrawcalls;
  private static volatile long lastFrameDrawcalls;

  private static long frameCount = 0L;

  private GraphicsMetrics() {
  }

  /**
   * Records payload bytes after an upload executes.
   *
   * @param bytes the number of payload bytes uploaded
   */
  public static void recordBufferUpload(long bytes) {
    BufferUploadBytes.add(bytes);
    pendingBufferUploadBytes.add(bytes);
    BufferUploadBatches.increment();
    pendingBufferUploadBatches.increment();
  }

  /**
   * Returns the number of payload upload batches in the last completed frame.
   *
   * @return the completed frame's payload upload batch count
   */
  public static long lastFrameBufferUploadBatches() {
    return lastFrameBufferUploadBatches;
  }

  /**
   * Returns the number of payload bytes uploaded in the last completed frame.
   *
   * @return the completed frame's payload upload byte count
   */
  public static long lastFrameBufferUploadBytes() {
    return lastFrameBufferUploadBytes;
  }

  /**
   * Returns the draw and compute dispatch calls issued in the last completed frame.
   *
   * @return the completed frame's draw and compute dispatch call count
   */
  public static long lastFrameDrawcalls() {
    return lastFrameDrawcalls;
  }

  /**
   * Advances the frame counter by one.
   * <p>This method should be invoked exactly once per frame (e.g., at the end
   * of the rendering loop), regardless of whether statistics are due to be printed.
   */
  public static void next() {
    long drawcalls = Drawcalls.sum();
    lastFrameDrawcalls = drawcalls - previousDrawcalls;
    previousDrawcalls = drawcalls;
    lastFrameBufferUploadBytes = pendingBufferUploadBytes.sumThenReset();
    lastFrameBufferUploadBatches = pendingBufferUploadBatches.sumThenReset();
    peakFrameBufferUploadBytes = Math.max(peakFrameBufferUploadBytes, lastFrameBufferUploadBytes);
    frameCount++;
  }

  /**
   * Prints interval totals and per-frame averages, then resets interval counters.
   *
   * <p>Averages are zero when no frame boundaries have been recorded since the previous report.
   */
  public static void dump() {
    long dCptTotal = Drawcalls.sum();
    long eCmpDtTotal = EncoderSum.sum();
    long dCmpDtTotal = DeviceQueueSize.sum();
    long bufferBytes = BufferUploadBytes.sum();
    long bufferBatches = BufferUploadBatches.sum();
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
    System.out.printf("  %-40s %10d %12d%n", "Buffer Upload (bytes)", bufferBytes,
        currentFrameCount > 0 ? bufferBytes / currentFrameCount : 0);
    System.out.printf("  Buffer Upload last/peak frame: %d / %d bytes%n",
        lastFrameBufferUploadBytes, peakFrameBufferUploadBytes);
    System.out.printf("  %-40s %10d %12d%n", "BTPT (Buffer Upload Batches)", bufferBatches,
        currentFrameCount > 0 ? bufferBatches / currentFrameCount : 0);
    System.out.println();

    // Reset counters and frame count for the next interval
    Drawcalls.reset();
    previousDrawcalls = 0L;
    EncoderSum.reset();
    DeviceQueueSize.reset();
    BufferUploadBytes.reset();
    BufferUploadBatches.reset();
    peakFrameBufferUploadBytes = 0L;
    frameCount = 0L;
  }
}
