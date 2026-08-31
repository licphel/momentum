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

package net.momentum.audio.openal;

import net.momentum.audio.AudioException;
import net.momentum.audio.AudioFormat;
import net.momentum.audio.Clip;
import net.momentum.audio.Controller;
import net.momentum.util.internal.InternalApi;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;

import static org.lwjgl.openal.AL11.*;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * OpenAL-backed {@link Clip} implementation.
 *
 * <p>Each clip owns one OpenAL source and (after {@link #open}) one buffer.
 * All AL calls are submitted to {@link OpenALMixer}'s audio thread via {@link OpenALMixer#submit}; fields that are read
 * from the calling thread are declared {@code volatile} so that changes made on the audio thread are visible without
 * additional synchronization.
 *
 * <p>Loop management is handled manually: OpenAL natively supports only
 * infinite looping ({@code AL_LOOPING}), so finite repeat counts are tracked in {@link #remainingLoops} and driven by
 * {@link OpenALMixer#pollEvents()}.
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
  private float pitch = 1.0F;
  private float volume = 1.0F;

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

  @SuppressWarnings("unchecked")
  @Override
  public <T> T get(Controller ctr) {
    return switch (ctr) {
      case VOLUME -> (T) Float.valueOf(volume);
      case PITCH -> (T) Float.valueOf(pitch);
      case POSITION -> (T) Float.valueOf(offset);
    };
  }

  @Override
  public void set(Controller ctr, Object value) {
    float v;
    if (value instanceof Number num) {
      v = num.floatValue();
    } else {
      throw new IllegalArgumentException("Value must be a number");
    }

    mixer.submit(() -> {
      switch (ctr) {
        case VOLUME -> alSourcef(source, AL_GAIN, volume = Math.max(v, 0.0F));
        case PITCH -> alSourcef(source, AL_PITCH, pitch = Math.max(v, 0.0F));
        case POSITION -> alSourcef(source, AL_SEC_OFFSET, offset = Math.max(v, 0.0F));
      }
    });
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