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

package io.viki.momentum.sfx.openal;

import io.viki.momentum.logging.Log;
import io.viki.momentum.logging.Logger;
import io.viki.momentum.math.Quaternion;
import io.viki.momentum.math.Vector3;
import io.viki.momentum.sfx.*;
import io.viki.momentum.sfx.ext.SfxEffect;
import io.viki.momentum.sfx.openal.ext.OpenALEffects;
import io.viki.momentum.util.InternalApi;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
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
  /** Maximum concurrent in-memory effect sources; streaming music is independent. */
  public static final int DEFAULT_SOURCE_CAPACITY = 32;
  /** Maximum auxiliary sends requested from an OpenAL context. */
  public static final int AUXILIARY_SEND_CAPACITY = 4;
  /** Sends reserved for mixer-owned environmental effects. */
  public static final int MIXER_EFFECT_SENDS = 2;
  private static final Logger LOGGER = Log.getLogger();
  private static final int QUEUE_CAPACITY = 128;

  static {
    new OpenALEffects();
  }

  final List<OpenALAudioBuffer> buffers = new ArrayList<>();
  private final List<OpenALClip> trackingList = new LinkedList<>();
  private final List<OpenALStreamingClip> streamingTrackingList = new LinkedList<>();
  private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
  private final AtomicBoolean running = new AtomicBoolean(true);
  private final Thread audioThread;
  private final int[] sources;
  private final int[] availableSources;
  private final int[] effectSlots = new int[AUXILIARY_SEND_CAPACITY];
  private final int[] globalDirectFilters = new int[MIXER_EFFECT_SENDS];
  private final boolean[] clipEffectSlots = new boolean[AUXILIARY_SEND_CAPACITY];
  private final SfxEffect[] mixerEffects = {
      SfxEffect.NONE, SfxEffect.NONE
  };
  private int availableCount;
  private long lastCheckErrorMs;
  private boolean debug = false;
  private volatile boolean spatialAudioEnabled = true;
  private volatile RolloffMode rolloffMode = RolloffMode.DEFAULT;
  private volatile float listenerX;
  private volatile float listenerY;
  private volatile float listenerZ;

  /**
   * Creates a new mixer, opens the default OpenAL device and context, and starts the audio thread.
   *
   * <p>Construction returns immediately; the device/context initialization
   * happens asynchronously on the audio thread. If device or context creation fails, the audio thread throws
   * {@link IllegalStateException}.
   */
  public OpenALMixer() {
    this(DEFAULT_SOURCE_CAPACITY);
  }

  /**
   * Creates a mixer with a fixed in-memory voice budget. Exhausted play requests
   * are dropped rather than blocking or allocating additional native sources.
   *
   * @param sourceCapacity maximum simultaneous effect sources, greater than zero
   */
  public OpenALMixer(int sourceCapacity) {
    if (sourceCapacity <= 0) {
      throw new IllegalArgumentException("Source capacity must be positive: " + sourceCapacity);
    }
    sources = new int[sourceCapacity];
    availableSources = new int[sourceCapacity];
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

  @Override
  public boolean isSpatialAudioEnabled() {
    return spatialAudioEnabled;
  }

  @Override
  public void setSpatialAudioEnabled(boolean enabled) {
    spatialAudioEnabled = enabled;
    submit(() -> {
      for (OpenALClip clip : trackingList) {
        clip.applySpatialPosition();
      }
      for (OpenALStreamingClip clip : streamingTrackingList) {
        clip.applySpatialPosition();
      }
    });
  }

  @Override
  public RolloffMode getRolloffMode() {
    return rolloffMode;
  }

  @Override
  public void setRolloffMode(RolloffMode mode) {
    rolloffMode = mode;
  }

  @Override
  public float getRolloffGain(float x, float y, float z) {
    float dx = x - listenerX;
    float dy = y - listenerY;
    float dz = z - listenerZ;
    return rolloffMode.gain((float) Math.sqrt(dx * dx + dy * dy + dz * dz));
  }

  @Override
  public void setListenerPosition(float x, float y, float z) {
    listenerX = x;
    listenerY = y;
    listenerZ = z;
    submit(() -> alListener3f(AL_POSITION, x, y, z));
  }

  @Override
  public void setListenerOrientation(Quaternion orientation) {
    Vector3 forward = orientation.rotate(Vector3.UNIT_X);
    Vector3 up = orientation.rotate(Vector3.UNIT_Z);
    float[] values = {
        forward.x(), forward.y(), forward.z(),
        up.x(), up.y(), up.z()
    };
    submit(() -> alListenerfv(AL_ORIENTATION, values));
  }

  @Override
  public void setGlobalEffect(int slot, SfxEffect effect) {
    if (slot < 0 || slot >= MIXER_EFFECT_SENDS) {
      throw new IllegalArgumentException("Mixer effect slot out of range: " + slot);
    }
    submit(() -> {
      SfxEffect previous = mixerEffects[slot];
      if (previous == effect) {
        return;
      }
      int effectSlot = effectSlots[slot];
      if (effectSlot != 0) {
        previous.detach(this, effectSlot);
        effect.attach(this, effectSlot);
      }
      mixerEffects[slot] = effect;
      for (OpenALClip clip : trackingList) {
        clip.applyMixerEffects();
      }
      for (OpenALStreamingClip clip : streamingTrackingList) {
        clip.applyMixerEffects();
      }
    });
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
      /*
       * OpenAL does not provide loop checkpoints
       * so we have to track all playing clips.
       *
       * Note that we do not close them. This is not our duty here.
       */
      for (Iterator<OpenALClip> it = trackingList.iterator(); it.hasNext(); ) {
        OpenALClip clip = it.next();

        clip.poll();
        if (clip.shouldClose()) {
          it.remove();
          if (clip.autoClosure) {
            clip.close();
          }
        }
      }

      for (Iterator<OpenALStreamingClip> it = streamingTrackingList.iterator(); it.hasNext(); ) {
        OpenALStreamingClip clip = it.next();

        clip.poll();

        if (clip.shouldClose()) {
          it.remove();
          if (clip.autoClosure) {
            clip.close();
          }
        }
      }
    });
  }

  @Override
  public Clip getClip() {
    return new OpenALClip(this);
  }

  @Override
  public AudioBuffer createBuffer(AudioFormat format, byte[] data) {
    return new OpenALAudioBuffer(this, format, data);
  }

  @Override
  public StreamingClip getStreamingClip() {
    return new OpenALStreamingClip(this);
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
    if (Thread.currentThread() == audioThread) {
      work.run();
      return;
    }
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
   * asynchronously on that thread after pending commands have been completed.
   * Close owned clips before closing their mixer so queued cleanup can finish.
   */
  @Override
  public void close() {
    running.set(false);
    audioThread.interrupt();
  }

  int acquireSource() {
    return availableCount == 0 ? 0 : availableSources[--availableCount];
  }

  void releaseSource(int source) {
    alSourceStop(source);
    alSourcei(source, AL_BUFFER, 0);
    alSourcei(source, AL_LOOPING, AL_FALSE);
    availableSources[availableCount++] = source;
  }

  /**
   * Attaches the reserved mixer sends to a source before clip-owned effects.
   * This method is called only on the OpenAL thread.
   */
  void attachGlobalEffects(int source) {
    applyGlobalDirectFilter(source);
    for (int send = 0; send < MIXER_EFFECT_SENDS; send++) {
      int slot = effectSlots[send];
      if (slot == 0 || mixerEffects[send] == SfxEffect.NONE || globalDirectFilters[send] != 0) {
        clearAuxiliarySend(source, send);
      } else {
        alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER,
            slot, send, EXTEfx.AL_FILTER_NULL);
      }
    }
  }

  /**
   * Updates a shared direct filter on the OpenAL thread; slot is a native auxiliary slot handle.
   *
   * @param slot  slot id
   * @param filter filter id
   */
  public void setGlobalDirectFilter(int slot, int filter) {
    for (int send = 0; send < MIXER_EFFECT_SENDS; send++) {
      if (effectSlots[send] == slot) {
        globalDirectFilters[send] = filter;
        return;
      }
    }
  }

  private void applyGlobalDirectFilter(int source) {
    if (!AL.getCapabilities().ALC_EXT_EFX) {
      return;
    }
    for (int filter : globalDirectFilters) {
      if (filter != 0) {
        alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filter);
        return;
      }
    }
  }

  /**
   * Clears only the reserved mixer sends from a source.
   * This method is called after clip-owned effects have detached.
   */
  void detachGlobalEffects(int source) {
    clearDirectFilter(source);
    for (int send = 0; send < MIXER_EFFECT_SENDS; send++) {
      clearAuxiliarySend(source, send);
    }
  }

  void clearDirectFilter(int source) {
    if (AL.getCapabilities().ALC_EXT_EFX) {
      alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
    }
  }

  private static void clearAuxiliarySend(int source, int send) {
    if (AL.getCapabilities().ALC_EXT_EFX) {
      alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER,
          EXTEfx.AL_EFFECTSLOT_NULL, send, EXTEfx.AL_FILTER_NULL);
    }
  }

  /**
   * Acquires one of the sends left for a clip-owned auxiliary effect.
   * This method is called only on the OpenAL thread.
   *
   * @return the logical send index, or {@code -1} when no send is available
   */
  public int acquireClipEffectSlot() {
    for (int send = MIXER_EFFECT_SENDS; send < effectSlots.length; send++) {
      if (effectSlots[send] != 0 && !clipEffectSlots[send]) {
        clipEffectSlots[send] = true;
        return send;
      }
    }
    return -1;
  }

  /**
   * Releases a clip-owned auxiliary effect send.
   * This method is called only on the OpenAL thread.
   *
   * @param send the AL send index
   */
  public void releaseClipEffectSlot(int send) {
    if (send >= MIXER_EFFECT_SENDS && send < clipEffectSlots.length) {
      clipEffectSlots[send] = false;
    }
  }

  /**
   * Returns the native slot handle for a logical send index.
   * This method is called only on the OpenAL thread.
   *
   * @param send the AL send index
   * @return the slot id of send
   */
  public int effectSlotId(int send) {
    return effectSlots[send];
  }

  /**
   * Clears an effect object from a reusable auxiliary slot.
   * This method is called only on the OpenAL thread.
   *
   * @param send the AL send index
   */
  public void clearEffectSlot(int send) {
    int slot = effectSlots[send];
    if (slot != 0) {
      EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, EXTEfx.AL_EFFECT_NULL);
    }
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

    boolean efxRequested = alcIsExtensionPresent(device, "ALC_EXT_EFX");
    IntBuffer attrs = memAllocInt(efxRequested ? 3 : 1);
    if (efxRequested) {
      attrs.put(EXTEfx.ALC_MAX_AUXILIARY_SENDS).put(AUXILIARY_SEND_CAPACITY);
    }
    attrs.put(0).flip();
    long context = ALC10.alcCreateContext(device, attrs);
    memFree(attrs);

    if (context == 0L) {
      alcCloseDevice(device);
      throw new IllegalStateException("OpenAL failed to create context");
    }

    alcMakeContextCurrent(context);
    AL.createCapabilities(ALC.createCapabilities(device));
    if (AL.getCapabilities().ALC_EXT_EFX) {
      for (int send = 0; send < effectSlots.length; send++) {
        effectSlots[send] = EXTEfx.alGenAuxiliaryEffectSlots();
        if (effectSlots[send] == 0) {
          break;
        }
      }
    }
    for (int i = 0; i < sources.length; i++) {
      sources[i] = alGenSources();
      if (sources[i] == 0) {
        throw new IllegalStateException("Failed to allocate OpenAL source pool at " + i);
      }
      availableSources[availableCount++] = sources[i];
    }

    // Blocks and waits for consuming tasks.
    while (running.get()) {
      try {
        // Block until work arrives, then drain any accumulated batch
        List<Runnable> batch = new ArrayList<>();
        batch.add(queue.take());
        queue.drainTo(batch);
        for (Runnable task : batch) {
          consume(task);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }

    // Clip owners queue cleanup before stopping the mixer; release streams
    // and native objects while their context still exists.
    Runnable pending;
    while ((pending = queue.poll()) != null) {
      consume(pending);
    }
    for (int source : sources) {
      alSourceStop(source);
      alSourcei(source, AL_BUFFER, 0);
      alDeleteSources(source);
    }
    while (!buffers.isEmpty()) {
      buffers.getLast().delete();
    }
    for (int send = 0; send < MIXER_EFFECT_SENDS; send++) {
      int slot = effectSlots[send];
      if (slot != 0) {
        mixerEffects[send].detach(this, slot);
        EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT,
            EXTEfx.AL_EFFECT_NULL);
      }
    }
    for (int send = 0; send < effectSlots.length; send++) {
      if (effectSlots[send] != 0) {
        EXTEfx.alDeleteAuxiliaryEffectSlots(effectSlots[send]);
        effectSlots[send] = 0;
      }
    }
    alcDestroyContext(context);
    alcCloseDevice(device);
  }

  private void consume(Runnable task) {
    try {
      task.run();
    } catch (Exception exception) {
      LOGGER.warn("OpenAL consumer fault", exception);
    }
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

  void track(OpenALStreamingClip clip) {
    if (!streamingTrackingList.contains(clip)) {
      streamingTrackingList.addLast(clip);
    }
  }

  void untrack(OpenALClip clip) {
    trackingList.remove(clip);
  }

  void untrack(OpenALStreamingClip clip) {
    streamingTrackingList.remove(clip);
  }

  void applyMixerEffects(int source) {
    attachGlobalEffects(source);
  }
}
