package io.viki.momentum.util;

import java.util.function.Supplier;

/**
 * Supplies a primitive double precision floating-point value without requiring boxed results.
 *
 * <p>The result may change between invocations. Implementations determine whether concurrent
 * calls are supported; callers must respect the supplier's own thread-safety requirements.
 */
@FunctionalInterface
public interface DoubleSupplier extends Supplier<Double> {
  /**
   * Returns an immutable supplier for a fixed value.
   *
   * @param value the constant value
   * @return a constant supplier
   */
  static DoubleSupplier constant(double value) {
    return () -> value;
  }

  /**
   * Obtains the current value rather than a previously captured snapshot.
   *
   * @return the value available at invocation time
   */
  double getAsDouble();

  @Override
  default Double get() {
    return getAsDouble();
  }
}
