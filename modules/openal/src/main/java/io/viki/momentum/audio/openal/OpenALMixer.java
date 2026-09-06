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

package io.viki.momentum.audio.openal;

import io.viki.momentum.audio.Clip;
import io.viki.momentum.audio.Mixer;
import io.viki.momentum.internal.InternalApi;
import io.viki.momentum.logging.Log;
import io.viki.momentum.logging.Logger;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.lwjgl.openal.AL11.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.system.MemoryUtil.memAllocInt;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * OpenAL-backed {@link Mixer} that serializes all OpenAL calls onto a single dedicated audio thread.
 *
 * <p>OpenAL contexts are not thread-safe; every AL/ALC call must happen on
 * the thread that called {@code alcMakeContextCurrent}. This class enforces that invariant by routing all work through
 * a bounded {@link BlockingQueue} consumed by one private daemon thread.
 *
 * <p>The audio thread is started immediately in the constructor. It
 * initializes the OpenAL device and context, then blocks on the queue until {@link #close()} interrupts it, at which
 * point it destroys the context and device before exiting.
 */
@InternalApi
public final class OpenALMixer implements Mixer {
  private static final Logger LOGGER = Log.getLogger();
  private static final int QUEUE_CAPACITY = 128;

  private final List<OpenALClip> trackingList = new CopyOnWriteArrayList<>();
  private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
  private final AtomicBoolean running = new AtomicBoolean(true);
  private final Thread audioThread;
  private long lastCheckErrorMs;
  private boolean debug = false;

  /**
   * Creates a new mixer, opens the default OpenAL device and context, and starts the audio thread.
   *
   * <p>Construction returns immediately; the device/context initialization
   * happens asynchronously on the audio thread. If device or context creation fails, the audio thread throws
   * {@link IllegalStateException}.
   */
  @InternalApi
  public OpenALMixer() {
    audioThread = new Thread(this::run, "OpenAL-Mixer");
    audioThread.setDaemon(true);
    audioThread.start();
  }

  /**
   * Sets whether to get debug outputs.
   *
   * @param debug debug flag
   * @return this for chaining
   */
  public OpenALMixer setDebug(boolean debug) {
    this.debug = debug;
    return this;
  }

  public void pollEvents() {
    if (debug) {
      long ms = System.currentTimeMillis();

      if (ms - lastCheckErrorMs > 1000) {
        lastCheckErrorMs = ms;

        submit(() -> {
          int err;
          while ((err = alGetError()) != AL_NO_ERROR) {
            LOGGER.warn("OpenAL error: 0x{}", Integer.toHexString(err));
          }
        });
      }
    }

    submit(() -> {
      List<OpenALClip> gc = new ArrayList<>();

      /*
       * OpenAL does not provide loop checkpoints
       * so we have to track all playing clips.
       *
       * Note that we do not close them. This is not our duty here.
       */
      for (OpenALClip clip : trackingList) {
        clip.state = alGetSourcei(clip.source, AL_SOURCE_STATE);
        clip.offset = alGetSourcef(clip.source, AL_SEC_OFFSET);

        if (!clip.isPlaying()) {
          if (clip.remainingLoops <= 0) {
            gc.add(clip);
            clip.shouldClose = true;
          } else {
            clip.loop(clip.remainingLoops);
          }
        }
      }

      trackingList.removeAll(gc);
    });
  }

  @Override
  public Clip getClip() {
    return new OpenALClip(this);
  }

  /**
   * Submits a task to the audio thread.
   *
   * <p>The task is executed on the OpenAL context thread.
   * Blocks if the internal queue is full.
   *
   * @param work the task to execute on the audio thread
   */
  @Override
  public void submit(Runnable work) {
    try {
      queue.put(work);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Stops the audio thread and releases the OpenAL context and device.
   *
   * <p>Interrupts the audio thread; context and device destruction happen
   * asynchronously on that thread after it wakes up.
   */
  @Override
  public void close() {
    running.set(false);
    audioThread.interrupt();
  }

  void run() {
    /*
     * Initialize OpenAL.
     * This process should be done on this thread so that OpenAL context can bind to it.
     */
    long device = alcOpenDevice((ByteBuffer) null);
    if (device == 0L) {
      throw new IllegalStateException("OpenAL failed to open device");
    }

    IntBuffer attrs = memAllocInt(1);
    attrs.put(0).flip();
    long context = ALC10.alcCreateContext(device, attrs);
    memFree(attrs);

    if (context == 0L) {
      alcCloseDevice(device);
      throw new IllegalStateException("OpenAL failed to create context");
    }

    alcMakeContextCurrent(context);
    AL.createCapabilities(ALC.createCapabilities(device));

    // Blocks and waits for consuming tasks.
    while (running.get()) {
      try {
        // Block until work arrives, then drain any accumulated batch
        List<Runnable> batch = new ArrayList<>();
        batch.add(queue.take());
        queue.drainTo(batch);
        for (Runnable task : batch) {
          try {
            task.run();
          } catch (Exception e) {
            LOGGER.warn("OpenAL consumer fault", e);
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }

    alcDestroyContext(context);
    alcCloseDevice(device);
  }

  /**
   * Adds a clip to the tracking list for loop management.
   *
   * <p>Tracked clips are polled in {@link #pollEvents()} to
   * detect playback completion and trigger subsequent loops. Has no effect if the clip is already tracked.
   *
   * @param clip the clip to track
   */
  void track(OpenALClip clip) {
    if (!trackingList.contains(clip)) {
      trackingList.addLast(clip);
    }
  }

  /**
   * Removes a clip from the tracking list.
   *
   * @param clip the clip to untrack
   */
  void untrack(OpenALClip clip) {
    trackingList.remove(clip);
  }
}