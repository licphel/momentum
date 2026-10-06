package io.viki.momentum.util;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.function.Consumer;

/**
 * Recycles objects without imposing their creation, reset, or disposal policy.
 *
 * <p>A successful acquisition transfers exclusive use of an object to the caller.
 * Release it exactly once, only after all users have finished, and do not access it
 * afterward. Callers must clear retained references and reset reusable state before
 * release, or fully initialize that state before the next use. Identity and duplicate
 * release checks are deliberately omitted from the hot path.
 *
 * <p>Thread-safe: acquisition, release, and clearing synchronize only access to the
 * available objects. They do not make acquired objects thread-safe. Release publishes
 * preceding writes to a subsequent acquiring thread. Creation and disposal remain
 * outside the pool's lock.
 *
 * <p>Acquisition is O(1); release is amortized O(1). Once sufficient storage is
 * available, neither operation allocates queue nodes. Available objects are retained
 * until acquired or cleared; the pool does not impose a retention limit.
 *
 * @param <T> the type of reusable object
 */
public final class Pool<T> {
  private final ArrayDeque<T> available = new ArrayDeque<>();

  /**
   * Creates an empty pool. Objects are created and owned by its callers.
   */
  public Pool() {
  }

  /**
   * Removes an available object for exclusive use, without creating one.
   *
   * @return a reusable object, or {@code null} if the caller must create one
   */
  public synchronized @Nullable T poll() {
    return available.pollFirst();
  }

  /**
   * Returns an object whose previous use has completely finished.
   *
   * <p>The caller is responsible for resetting its state and must not release the
   * same object twice or release an object still in use by another thread.
   *
   * @param object the reusable object to transfer back to this pool
   */
  public synchronized void release(T object) {
    available.addFirst(object);
  }

  /**
   * Discards references to all currently available objects without disposing them.
   *
   * <p>Acquired objects are unaffected and may subsequently be released again.
   * This operation is O(n) in the number of available objects.
   */
  public synchronized void clear() {
    available.clear();
  }

  /**
   * Removes and disposes available objects, leaving this pool reusable.
   *
   * <p>Disposal runs outside the lock. Concurrent releases may be included or remain
   * in the pool; stop producers first when a complete shutdown drain is required.
   * Acquired objects are unaffected. If disposal throws, that object has already
   * been removed and the remaining objects stay available.
   *
   * @param disposer the operation that releases each object's external resources
   */
  public void clear(Consumer<? super T> disposer) {
    T object;
    while ((object = poll()) != null) {
      disposer.accept(object);
    }
  }
}
