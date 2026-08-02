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

package net.fmhi.world.light;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * A mutable {@link LightBuffer} backed by three plain float fields.
 *
 * <p>Instances are not thread-safe; they are intended as short-lived scratch
 * buffers during light computation.
 *
 * @see LightBuffer
 */
public final class SimpleLightBuffer implements LightBuffer {
  private static final ThreadLocal<Queue<SimpleLightBuffer>> POOL = ThreadLocal.withInitial(ArrayDeque::new);

  private float rv;
  private float gv;
  private float bv;
  private boolean pooled;

  /**
   * Creates a non-pooled light buffer.
   */
  public SimpleLightBuffer() {
  }

  /**
   * Returns a pooled SimpleLightBuffer instance for the current thread.
   * The buffer may contain stale values from previous usage.
   *
   * @return a thread-local light buffer
   */
  public static SimpleLightBuffer pooled() {
    Queue<SimpleLightBuffer> pool = POOL.get();
    SimpleLightBuffer buf = pool.poll();
    if (buf == null) {
      buf = new SimpleLightBuffer();
      buf.pooled = true;
    }
    buf.rv = 0F;
    buf.gv = 0F;
    buf.bv = 0F;
    return buf;
  }

  /**
   * Returns this buffer to the pool for reuse.
   * Call this when done with the buffer.
   */
  public void recycle() {
    if (pooled) {
      POOL.get().offer(this);
    }
  }

  @Override
  public float r() {
    return rv;
  }

  @Override
  public float g() {
    return gv;
  }

  @Override
  public float b() {
    return bv;
  }

  @Override
  public void r(float v) {
    rv = v;
  }

  @Override
  public void g(float v) {
    gv = v;
  }

  @Override
  public void b(float v) {
    bv = v;
  }
}
