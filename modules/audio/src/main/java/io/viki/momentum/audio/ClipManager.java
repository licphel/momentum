package io.viki.momentum.audio;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Owns explicitly registered clips and releases them when playback completes.
 *
 * <p>Registration transfers responsibility for closing the clip to this manager. Unregistering
 * transfers it back without stopping playback. Clips merely created or opened elsewhere are
 * never managed automatically; interrupted music belongs to {@link MusicManager} instead.
 *
 * <p>Registration, removal, polling, and closure are thread-safe. The supplied clips must
 * support playback observation and closure from the cleanup thread. Backend commands remain
 * responsible for their own audio-thread dispatch.
 */
public final class ClipManager implements AutoCloseable {
  /** Maximum interval between automatic completion checks, in milliseconds. */
  private static final long CLEANUP_INTERVAL_MILLIS = 100;
  private final Set<Clip> clips = Collections.newSetFromMap(new IdentityHashMap<>());
  private final Thread cleanupThread;
  private volatile boolean running = true;

  /**
   * Starts an owner for explicitly registered clips.
   *
   * <p>Cleanup runs on a daemon thread until this manager is closed.
   */
  public ClipManager() {
    cleanupThread = new Thread(this::cleanupLoop, "ClipManager-Cleanup");
    cleanupThread.setDaemon(true);
    cleanupThread.start();
  }

  /**
   * Takes responsibility for releasing a clip when its backend reports completion.
   *
   * <p>Registering the same instance repeatedly has no additional effect. Pending or paused
   * playback is not completion; only {@link Clip#shouldClose()} authorizes release.
   *
   * @param clip the clip whose lifecycle is transferred to this manager
   * @throws IllegalStateException if this manager has been closed
   */
  public synchronized void register(Clip clip) {
    if (!running) {
      throw new IllegalStateException("ClipManager is closed");
    }
    clips.add(clip);
  }

  /**
   * Transfers responsibility for a registered clip back to its caller.
   *
   * <p>This never stops or closes the clip and has no effect on an unregistered instance.
   *
   * @param clip the clip to remove from automatic management
   */
  public synchronized void unregister(Clip clip) {
    clips.remove(clip);
  }

  /**
   * Returns the number of clips whose release is currently managed.
   *
   * @return the registered clip count
   */
  public synchronized int activeCount() {
    return clips.size();
  }

  /**
   * Releases completed clips immediately instead of waiting for automatic polling.
   *
   * <p>Applications may call this after mixer polling for lower cleanup latency.
   * Calling it after closure has no effect.
   */
  public synchronized void update() {
    var iterator = clips.iterator();
    while (iterator.hasNext()) {
      Clip clip = iterator.next();
      if (clip.shouldClose()) {
        iterator.remove();
        clip.close();
      }
    }
  }

  @SuppressWarnings("BusyWait")
  private void cleanupLoop() {
    while (running) {
      try {
        Thread.sleep(CLEANUP_INTERVAL_MILLIS);
        update();
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  /**
   * Releases all registered clips and permanently rejects further registrations.
   *
   * <p>This also ends automatic polling. Repeated calls have no effect.
   */
  @Override
  public synchronized void close() {
    if (!running) {
      return;
    }
    running = false;
    cleanupThread.interrupt();
    for (Clip clip : clips) {
      clip.close();
    }
    clips.clear();
  }
}
