package io.viki.momentum.ai;

import java.io.Serial;

/** Reports a failure while loading or using a native language model. */
public final class LLMException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 2026092500L;

  /**
   * Creates a model failure with a descriptive message and its underlying cause.
   *
   * @param message context describing the failed model operation
   * @param cause underlying native or integration failure
   */
  public LLMException(String message, Throwable cause) {
    super(message, cause);
  }
}
