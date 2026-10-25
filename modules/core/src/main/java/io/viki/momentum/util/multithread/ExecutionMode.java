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

package io.viki.momentum.util.multithread;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

/**
 * Thread ownership for a long-lived native subsystem, not a choice of shared executor.
 * Immutable and thread-safe. Select once when opening/loading the subsystem.
 */
public enum ExecutionMode {
  /** Creates one component-owned platform thread; native initialization, work and cleanup stay there. */
  DEDICATED_THREAD,
  /**
   * Creates no worker. The opening/loading caller owns native initialization, polling/execution
   * and cleanup. Other threads may enqueue commands but never take over its native context.
   */
  CALLER_THREAD;

  /**
   * Chooses CALLER_THREAD on thread-constrained cases, otherwise DEDICATED_THREAD.
   * This is a static platform policy, not live CPU-load measurement or automatic migration.
   *
   * @return currently optimal execution mode
   */
  public static ExecutionMode auto() {
    int cores = Runtime.getRuntime().availableProcessors();
    if (cores <= 2) {
      return CALLER_THREAD;
    }

    try {
      OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
      double load = osBean.getSystemLoadAverage();

      if (load >= 0) {
        double perCoreLoad = load / cores;
        return perCoreLoad > 0.7 ? CALLER_THREAD : DEDICATED_THREAD;
      }
    } catch (Error | Exception e) {
      // Ignore
    }
    return Parallel.isThreadConstrainedPlatform() ? CALLER_THREAD : DEDICATED_THREAD;
  }
}
