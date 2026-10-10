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

package io.viki.momentum.codec.streaming;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A lock-free pool for heap-backed {@link BinaryBuffer} instances of one expected size.
 *
 * <p>Use the three built-in instances for the normal workload classes:
 * {@link #SMALL} for short records, {@link #MEDIUM} for chunk and definition records, and
 * {@link #LARGE} for bulk snapshots. A buffer starts at the pool's initial capacity and keeps the
 * normal {@link BinaryBuffer} growth semantics if a record is larger. An expanded buffer that no
 * longer belongs to this pool's retention range is discarded on close instead of polluting a
 * different tier.
 *
 * <p>Acquisition and release use a {@link ConcurrentLinkedQueue}; callers own an acquired buffer
 * exclusively until {@link BinaryBuffer#close()} or {@link #release(BinaryBuffer)}. The pool
 * retains at most the configured number of available buffers and is safe to share between worker
 * threads.
 */
public final class BinaryBufferPool implements AutoCloseable {
  /** Pool for short packets and small metadata records. */
  public static final BinaryBufferPool SMALL = new BinaryBufferPool(4 * 1024, 32);
  /** Pool for typical chunk, bulk, and definition records. */
  public static final BinaryBufferPool MEDIUM = new BinaryBufferPool(64 * 1024, 16);
  /** Pool for large snapshots; intentionally retains only a few buffers. */
  public static final BinaryBufferPool LARGE = new BinaryBufferPool(1024 * 1024, 8);

  private final int initialCapacity;
  private final int retainedCapacity;
  private final int retainedLimit;
  private final ConcurrentLinkedQueue<HeapBinaryBuffer> available = new ConcurrentLinkedQueue<>();
  private final AtomicInteger availableCount = new AtomicInteger();

  /**
   * Creates an independent buffer pool.
   *
   * @param initialCapacity capacity used for newly acquired buffers
   * @param retainedLimit maximum number of available buffers retained by this pool
   */
  public BinaryBufferPool(int initialCapacity, int retainedLimit) {
    if (initialCapacity <= 0 || retainedLimit <= 0) {
      throw new IllegalArgumentException("Pool capacity and retention limit must be positive");
    }
    this.initialCapacity = initialCapacity;
    this.retainedCapacity = initialCapacity;
    this.retainedLimit = retainedLimit;
  }

  /**
   * Acquires a cleared buffer. The buffer grows automatically when its record exceeds the tier.
   *
   * @return an exclusively owned buffer
   */
  public BinaryBuffer acquire() {
    HeapBinaryBuffer buffer = available.poll();
    if (buffer != null) {
      availableCount.decrementAndGet();
    } else {
      buffer = new HeapBinaryBuffer(initialCapacity, this);
    }
    buffer.reopen();
    return buffer;
  }

  /**
   * Returns a buffer acquired from this pool.
   *
   * <p>Calling {@link BinaryBuffer#close()} is the usual form and has the same effect.
   *
   * @param buffer the buffer whose use has finished
   */
  public void release(BinaryBuffer buffer) {
    if (!(buffer instanceof HeapBinaryBuffer heap) || heap.owner() != this) {
      throw new IllegalArgumentException("Buffer was not acquired from this pool");
    }
    heap.recycle();
  }

  /** Discards all currently available buffers. Buffers still in use are unaffected. */
  @Override
  public void close() {
    HeapBinaryBuffer buffer;
    while ((buffer = available.poll()) != null) {
      availableCount.decrementAndGet();
      buffer.discard();
    }
  }

  void recycle(HeapBinaryBuffer buffer) {
    if (buffer.capacity() > retainedCapacity || !reserveSlot()) {
      buffer.discard();
      return;
    }
    available.offer(buffer);
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
