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

import io.viki.momentum.audio.AudioException;
import io.viki.momentum.audio.AudioFormat;
import io.viki.momentum.audio.Clip;
import io.viki.momentum.util.InternalApi;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;

import static org.lwjgl.openal.AL11.*;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * Implements an in-memory audio clip using one OpenAL source and buffer.
 *
 * <p>Playback commands are submitted to the owning mixer's audio thread, while the controller
 * values exposed to callers remain observable across that thread boundary. Finite looping is
 * coordinated by {@link OpenALMixer#pollEvents()} because OpenAL directly supports only continuous
 * looping.
 */
@InternalApi
public final class OpenALClip implements Clip {
  private final OpenALMixer mixer;
  volatile int source;
  volatile int state = AL_INITIAL;
  volatile float offset = 0.0F;
  volatile boolean shouldClose = false;
  volatile int remainingLoops;
  private int buffer = 0;
  private @Nullable AudioFormat format;
  private boolean open = false;
  private volatile float pitch = 1.0F;
  private volatile float volume = 1.0F;

  OpenALClip(OpenALMixer mixer) {
    this.mixer = mixer;

    mixer.submit(() -> {
      source = alGenSources();

      if (source == 0) {
        throw new AudioException("Failed to generate OpenAL source");
      }
    });
  }

  @Override
  public void open(AudioFormat format, byte[] data) {
    if (open) {
      throw new IllegalStateException("Clip already open");
    }
    open = true;

    this.format = format;

    mixer.submit(() -> {
      buffer = alGenBuffers();
      if (buffer == 0) {
        throw new AudioException("Failed to generate OpenAL buffer");
      }

      int alFormat = OpenALUtils.convertFormat(format);
      ByteBuffer buffer = memAlloc(data.length);
      try {
        buffer.put(data).flip();
        alBufferData(this.buffer, alFormat, buffer, format.sampleRate());
      } finally {
        memFree(buffer);
      }

      alSourcei(source, AL_BUFFER, this.buffer);
    });
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public void pause() {
    mixer.submit(() -> {
      alSourcePause(source);
    });
  }

  @Override
  public void stop() {
    mixer.submit(() -> {
      alSourceStop(source);
    });
  }

  @Override
  public void loop(int count) {
    if (count < 0) {
      throw new IllegalArgumentException("Loop count must be >= 0");
    }
    /*
     * For loops, we tend to handle manually.
     * OpenAL only supports infinite looping natively.
     */
    remainingLoops = count == LOOP_CONTINUOUSLY ? LOOP_CONTINUOUSLY : count - 1;

    mixer.submit(() -> {
      alSourcePlay(source);
      mixer.track(this); // track at last. This prevents the clip from being removed instantly.
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
    return volume;
  }

  @Override
  public void setVolume(float value) {
    mixer.submit(() -> alSourcef(source, AL_GAIN, volume = Math.max(value, 0.0F)));
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
    return offset;
  }

  @Override
  public void setPosition(float value) {
    mixer.submit(() -> alSourcef(source, AL_SEC_OFFSET, offset = Math.max(value, 0.0F)));
  }

  @Override
  public @Nullable AudioFormat format() {
    return format;
  }

  @Override
  public void close() {
    if (!open) {
      return;
    }
    open = false;

    mixer.submit(() -> {
      alSourceStop(source);
      alDeleteSources(source);

      if (buffer != 0) {
        alDeleteBuffers(buffer);
        buffer = 0;
      }
    });

    mixer.untrack(this);
  }
}
