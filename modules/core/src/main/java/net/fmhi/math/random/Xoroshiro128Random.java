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

package net.fmhi.math.random;

import java.util.Arrays;
import java.util.List;

/**
 * Xoroshiro128+ pseudo-random number generator.
 *
 * <p>Period: 2^128 − 1. Passes BigCrush. Supports parallel streams via
 * {@link #jump()} (2^64 advance) and {@link #longJump()} (2^96 advance).
 */
public final class Xoroshiro128Random implements RandomGenerator {
  private final long initS0;
  private final long initS1;
  private long s0;
  private long s1;

  /**
   * Creates a Xoroshiro128+ rng from current date.
   */
  public Xoroshiro128Random() {
    this(System.nanoTime());
  }

  /**
   * Creates a Xoroshiro128+ rng from a long seed.
   *
   * @param seed the long seed
   */
  public Xoroshiro128Random(long seed) {
    s0 = splitMix64(seed);
    s1 = splitMix64(s0);
    initS0 = s0;
    initS1 = s1;
  }

  /**
   * Creates a Xoroshiro128+ rng from two long seed.
   *
   * @param s0 seed A
   * @param s1 seed B
   */
  public Xoroshiro128Random(long s0, long s1) {
    this.s0 = s0;
    this.s1 = s1;
    initS0 = s0;
    initS1 = s1;
  }

  private static long rotateLeft(long x, int k) {
    return (x << k) | (x >>> (64 - k));
  }

  private static long splitMix64(long x) {
    x += 0x9e3779b97f4a7c15L;
    x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
    x = (x ^ (x >>> 27)) * 0x94d049bb133111EbL;
    return x ^ (x >>> 31);
  }

  private long nextLong() {
    final long result = s0 + s1;
    final long t = s1 ^ s0;
    s0 = rotateLeft(s0, 24) ^ t ^ (t << 16);
    s1 = rotateLeft(t, 37);
    return result;
  }

  @Override
  public double nextDouble() {
    // Use upper 53 bits for double precision.
    return (nextLong() >>> 11) * 0x1.0p-53;
  }

  @Override
  public int nextInt(int bound) {
    if (bound <= 0) {
      throw new IllegalArgumentException("bound must be > 0");
    }

    long mask = bound - 1L;
    if ((bound & mask) == 0) {
      return (int) (nextLong() >>> (64 - Long.numberOfTrailingZeros(bound)));
    }

    long r;
    long threshold = Long.remainderUnsigned(-bound, bound);
    do {
      r = nextLong() >>> 1;
    } while (r < threshold);
    return (int) (r % bound);
  }

  @Override
  public String state() {
    return s0 + "@" + s1;
  }

  @Override
  public void recover(String state) {
    List<Long> parts = Arrays.stream(state.split("@")).map(Long::parseLong).toList();

    if (parts.size() != 2) {
      throw new IllegalArgumentException("Invalid state: " + state);
    }

    s0 = parts.get(0);
    s1 = parts.get(1);
  }

  @Override
  public RandomGenerator copyOriginally() {
    return new Xoroshiro128Random(initS0, initS1);
  }

  @Override
  public RandomGenerator copyCurrently() {
    return new Xoroshiro128Random(s0, s1);
  }

  @Override
  public RandomGenerator perturb(long x) {
    return new Xoroshiro128Random(s0 ^ x, s1 ^ (x * 0x9e3779b97f4a7c15L));
  }

  /**
   * Advances the state by 2^64 steps — equivalent to that many calls to {@link #nextLong()}.
   */
  public void jump() {
    applyJump(new long[] {0xdf900294d8f554a5L, 0x170865df4b3201fcL});
  }

  /**
   * Advances the state by 2^96 steps.
   */
  public void longJump() {
    applyJump(new long[] {0xd2a98b26625eee7bL, 0xdddf9b1090aa7ac1L});
  }

  private void applyJump(long[] jump) {
    long s0 = 0;
    long s1 = 0;
    for (long j : jump) {
      for (int b = 0; b < 64; b++) {
        if ((j & (1L << b)) != 0) {
          s0 ^= this.s0;
          s1 ^= this.s1;
        }
        nextLong();
      }
    }
    this.s0 = s0;
    this.s1 = s1;
  }
}
