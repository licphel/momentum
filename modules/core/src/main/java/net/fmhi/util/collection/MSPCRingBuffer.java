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

package net.fmhi.util.collection;

/**
 * A bounded ring buffer of {@code int}s for multiple producers and a single
 * consumer, with backpressure: a producer whose {@link #add(int)} would
 * overflow the capacity spins on the spot until the consumer frees a slot.
 *
 * <p>The producer cursor is guarded by an internal lock; the consumer reads
 * it through a volatile, so the full-check outside the lock is safe (stale
 * reads are re-verified under the lock). The consumer cursor is volatile so
 * producers observe freed slots. Each slot is published by the volatile
 * write of the producer cursor, so the consumer always reads a fully
 * written value.
 *
 * <p><b>Thread safety:</b> {@link #add(int)} is safe from any number of
 * producer threads; {@link #poll()} must only be called from the single
 * consumer thread, and only when the buffer is not empty. The consumer must
 * make progress concurrently with producers, otherwise a full buffer spins
 * its producers indefinitely.
 */
public final class MSPCRingBuffer {
  private final int[] buf;
  private final int mask;
  private final Object lock = new Object();
  private volatile long write; // producer cursor; only advanced under lock
  private volatile long read;  // consumer cursor

  /**
   * Creates a ring buffer with a power-of-two capacity of at least the
   * requested size.
   *
   * @param capacity the minimum number of values the buffer can hold
   */
  public MSPCRingBuffer(int capacity) {
    if (capacity < 2) {
      throw new IllegalArgumentException("capacity must be at least 2, got " + capacity);
    }
    int cap = Integer.highestOneBit(capacity - 1) << 1;
    buf = new int[cap];
    mask = cap - 1;
  }

  /**
   * Adds a value to the tail, spinning when the buffer is full until the
   * consumer frees a slot. Safe to call from any number of threads.
   *
   * @param value the value to add
   */
  public void add(int value) {
    for (; ; ) {
      // the stale cursor is fine here: the lock re-checks before writing
      if (write - read < buf.length) {
        synchronized (lock) {
          if (write - read < buf.length) {
            // writing the slot before the volatile cursor publish makes the
            // value visible to the consumer (happens-before)
            buf[(int) (write & mask)] = value;
            write++;
            return;
          }
        }
      }
      Thread.onSpinWait();
    }
  }

  /**
   * Removes and returns the head value. Must only be called from the single
   * consumer thread, and only when the buffer is not empty.
   *
   * @return the head value
   */
  public int poll() {
    long r = read;
    assert r < write : "poll on an empty ring buffer";
    int value = buf[(int) (r & mask)];
    read = r + 1;
    return value;
  }

  /**
   * Returns the number of values waiting for the consumer, in O(1).
   *
   * @return the number of values in the buffer
   */
  public int size() {
    return (int) (write - read);
  }

  /**
   * Returns whether the buffer holds no values.
   *
   * @return true when empty
   */
  public boolean isEmpty() {
    return write == read;
  }

  /**
   * Returns the power-of-two capacity of this buffer.
   *
   * @return the capacity
   */
  public int capacity() {
    return buf.length;
  }
}
