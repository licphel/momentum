package net.momentum.util;

import java.io.Serial;

/**
 * Thrown to indicate that the current {@link Platform} is not supported by an operation.
 *
 * @see Platform
 */
public class PlatformNotSupportedException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 2026072400L;

  /**
   * Creates a new exception with the given detail message.
   *
   * @param message the detail message
   */
  public PlatformNotSupportedException(String message) {
    super(message);
  }
}
