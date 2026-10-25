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

/**
 * Defines the execution modes for background tasks.
 *
 * <p>This enum guides the system in allocating thread resources for specific tasks under different
 * runtime environments. It is used in conjunction with {@link Parallel#isCpuResourceLimited()} to
 * adaptively adjust between resource-constrained environments (e.g., mobile or embedded devices)
 * and resource-rich environments (e.g., desktops).
 *
 * @see Parallel
 * @since 1.0
 */
public enum ExecutionMode {
  /**
   * Dedicated thread mode.
   *
   * <p>Tasks should run on dedicated, long-lived threads. This mode is typically suitable for
   * resource-constrained environments or scenarios where a task requires exclusive thread
   * context (e.g., specific native dependencies).
   */
  DEDICATED_THREAD,

  /**
   * Bound thread mode.
   *
   * <p>Tasks should be bound to shared, bounded thread pools (such as {@link Parallel#cpu()} or
   * {@link Parallel#io()}). This mode is suitable for resource-rich environments, reducing
   * context-switching and memory overhead through thread reuse.
   */
  BOUND_THREAD;

  /**
   * Automatically selects the appropriate execution mode based on the current platform's
   * resource conditions.
   *
   * <p>Returns {@link #DEDICATED_THREAD} if the current platform is deemed CPU-resource-limited
   * by {@link Parallel#isCpuResourceLimited()}; otherwise returns {@link #BOUND_THREAD}.
   *
   * @return the automatically inferred execution mode, never {@code null}
   */
  public static ExecutionMode auto() {
    return Parallel.isCpuResourceLimited() ? DEDICATED_THREAD : BOUND_THREAD;
  }
}