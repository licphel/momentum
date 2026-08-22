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

import java.util.Objects;
import java.util.function.Function;

/**
 * A simple pair of two values.
 *
 * @param left  the first value
 * @param right the second value
 * @param <L>   the type of the left value
 * @param <R>   the type of the right value
 */
public record Pair<L, R>(L left, R right) {
  /**
   * Creates a new pair.
   *
   * @param left  the first value
   * @param right the second value
   * @param <L>   the type of the left value
   * @param <R>   the type of the right value
   * @return a new pair
   */
  public static <L, R> Pair<L, R> of(L left, R right) {
    return new Pair<>(left, right);
  }

  /**
   * Returns the left value.
   *
   * @return the left value
   */
  public L left() {
    return left;
  }

  /**
   * Returns the right value.
   *
   * @return the right value
   */
  public R right() {
    return right;
  }

  /**
   * Maps the left value using the given function.
   *
   * @param mapper the mapping function
   * @param <NL>   the new left type
   * @return a new pair with the mapped left value
   */
  public <NL> Pair<NL, R> mapLeft(Function<L, NL> mapper) {
    return new Pair<>(mapper.apply(left), right);
  }

  /**
   * Maps the right value using the given function.
   *
   * @param mapper the mapping function
   * @param <NR>   the new right type
   * @return a new pair with the mapped right value
   */
  public <NR> Pair<L, NR> mapRight(Function<R, NR> mapper) {
    return new Pair<>(left, mapper.apply(right));
  }

  /**
   * Maps both values using the given functions.
   *
   * @param leftMapper  the left mapping function
   * @param rightMapper the right mapping function
   * @param <NL>        the new left type
   * @param <NR>        the new right type
   * @return a new pair with mapped values
   */
  public <NL, NR> Pair<NL, NR> mapBoth(Function<L, NL> leftMapper, Function<R, NR> rightMapper) {
    return new Pair<>(leftMapper.apply(left), rightMapper.apply(right));
  }

  /**
   * Swaps the left and right values.
   *
   * @return a new pair with swapped values
   */
  public Pair<R, L> swap() {
    return new Pair<>(right, left);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Pair<?, ?>(Object left1, Object right1))) {
      return false;
    }
    return Objects.equals(left, left1) && Objects.equals(right, right1);
  }

  @Override
  public int hashCode() {
    return Objects.hash(left, right);
  }

  @Override
  public String toString() {
    return "Pair[" + left + ", " + right + "]";
  }
}