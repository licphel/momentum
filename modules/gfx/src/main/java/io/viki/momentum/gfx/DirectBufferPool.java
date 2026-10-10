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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package io.viki.momentum.gfx;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * A tiered, thread-safe pool for direct byte buffers.
 *
 * <p>Use {@link #SMALL}, {@link #MEDIUM}, or {@link #LARGE} according to the
 * expected upload size. A request may exceed the tier's initial capacity; the
 * buffer grows for that request and is discarded on release instead of being
 * retained in a different tier.
 *
 * <p>The pool uses a lock-free queue and an atomic retained-buffer count. It
 * does not make an acquired buffer thread-safe; ownership transfers to the
 * caller until {@link #release(ByteBuffer)} is called exactly once.
 */
public final class DirectBufferPool implements AutoCloseable {
  /** Pool for matrices and other small staging uploads. */
  public static final DirectBufferPool SMALL = new DirectBufferPool(4 * 1024, 32);
  /** Pool for ordinary texture and buffer uploads. */
  public static final DirectBufferPool MEDIUM = new DirectBufferPool(64 * 1024, 16);
  /** Pool for light maps, render targets, and other large uploads. */
  public static final DirectBufferPool LARGE = new DirectBufferPool(1024 * 1024, 8);

  private final int initialCapacity;
  private final int retainedCapacity;
  private final int retainedLimit;
  private final ConcurrentLinkedQueue<ByteBuffer> available = new ConcurrentLinkedQueue<>();
  private final AtomicInteger availableCount = new AtomicInteger();

  /**
   * Creates an independent direct-buffer pool.
   *
   * @param initialCapacity capacity of newly allocated buffers
   * @param retainedLimit maximum number of available buffers retained by this pool
   */
  public DirectBufferPool(int initialCapacity, int retainedLimit) {
    if (initialCapacity <= 0 || retainedLimit <= 0) {
      throw new IllegalArgumentException("Pool capacity and retention limit must be positive");
    }
    this.initialCapacity = initialCapacity;
    this.retainedCapacity = initialCapacity;
    this.retainedLimit = retainedLimit;
  }

  /**
   * Acquires a cleared buffer with at least {@code neededSize} bytes.
   *
   * <p>The requested size is the minimum required capacity, not a tier
   * selection. Callers choose the tier through the pool instance and provide
   * the exact size needed by the current operation.
   *
   * @param neededSize minimum required capacity, greater than zero
   * @return an exclusively owned, cleared direct buffer
   */
  public ByteBuffer acquire(int neededSize) {
    if (neededSize <= 0) {
      throw new IllegalArgumentException("Requested size must be positive");
    }
    ByteBuffer buffer = available.poll();
    if (buffer != null) {
      availableCount.decrementAndGet();
    }
    if (buffer == null || buffer.capacity() < neededSize) {
      if (buffer != null) {
        memFree(buffer);
      }
      buffer = memAlloc(Math.max(initialCapacity, neededSize));
    }
    buffer.clear();
    return buffer;
  }

  /**
   * Returns a buffer acquired from this pool.
   *
   * <p>Buffers larger than this tier's retained capacity are freed immediately,
   * and the pool keeps at most its configured number of available buffers.
   *
   * @param buffer the finished buffer; release it exactly once
   */
  public void release(ByteBuffer buffer) {
    if (buffer.capacity() > retainedCapacity || !reserveSlot()) {
      memFree(buffer);
      return;
    }
    available.offer(buffer);
  }

  /** Frees all available native buffers. Acquired buffers are unaffected. */
  @Override
  public void close() {
    ByteBuffer buffer;
    while ((buffer = available.poll()) != null) {
      availableCount.decrementAndGet();
      memFree(buffer);
    }
  }

  private boolean reserveSlot() {
    int current;
    do {
      current = availableCount.get();
      if (current >= retainedLimit) {
        return false;
      }
    } while (!availableCount.compareAndSet(current, current + 1));
    return true;
  }
}
