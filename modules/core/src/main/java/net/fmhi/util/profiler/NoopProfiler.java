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

package net.fmhi.util.profiler;

/**
 * The profiling-disabled {@link Profiler} implementation.
 *
 * <p>Every method is a no-op and {@link #scope(String)} returns a shared scope, so the
 * disabled default costs no allocation on the hot path.
 */
final class NoopProfiler implements Profiler {
  static final NoopProfiler INSTANCE = new NoopProfiler();
  /** Shared scope returned for every section so disabled profiling allocates nothing. */
  private static final Scope NOOP_SCOPE = new Scope(null, "");

  private NoopProfiler() {
  }

  /**
   * Returns the shared no-op scope, ignoring the requested section name.
   */
  @Override
  public Scope scope(String name) {
    return NOOP_SCOPE;
  }

  @Override
  public void start(String name) {
  }

  @Override
  public void end(String name) {
  }

  @Override
  public void dump() {
  }

  @Override
  public void reset() {
  }
}
