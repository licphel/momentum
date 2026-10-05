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

import io.viki.momentum.sfx.AudioException;
import io.viki.momentum.sfx.AudioFormat;
import io.viki.momentum.sfx.StreamingClip;
import io.viki.momentum.util.FloatSupplier;
import io.viki.momentum.sfx.io.AudioInputStream;
import io.viki.momentum.util.InternalApi;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

import static org.lwjgl.openal.AL11.*;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * OpenAL streaming clip whose native resources are confined to the owning mixer's audio thread.
 */
@InternalApi
public final class OpenALStreamingClip implements StreamingClip {
  private static final int BUFFER_COUNT = 4;
  private static final int BUFFER_BYTES = 64 * 1024;

  private final OpenALMixer mixer;
  private final int[] buffers = new int[BUFFER_COUNT];
  private final int[] bufferSizes = new int[BUFFER_COUNT];
  private final byte[][] stagingBuffers = new byte[BUFFER_COUNT][];
  private volatile int source;
  private volatile int state = AL_INITIAL;
  private volatile float position;
  private volatile float pitch = 1.0F;
  private volatile float volume = 1.0F;
  private volatile @Nullable FloatSupplier volumeSource;
  private float appliedVolume = -1;
  private volatile boolean open;
  private volatile boolean shouldClose;
  private @Nullable AudioFormat format;
  private @Nullable AudioInputStream stream;
  private @Nullable Supplier<? extends AudioInputStream> streamSupplier;
  private boolean endOfStream;
  private volatile boolean started;
  private boolean terminal;
  private boolean closed;
  private int remainingLoops;
  private long processedBytes;

  OpenALStreamingClip(OpenALMixer mixer) {
    this.mixer = mixer;
    mixer.submit(() -> {
      source = alGenSources();
      if (source == 0) {
        throw new AudioException("Failed to generate OpenAL streaming source");
      }
    });
  }

  @Override
  public void open(Supplier<? extends AudioInputStream> streamSupplier) {
    if (closed) throw new IllegalStateException("Streaming clip is closed");
    if (open) {
      throw new IllegalStateException("Streaming clip already open");
    }

    AudioInputStream stream = streamSupplier.get();
    this.stream = stream;
    AudioFormat streamFormat = stream.format();
    OpenALUtils.convertFormat(streamFormat);
    format = streamFormat;
    this.streamSupplier = streamSupplier;
    open = true;

    mixer.submit(() -> {
      int capacity = BUFFER_BYTES - BUFFER_BYTES % streamFormat.frameSize();
      for (int i = 0; i < BUFFER_COUNT; i++) {
        buffers[i] = alGenBuffers();
        if (buffers[i] == 0) {
          fail(new AudioException("Failed to generate OpenAL streaming buffer " + i));
          return;
        }
        stagingBuffers[i] = new byte[capacity];
        if (!fill(i)) {
          break;
        }
      }
    });
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public void play() {
    if (started) {
      if (terminal) {
        throw new IllegalStateException("Streaming clip has already been consumed");
      }
      mixer.submit(() -> alSourcePlay(source));
      return;
    }
    StreamingClip.super.play();
  }

  @Override
  public void loop(int count) {
    if (!open) {
      throw new IllegalStateException("Streaming clip is not open");
    }
    if (count < 0) {
      throw new IllegalArgumentException("Loop count must be >= 0");
    }
    if (terminal) {
      throw new IllegalStateException("Streaming clip has already been consumed");
    }
    remainingLoops = count == LOOP_CONTINUOUSLY ? LOOP_CONTINUOUSLY : count - 1;
    mixer.submit(() -> {
      started = true;
      applyVolume();
      alSourcePlay(source);
      mixer.track(this);
    });
  }

  @Override
  public void pause() {
    mixer.submit(() -> alSourcePause(source));
  }

  @Override
  public void resume() {
    if (!open || terminal) throw new IllegalStateException("Streaming clip cannot resume after completion");
    mixer.submit(() -> {
      started = true;
      applyVolume();
      alSourcePlay(source);
      mixer.track(this);
    });
  }

  @Override
  public void stop() {
    mixer.submit(() -> {
      alSourceStop(source);
      endOfStream = true;
      terminal = true;
      shouldClose = true;
      closeStream();
    });
  }

  @Override
  public boolean isPlaying() {
    return state == AL_PLAYING;
  }

  @Override
  public boolean shouldClose() {
    return shouldClose;
  }

  @Override
  public float getVolume() {
    FloatSupplier source = volumeSource;
    return source == null ? volume : source.getAsFloat();
  }

  @Override
  public void setVolume(float value) {
    volumeSource = null;
    volume = Math.max(value, 0.0F);
    mixer.submit(this::applyVolume);
  }

  @Override
  public void setVolume(FloatSupplier volume) {
    volumeSource = volume;
    mixer.submit(this::applyVolume);
  }

  private void applyVolume() {
    float effective = Math.max(0, getVolume());
    if (appliedVolume != effective) {
      alSourcef(source, AL_GAIN, effective);
      appliedVolume = effective;
    }
  }

  @Override
  public float getPitch() {
    return pitch;
  }

  @Override
  public void setPitch(float value) {
    mixer.submit(() -> alSourcef(source, AL_PITCH, pitch = Math.max(value, 1E-5F)));
  }

  @Override
  public float getPosition() {
    return position;
  }

  @Override
  public @Nullable AudioFormat format() {
    return format;
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    open = false;
    mixer.untrack(this);
    mixer.submit(() -> {
      alSourceStop(source);
      closeStream();
      alDeleteSources(source);
      for (int i = 0; i < BUFFER_COUNT; i++) {
        if (buffers[i] != 0) {
          alDeleteBuffers(buffers[i]);
          buffers[i] = 0;
        }
      }
    });
  }

  void poll() {
    applyVolume();
    int processed = alGetSourcei(source, AL_BUFFERS_PROCESSED);
    for (int i = 0; i < processed; i++) {
      int processedBuffer = alSourceUnqueueBuffers(source);
      int index = indexOf(processedBuffer);
      if (index < 0) {
        fail(new AudioException("OpenAL returned unknown streaming buffer " + processedBuffer));
        return;
      }
      processedBytes += bufferSizes[index];
      bufferSizes[index] = 0;
      if (!endOfStream) {
        fill(index);
      }
    }

    state = alGetSourcei(source, AL_SOURCE_STATE);
    AudioFormat currentFormat = format;
    if (currentFormat != null) {
      position = (float) (processedBytes / (double) currentFormat.getByteRate()
          + alGetSourcef(source, AL_SEC_OFFSET));
    }

    int queued = alGetSourcei(source, AL_BUFFERS_QUEUED);
    if (endOfStream && queued == 0) {
      if (remainingLoops > 0) {
        restartStream();
      } else {
        terminal = true;
        shouldClose = true;
        closeStream();
      }
    } else if (started && state != AL_PLAYING && state != AL_PAUSED && queued > 0) {
      alSourcePlay(source);
      state = AL_PLAYING;
    }
  }

  private boolean fill(int index) {
    AudioInputStream currentStream = stream;
    AudioFormat currentFormat = format;
    if (currentStream == null || currentFormat == null) {
      return false;
    }

    byte[] data = stagingBuffers[index];
    int length = 0;
    try {
      while (length < data.length) {
        int read = currentStream.read(data, length, data.length - length);
        if (read < 0) {
          endOfStream = true;
          closeStream();
          break;
        }
        if (read == 0) {
          continue;
        }
        length += read;
      }
    } catch (IOException e) {
      fail(new AudioException("Failed to read streaming audio", e));
      return false;
    }

    if (length % currentFormat.frameSize() != 0) {
      fail(new AudioException("Streaming audio ended with an incomplete PCM frame"));
      return false;
    }
    if (length == 0) {
      return false;
    }

    ByteBuffer nativeData = memAlloc(length);
    try {
      nativeData.put(data, 0, length).flip();
      alBufferData(buffers[index], OpenALUtils.convertFormat(currentFormat), nativeData, currentFormat.sampleRate());
      alSourceQueueBuffers(source, buffers[index]);
      bufferSizes[index] = length;
      return true;
    } finally {
      memFree(nativeData);
    }
  }

  private int indexOf(int buffer) {
    for (int i = 0; i < BUFFER_COUNT; i++) {
      if (buffers[i] == buffer) {
        return i;
      }
    }
    return -1;
  }

  private void restartStream() {
    Supplier<? extends AudioInputStream> supplier = streamSupplier;
    AudioFormat currentFormat = format;
    if (supplier == null || currentFormat == null) {
      fail(new AudioException("Streaming clip has no input supplier"));
      return;
    }

    AudioInputStream nextStream;
    try {
      nextStream = supplier.get();
    } catch (RuntimeException e) {
      fail(new AudioException("Failed to open the next streaming audio repetition", e));
      return;
    }
    AudioFormat nextFormat = nextStream.format();
    if (!currentFormat.equals(nextFormat)) {
      try {
        nextStream.close();
      } catch (IOException ignored) {
        // The incompatible stream cannot be used and is already terminal.
      }
      fail(new AudioException("Streaming loop format changed from " + currentFormat + " to " + nextFormat));
      return;
    }

    stream = nextStream;
    endOfStream = false;
    processedBytes = 0L;
    position = 0.0F;
    if (remainingLoops != LOOP_CONTINUOUSLY) {
      remainingLoops--;
    }
    for (int i = 0; i < BUFFER_COUNT; i++) {
      if (!fill(i)) {
        break;
      }
    }
    if (alGetSourcei(source, AL_BUFFERS_QUEUED) > 0) {
      alSourcePlay(source);
      state = AL_PLAYING;
    }
  }

  private void fail(AudioException exception) {
    endOfStream = true;
    terminal = true;
    shouldClose = true;
    closeStream();
    throw exception;
  }

  private void closeStream() {
    AudioInputStream currentStream = stream;
    stream = null;
    if (currentStream == null) {
      return;
    }
    try {
      currentStream.close();
    } catch (IOException ignored) {
      // The playback resource is already terminal; no recovery is possible here.
    }
  }
}
