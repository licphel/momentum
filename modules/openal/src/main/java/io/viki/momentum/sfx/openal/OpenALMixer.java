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
import io.viki.momentum.util.multithread.ExecutionMode;
import io.viki.momentum.util.InternalApi;
import io.viki.momentum.util.Result;
import org.lwjgl.openal.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.lwjgl.openal.AL11.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.system.MemoryUtil.memAllocInt;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * OpenAL-backed {@link Mixer} that serializes native calls onto one owning thread.
 *
 * <p>{@link ExecutionMode#DEDICATED_THREAD} owns a daemon audio thread. In
 * {@link ExecutionMode#BOUND_THREAD}, construction, {@link #pollEvents()}, and {@link #close()}
 * belong to the creating thread (normally the main thread); submissions on that thread execute
 * immediately, while other threads enqueue commands. The owner must poll regularly to service
 * cross-thread work and streaming playback. Other public commands may be submitted concurrently.
 */
@InternalApi
public final class OpenALMixer implements Mixer {
  /** Maximum concurrent in-memory effect sources; streaming music is independent. */
  public static final int DEFAULT_SOURCE_CAPACITY = 32;
  /** Maximum auxiliary sends requested from an OpenAL context. */
  public static final int AUXILIARY_SEND_CAPACITY = 4;
  /** Sends reserved for mixer-owned environmental effects. */
  public static final int MIXER_EFFECT_SENDS = 2;
  private static final double BOUND_THREAD_PRESSURE = 0.75;
  private static final Logger LOGGER = Log.getLogger();
  private static final int QUEUE_CAPACITY = 128;
  /** Device discovery is off the caller thread and deliberately limited to one query per second. */
  private static final long DEVICE_CHECK_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1);
  private static final Result<Void> DEVICE_CLOSED = Result.failure("Audio mixer is closed or failed to initialize");
  private static final Result<Void> DEVICE_BUSY = Result.failure("Audio command queue is full; retry device selection");
  private static final Result<Void> DEVICE_UNSUPPORTED = Result.failure("Audio backend does not support preserving playback while switching devices");

  static {
    new OpenALEffects();
  }

  final List<OpenALAudioBuffer> buffers = new ArrayList<>();
  private final List<OpenALClip> trackingList = new LinkedList<>();
  private final List<OpenALStreamingClip> streamingTrackingList = new LinkedList<>();
  private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
  private final AtomicBoolean running = new AtomicBoolean(true);
  private final Thread audioThread;
  private final ExecutionMode executionMode;
  private final int[] sources;
  private final int[] availableSources;
  private final int[] effectSlots = new int[AUXILIARY_SEND_CAPACITY];
  private final int[] globalDirectFilters = new int[MIXER_EFFECT_SENDS];
  private final boolean[] clipEffectSlots = new boolean[AUXILIARY_SEND_CAPACITY];
  private final SfxEffect[] mixerEffects = {
      SfxEffect.NONE, SfxEffect.NONE
  };
  private volatile List<String> deviceNames = List.of();
  private volatile String deviceName = DEFAULT_DEVICE;
  private volatile boolean followingDefaultDevice = true;
  // Native device metadata below is owned exclusively by the audio thread.
  private long outputDevice;
  private long outputContext;
  private boolean consumingCommands;
  private boolean enumerateAll;
  private boolean enumerateDevices;
  private boolean reopenSupported;
  private boolean disconnectSupported;
  private int[] outputAttributes = {0};
  private long nextDeviceCheck;
  private int availableCount;
  private long lastCheckErrorMs;
  private boolean debug = false;
  private volatile boolean spatialAudioEnabled = true;
  private volatile RolloffMode rolloffMode = RolloffMode.DEFAULT;
  private volatile float listenerX;
  private volatile float listenerY;
  private volatile float listenerZ;
  /**
   * Creates a mixer using budget-based automatic thread ownership.
   *
   * <p>Bound mode initializes on the caller; dedicated mode initializes asynchronously.
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
    this(sourceCapacity, ExecutionMode.auto());
  }

  /**
   * Creates a mixer with explicit execution ownership or budget-based automatic selection.
   *
   * @param mode           the execution mode
   */
  public OpenALMixer(ExecutionMode mode) {
    this(DEFAULT_SOURCE_CAPACITY, mode);
  }

  /**
   * Bound mode initializes synchronously and must subsequently be polled/closed on this thread.
   *
   * @param sourceCapacity maximum simultaneous effect sources, greater than zero
   * @param mode           the execution mode
   */
  public OpenALMixer(int sourceCapacity, ExecutionMode mode) {
    if (sourceCapacity <= 0) {
      throw new IllegalArgumentException("Source capacity must be positive: " + sourceCapacity);
    }
    sources = new int[sourceCapacity];
    availableSources = new int[sourceCapacity];
    executionMode = mode;
    if (executionMode == ExecutionMode.BOUND_THREAD) {
      audioThread = Thread.currentThread();
      try {
        initializeDevice();
      } catch (RuntimeException | Error exception) {
        running.set(false);
        disposeDevice();
        throw exception;
      }
    } else {
      audioThread = new Thread(this::run, "OpenAL-Mixer");
      audioThread.setDaemon(true);
      audioThread.start();
    }
  }

  private static void clearAuxiliarySend(int source, int send) {
    if (AL.getCapabilities().ALC_EXT_EFX) {
      alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER,
          EXTEfx.AL_EFFECTSLOT_NULL, send, EXTEfx.AL_FILTER_NULL);
    }
  }

  /**
   * Returns the resolved mode.
   *
   * @return the resolved execution mode
   */
  public ExecutionMode executionMode() {
    return executionMode;
  }

  private void requireOwnerThread() {
    if (Thread.currentThread() != audioThread) {
      throw new IllegalStateException("Bound OpenAL mixer must be polled and closed on its creating thread");
    }
  }

  @Override
  public List<String> getDeviceNames() {
    return deviceNames;
  }

  @Override
  public String getDeviceName() {
    return deviceName;
  }

  @Override
  public boolean isFollowingDefaultDevice() {
    return followingDefaultDevice;
  }

  @Override
  public CompletableFuture<Result<Void>> setDevice(String name) {
    CompletableFuture<Result<Void>> result = new CompletableFuture<>();
    DeviceChange change = new DeviceChange(name, result);
    if (Thread.currentThread() == audioThread) {
      submit(change);
    } else {
      // Serialize admission with shutdown; never block the UI behind a full audio queue.
      synchronized (queue) {
        if (!running.get()) {
          result.complete(DEVICE_CLOSED);
        } else if (!queue.offer(change)) {
          result.complete(DEVICE_BUSY);
        }
      }
    }
    return result;
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
    if (!running.get()) {
      return;
    }
    if (executionMode == ExecutionMode.BOUND_THREAD) {
      requireOwnerThread();
      drainCommands();
      refreshDevices();
    }
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
      if (executionMode == ExecutionMode.BOUND_THREAD && !consumingCommands) {
        drainCommands();
      }
      if (outputDevice == 0) {
        return;
      }
      boolean previous = consumingCommands;
      consumingCommands = true;
      try {
        work.run();
      } finally {
        consumingCommands = previous;
      }
      return;
    }
    try {
      while (running.get()) {
        if (queue.offer(work, 100, TimeUnit.MILLISECONDS)) {
          return;
        }
      }
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
    if (!running.get()) {
      return;
    }
    if (executionMode == ExecutionMode.BOUND_THREAD) {
      requireOwnerThread();
    }
    synchronized (queue) {
      running.set(false);
    }
    if (executionMode == ExecutionMode.BOUND_THREAD) {
      disposeDevice();
      deviceNames = List.of();
      deviceName = DEFAULT_DEVICE;
    } else {
      audioThread.interrupt();
    }
  }

  /** Audio-thread-only switch. Native reopen retains the context and all of its playback objects. */
  private Result<Void> changeDevice(String name) {
    if (!running.get() || outputDevice == 0) {
      return DEVICE_CLOSED;
    }
    if (!reopenSupported) {
      return DEVICE_UNSUPPORTED;
    }
    // Clear old ALC errors so a failed request reports its own native failure.
    alcGetError(outputDevice);
    if (!SOFTReopenDevice.alcReopenDeviceSOFT(outputDevice, name.isEmpty() ? null : name, outputAttributes)) {
      int error = alcGetError(outputDevice);
      return Result.failure("Cannot switch audio output to " + (name.isEmpty() ? "system default" : name)
                            + " (ALC error 0x" + Integer.toHexString(error) + ')');
    }
    followingDefaultDevice = name.isEmpty();
    deviceName = currentDeviceName();
    return Result.OK;
  }

  /** Reads the native device's current endpoint name on its owning thread. */
  private String currentDeviceName() {
    String name = alcGetString(outputDevice, enumerateAll ? ALC11.ALC_ALL_DEVICES_SPECIFIER : ALC_DEVICE_SPECIFIER);
    return name == null ? DEFAULT_DEVICE : name;
  }

  /** Polling also runs while playback is idle; failed recovery is retried at the same bounded rate. */
  private void refreshDevices() {
    long now = System.nanoTime();
    if (now - nextDeviceCheck < 0) {
      return;
    }
    nextDeviceCheck = now + DEVICE_CHECK_INTERVAL_NANOS;
    if (enumerateDevices) {
      List<String> names = ALUtil.getStringList(0, enumerateAll ? ALC11.ALC_ALL_DEVICES_SPECIFIER : ALC_DEVICE_SPECIFIER);
      deviceNames = names == null ? List.of() : List.copyOf(names);
    }
    deviceName = currentDeviceName();
    if (!followingDefaultDevice || !reopenSupported) {
      return;
    }
    String systemDefault = alcGetString(0, enumerateAll ? ALC11.ALC_DEFAULT_ALL_DEVICES_SPECIFIER : ALC_DEFAULT_DEVICE_SPECIFIER);
    boolean disconnected = disconnectSupported && alcGetInteger(outputDevice, EXTDisconnect.ALC_CONNECTED) == ALC_FALSE;
    if (disconnected || systemDefault != null && !systemDefault.equals(deviceName)) {
      Result<Void> result = changeDevice(DEFAULT_DEVICE);
      if (!result.isOk() && debug) {
        LOGGER.warn("{}", result.reason());
      }
    }
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
   * @param slot   slot id
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
    try {
      runDevice();
    } finally {
      synchronized (queue) {
        running.set(false);
        Runnable pending;
        while ((pending = queue.poll()) != null) {
          if (pending instanceof DeviceChange change) {
            change.result.complete(DEVICE_CLOSED);
          }
        }
      }
      deviceNames = List.of();
      deviceName = DEFAULT_DEVICE;
    }
  }

  /** Initializes and disposes native audio objects only on their owning thread. */
  private void runDevice() {
    try {
      initializeDevice();
      while (running.get()) {
        try {
          Runnable first = queue.poll(1, TimeUnit.SECONDS);
          if (first != null) {
            consume(first);
          }
          drainCommands();
          refreshDevices();
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    } finally {
      disposeDevice();
    }
  }

  private void drainCommands() {
    Runnable pending;
    while ((pending = queue.poll()) != null) {
      consume(pending);
    }
  }

  private void initializeDevice() {
    /*
     * Initialize OpenAL.
     * This process should be done on this thread so that OpenAL context can bind to it.
     */
    long device = alcOpenDevice((ByteBuffer) null);
    if (device == 0L) {
      throw new IllegalStateException("OpenAL failed to open device");
    }
    outputDevice = device;

    boolean efxRequested = alcIsExtensionPresent(device, "ALC_EXT_EFX");
    IntBuffer attrs = memAllocInt(efxRequested ? 3 : 1);
    if (efxRequested) {
      attrs.put(EXTEfx.ALC_MAX_AUXILIARY_SENDS).put(AUXILIARY_SEND_CAPACITY);
    }
    attrs.put(0).flip();
    long context = ALC10.alcCreateContext(device, attrs);
    memFree(attrs);

    if (context == 0L) {
      throw new IllegalStateException("OpenAL failed to create context");
    }
    outputContext = context;

    alcMakeContextCurrent(context);
    ALCCapabilities capabilities = ALC.createCapabilities(device);
    AL.createCapabilities(capabilities);
    enumerateAll = capabilities.ALC_ENUMERATE_ALL_EXT;
    enumerateDevices = enumerateAll || capabilities.ALC_ENUMERATION_EXT;
    reopenSupported = capabilities.ALC_SOFT_reopen_device;
    disconnectSupported = capabilities.ALC_EXT_disconnect;
    outputAttributes = efxRequested ? new int[] {EXTEfx.ALC_MAX_AUXILIARY_SENDS, AUXILIARY_SEND_CAPACITY, 0} : new int[] {0};
    refreshDevices();
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
  }

  private void disposeDevice() {
    if (outputDevice == 0) {
      return;
    }
    if (outputContext == 0) {
      alcCloseDevice(outputDevice);
      outputDevice = 0;
      return;
    }

    // Clip owners queue cleanup before stopping the mixer; release streams
    // and native objects while their context still exists.
    drainCommands();
    for (int source : sources) {
      if (source == 0) {
        continue;
      }
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
    alcMakeContextCurrent(0);
    alcDestroyContext(outputContext);
    alcCloseDevice(outputDevice);
    outputContext = 0;
    outputDevice = 0;
  }

  private void consume(Runnable task) {
    boolean previous = consumingCommands;
    consumingCommands = true;
    try {
      task.run();
    } catch (Exception exception) {
      LOGGER.warn("OpenAL consumer fault", exception);
    } finally {
      consumingCommands = previous;
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

  /** Queued changes complete even if initialization fails or shutdown rejects pending work. */
  private final class DeviceChange implements Runnable {
    private final String name;
    private final CompletableFuture<Result<Void>> result;

    private DeviceChange(String name, CompletableFuture<Result<Void>> result) {
      this.name = name;
      this.result = result;
    }

    @Override
    public void run() {
      if (result.isDone()) {
        return;
      }
      try {
        result.complete(changeDevice(name));
      } catch (RuntimeException exception) {
        result.completeExceptionally(exception);
      }
    }
  }
}
