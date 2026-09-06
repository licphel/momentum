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

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * A thread-safe object pool for managing {@code DirectByteBuffer} instances.
 *
 * <p>This pool serves as a recycling mechanism for off-heap memory to reduce the overhead
 * of frequent {@code malloc} and {@code free} calls in high-throughput environments.
 * It is designed to support multiple producer threads acquiring buffers and a single
 * (or multiple) consumer threads releasing them back into the pool.
 *
 * <p><b>Thread safety:</b>
 * This class is thread-safe.
 */
public final class DirectBufferPool {
  private static final ConcurrentLinkedQueue<ByteBuffer> pool = new ConcurrentLinkedQueue<>();

  private DirectBufferPool() {
  }

  /**
   * Retrieves a {@code DirectByteBuffer} from the pool with at least the required capacity.
   *
   * <p>The returned buffer is always in a {@code clear()} state, meaning its
   * {@code position} is set to 0 and its {@code limit} is set to its capacity,
   * making it ready for immediate writes.
   *
   * @param neededSize the minimum required capacity in bytes (must be {@code > 0})
   * @return a cleared, reusable {@code DirectByteBuffer} with capacity {@code >= neededSize}
   * @throws IllegalArgumentException if {@code neededSize} is {@code <= 0}
   */
  public static ByteBuffer acquire(int neededSize) {
    if (neededSize <= 0) {
      throw new IllegalArgumentException("Requested size must be positive.");
    }

    ByteBuffer buffer = pool.poll();

    if (buffer == null || buffer.capacity() < neededSize) {
      if (buffer != null) {
        memFree(buffer); // Dispose of the under-sized buffer
      }
      buffer = memAlloc(neededSize);
    }

    buffer.clear();
    return buffer;
  }

  /**
   * Returns a previously acquired {@code DirectByteBuffer} back to the pool.
   *
   * <p>Once the data stored in the buffer has been fully consumed (e.g., passed to
   * a native library or uploaded to the GPU), the buffer should be released to allow
   * other threads to reuse its underlying off-heap memory.
   *
   * <p><b>Important:</b> Only direct buffers obtained from {@link #acquire(int)}
   * should be passed to this method. Passing a heap-based {@code ByteBuffer}
   * or an unmanaged direct buffer will be silently ignored.
   *
   * @param buffer the {@code DirectByteBuffer} to return to the pool; may be {@code null}
   */
  public static void release(ByteBuffer buffer) {
    pool.offer(buffer);
  }

  /**
   * Frees all {@code DirectByteBuffer} instances currently held in the pool.
   *
   * <p>This method should be invoked during application shutdown to explicitly release
   * the off-heap memory resources back to the operating system.
   *
   * <p>After calling this method, the pool is empty. Attempts to {@link #acquire(int)}
   * will result in newly allocated buffers.
   */
  public static void close() {
    ByteBuffer buffer;
    while ((buffer = pool.poll()) != null) {
      memFree(buffer);
    }
  }
}