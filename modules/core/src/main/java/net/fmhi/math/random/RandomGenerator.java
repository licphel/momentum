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

import java.util.List;

/**
 * Base interface for pseudo-random number generators (PRNGs).
 *
 * <p>All implementations must be deterministic: given the same initial state,
 * they produce the same sequence of random numbers. This is essential for reproducible simulations.
 *
 * <p>The default instance is {@link Xoroshiro128Random}, a fast, high-quality
 * generator suitable for most scenarios.
 */
public interface RandomGenerator {
  /**
   * Global default instance (Xoroshiro128+).
   *
   * <p>This generator is thread-safe but not recommended for concurrent
   * use from multiple threads. For multithreaded scenarios, create separate instances per thread.
   */
  RandomGenerator DEFAULT = new Xoroshiro128Random();

  /**
   * Returns a random {@code double} in {@code [0, 1)}.
   *
   * <p>The returned value is uniformly distributed in the half-open interval.
   * The upper bound (1.0) is exclusive.
   *
   * @return a random double between 0 (inclusive) and 1 (exclusive)
   */
  double nextDouble();

  /**
   * Returns a random non-negative {@code int} in {@code [0, bound)}.
   *
   * @param bound the upper bound (exclusive); must be positive
   * @return a random int between 0 (inclusive) and {@code bound} (exclusive)
   * @throws IllegalArgumentException if {@code bound <= 0}
   */
  int nextInt(int bound);

  /**
   * Returns a random boolean (50% chance).
   *
   * @return true or false with equal probability
   */
  default boolean nextBool() {
    return nextDouble() < 0.5;
  }

  /**
   * Returns a random {@code double} in {@code [min, max)}.
   *
   * @param min lower bound (inclusive)
   * @param max upper bound (exclusive); must be greater than {@code min}
   * @return a random double in the specified range
   */
  default double nextDouble(double min, double max) {
    return min + nextDouble() * (max - min);
  }

  /**
   * Returns a random {@code int} in {@code [min, max)}.
   *
   * @param min lower bound (inclusive)
   * @param max upper bound (exclusive); must be greater than {@code min}
   * @return a random int in the specified range
   */
  default int nextInt(int min, int max) {
    return min + nextInt(max - min);
  }

  /**
   * Selects a random element from a list.
   *
   * @param list the list to select from; must not be empty
   * @param <T>  the element type
   * @return a randomly selected element
   * @throws IllegalArgumentException if the list is empty
   */
  default <T> T select(List<T> list) {
    return list.get(nextInt(list.size()));
  }

  /**
   * Selects a random element from an array.
   *
   * @param items the array to select from; must not be empty
   * @param <T>   the element type
   * @return a randomly selected element
   * @throws IllegalArgumentException if the array is empty
   */
  @SuppressWarnings("unchecked")
  default <T> T select(T... items) {
    return items[nextInt(items.length)];
  }

  /**
   * Returns a random Gaussian (normal) distribution value mapped to {@code [0, 1]}.
   *
   * <p>Uses the Box-Muller transform to generate a standard normal deviate,
   * then applies {@code tanh(z * 0.5)} to map it to the approximate range {@code [0, 1]}. The mapping is sigmoidal:
   * most values cluster near 0.5, with tails approaching 0 and 1.
   *
   * @return a pseudo-Gaussian value in {@code [0, 1]}
   */
  default double nextGaussianDouble() {
    double u1 = nextDouble();
    double u2 = nextDouble();
    double z = Math.sqrt(-2.0 * Math.log(Math.max(u1, 1E-10))) * Math.cos(2.0 * Math.PI * u2);
    return (Math.tanh(z * 0.5) + 1.0) * 0.5;
  }

  /**
   * Returns a random Gaussian value mapped to {@code [min, max]}.
   *
   * @param min lower bound (inclusive)
   * @param max upper bound (inclusive); must be greater than {@code min}
   * @return a pseudo-Gaussian value in the specified range
   */
  default double nextGaussianDouble(double min, double max) {
    return min + nextGaussianDouble() * (max - min);
  }

  /**
   * Returns the current internal state of the generator as a string. The length and interpretation of the string are
   * implementation-specific.
   *
   * @return a copy of the current state
   */
  String state();

  /**
   * Restores the generator to a previously saved state.
   *
   * <p>The string must have been obtained from {@link #state()} of the same
   * generator implementation.
   *
   * @param state the state to restore
   * @throws IllegalArgumentException if the state is invalid for this generator
   */
  void recover(String state);

  /**
   * Creates a copy of this generator with its original (initial) state.
   *
   * <p>The new generator behaves as if it was just created with the same seed
   * as this generator's initial state.
   *
   * @return a new generator with the same initial state
   */
  RandomGenerator copyOriginally();

  /**
   * Creates a copy of this generator with its current state.
   *
   * <p>The new generator will produce the same future sequence as this generator.
   *
   * @return a new generator with the same current state
   */
  RandomGenerator copyCurrently();

  /**
   * Creates a derived generator with a perturbed state.
   *
   * <p>Different {@code x} values produce statistically independent sequences.
   * This is not a true state-space jump, but a fast way to generate multiple independent RNGs from a single parent.
   *
   * @param x perturbation value (different values yield different sequences)
   * @return a new generator with perturbed state
   */
  RandomGenerator perturb(long x);
}