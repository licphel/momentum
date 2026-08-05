package net.fmhi.util;

/**
 * Represents an integer handle provider.
 *
 * <p>This is used for backend abstraction. Do not use it.
 */
@InternalApi
public interface Handle {
  /**
   * Returns the native opaque handle.
   *
   * @param slot the handle slot - a handle object may contain multiple slots
   * @return native handle
   */
  int handle(int slot);

  /**
   * Returns the native opaque handle at 0.
   *
   * @return native handle
   */
  default int handle() {
    return handle(0);
  }
}
