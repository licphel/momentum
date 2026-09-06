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

package io.viki.momentum.config;

import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Validates a value before it is accepted.
 *
 * <p>Implementations return {@code null} to indicate the value is acceptable,
 * or an error message string describing why it should be rejected.
 *
 * <p>Static factory methods provide common validators for ranges, set membership,
 * and non-blank strings.
 *
 * @param <T> the value type this validator checks
 * @see Config
 * @see ConfigSpec
 */
@FunctionalInterface
public interface Validator<T> {
  /**
   * Returns a validator that requires an integer within {@code [min, max]}.
   *
   * @param min the minimum allowed value, inclusive
   * @param max the maximum allowed value, inclusive
   * @return a validator that checks the range
   */
  static Validator<Integer> rangedInt(int min, int max) {
    return value -> {
      if (value < min || value > max) {
        return "Value must be between " + min + " and " + max + ", got " + value;
      }
      return null;
    };
  }

  /**
   * Returns a validator that requires a long within {@code [min, max]}.
   *
   * @param min the minimum allowed value, inclusive
   * @param max the maximum allowed value, inclusive
   * @return a validator that checks the range
   */
  static Validator<Long> rangedLong(long min, long max) {
    return value -> {
      if (value < min || value > max) {
        return "Value must be between " + min + " and " + max + ", got " + value;
      }
      return null;
    };
  }

  /**
   * Returns a validator that requires a double within {@code [min, max]}.
   *
   * @param min the minimum allowed value, inclusive
   * @param max the maximum allowed value, inclusive
   * @return a validator that checks the range
   */
  static Validator<Double> rangedDouble(double min, double max) {
    return value -> {
      if (value < min || value > max) {
        return "Value must be between " + min + " and " + max + ", got " + value;
      }
      return null;
    };
  }

  /**
   * Returns a validator that requires a float within {@code [min, max]}.
   *
   * @param min the minimum allowed value, inclusive
   * @param max the maximum allowed value, inclusive
   * @return a validator that checks the range
   */
  static Validator<Float> rangedFloat(float min, float max) {
    return value -> {
      if (value < min || value > max) {
        return "Value must be between " + min + " and " + max + ", got " + value;
      }
      return null;
    };
  }

  /**
   * Returns a validator that requires a non-blank string.
   *
   * @return a validator that rejects blank strings
   */
  static Validator<String> nonBlank() {
    return value -> {
      if (value.isBlank()) {
        return "String must not be blank";
      }
      return null;
    };
  }

  /**
   * Returns a validator that requires the value to be one of the given options.
   *
   * @param values the allowed values
   * @param <T>    the value type
   * @return a validator that checks for set membership
   */
  static <T> Validator<T> oneOf(Object... values) {
    Set<?> set = Set.of(values);
    return value -> {
      if (!set.contains(value)) {
        return "Value must be one of " + set + ", got " + value;
      }
      return null;
    };
  }

  /**
   * Validates the given value.
   *
   * @param value the value to check
   * @return {@code null} if the value is valid, or an error message
   * describing why it should be rejected
   */
  @Nullable String validate(T value);
}
