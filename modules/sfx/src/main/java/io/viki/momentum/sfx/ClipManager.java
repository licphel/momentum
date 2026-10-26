package io.viki.momentum.sfx;

import io.viki.momentum.util.multithread.Parallel;

import java.time.Duration;
import java.util.*;

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
  private static final long CLEANUP_INTERVAL_MILLIS = 500;
  private final List<Clip> clips = new LinkedList<>();
  private volatile boolean running = true;

  /**
   * Starts an owner for explicitly registered clips.
   *
   * <p>Cleanup runs on a daemon thread until this manager is closed.
   */
  public ClipManager() {
    Parallel.registerScheduledTask(Duration.ofMillis(CLEANUP_INTERVAL_MILLIS), this::update);
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
    try {
      clip.__enableNativeAutoClosure();
    } catch (Exception _) {
      clips.add(clip); // Native auto closure is not supported. Fallback to our management.
    }
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
   * <p>May not be the same as the count of playing clips.
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
    for (int i = clips.size() - 1; i >= 0; i--) {
      Clip clip = clips.get(i);
      if (clip.shouldClose()) {
        clips.remove(i);
        clip.close();
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
    for (Clip clip : clips) {
      clip.close();
    }
    clips.clear();
  }
}
