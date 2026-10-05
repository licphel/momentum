package io.viki.momentum.util;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Immutable outcome with an optional payload. Empty reason means success.
 * The wrapper is thread-safe; thread safety of its payload remains the caller's responsibility.
 *
 * @param <T> payload type
 */
public final class Result<T> {
  public static final Result<Void> OK = new Result<>("", null);
  private final String reason;
  private final @Nullable T value;

  private Result(String reason, @Nullable T value) {
    this.reason = reason;
    this.value = value;
  }

  /**
   * Returns the shared success without a payload. Does not allocate.
   *
   * @param <T> payload type
   * @return shared empty success
   */
  @SuppressWarnings("unchecked")
  public static <T> Result<T> ok() {
    return (Result<T>) OK;
  }

  /**
   * Creates a success carrying a value. Store the result statically when both outcome and value are fixed.
   *
   * @param value returned value
   * @param <T>   payload type
   * @return successful result
   */
  public static <T> Result<T> ok(T value) {
    return new Result<>("", value);
  }

  /**
   * Creates a failure. Fixed failures can be shared as static instances.
   *
   * @param reason failure reason
   * @param <T>    payload type
   * @return failure without a payload
   */
  public static <T> Result<T> failure(String reason) {
    if (reason.isEmpty()) {
      throw new IllegalArgumentException("Failure reason must not be empty");
    }
    return new Result<>(reason, null);
  }

  /**
   * Returns whether the operation succeeded.
   *
   * @return whether the operation succeeded
   */
  public boolean isOk() {
    return reason.isEmpty();
  }

  /**
   * Returns the failure reason, or empty if succeeded.
   *
   * @return the failure reason
   */
  public String reason() {
    return reason;
  }

  /**
   * Returns the payload, or null for a failure or a success without a payload
   *
   * @return the payload
   */
  public @Nullable T value() {
    return value;
  }

  @Override
  public int hashCode() {
    return 31 * reason.hashCode() + Objects.hashCode(value);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj == this) {
      return true;
    }
    return obj instanceof Result<?> that && reason.equals(that.reason) && Objects.equals(value, that.value);
  }

  @Override
  public String toString() {
    return "Result[reason=" + reason + ", value=" + value + ']';
  }
}
