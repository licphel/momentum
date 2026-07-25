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

package net.fmhi.audio;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages automatic cleanup of {@link Clip}s that have finished playing.
 *
 * <p>After registering a clip with {@link #register(Clip)}, a background
 * thread monitors the clip and calls {@link Clip#close()} once playback completes. This removes the need for callers to
 * track playback completion manually.
 *
 * <p>Clips that are still playing when the manager is closed are stopped
 * and released immediately.
 *
 * @see Clip
 */
public final class ClipManager implements AutoCloseable {
  private final Map<Integer, Clip> clips = new ConcurrentHashMap<>();
  private final Thread cleanupThread;
  private volatile boolean running = true;

  /**
   * Creates a new {@code ClipManager} and starts the background cleanup thread.
   */
  public ClipManager() {
    cleanupThread = new Thread(this::cleanupLoop, "ClipManager-Cleanup");
    cleanupThread.setDaemon(true);
    cleanupThread.start();
  }

  /**
   * Registers a clip for automatic lifecycle management.
   *
   * <p>Once registered, the clip will be closed automatically when
   * {@link Clip#shouldClose()} returns {@code true}. Registering the same clip instance more than once has no
   * additional effect.
   *
   * @param clip the clip to manage
   */
  public void register(Clip clip) {
    clips.put(System.identityHashCode(clip), clip);
  }

  /**
   * Removes a clip from automatic management without closing it.
   *
   * <p>Has no effect if the clip is not currently registered.
   *
   * @param clip the clip to stop managing
   */
  public void unregister(Clip clip) {
    clips.remove(System.identityHashCode(clip));
  }

  /**
   * Returns the number of clips currently registered with this manager.
   *
   * @return number of registered clips
   */
  public int activeCount() {
    return clips.size();
  }

  /**
   * Runs the cleanup loop, periodically checking registered clips and closing those that have finished playing.
   */
  @SuppressWarnings("BusyWait")
  private void cleanupLoop() {
    while (running) {
      try {
        Thread.sleep(100);

        for (Map.Entry<Integer, Clip> entry : clips.entrySet()) {
          Clip clip = entry.getValue();

          /*
           * Do not check Clip.isPlaying here.
           * We do not guarantee that method will be implemented properly
           * on every backend, but Clip.shouldClose does so.
           */
          if (clip.shouldClose()) {
            clip.close();
            clips.remove(entry.getKey());
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  @Override
  public void close() {
    running = false;
    cleanupThread.interrupt();

    for (Clip clip : clips.values()) {
      clip.close();
    }
  }
}
