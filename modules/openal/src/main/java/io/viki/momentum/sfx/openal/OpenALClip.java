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

import io.viki.momentum.sfx.AudioBuffer;
import io.viki.momentum.sfx.AudioFormat;
import io.viki.momentum.sfx.Clip;
import io.viki.momentum.util.FloatSupplier;
import io.viki.momentum.util.InternalApi;
import org.jspecify.annotations.Nullable;

import static org.lwjgl.openal.AL11.*;

/**
 * Borrows shared samples and acquires a pooled source only while playing or paused.
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
  volatile boolean autoClosure;
  private @Nullable OpenALAudioBuffer buffer;
  private boolean ownsBuffer;
  private @Nullable AudioFormat format;
  private boolean open = false;
  private boolean closed;
  private volatile float pitch = 1.0F;
  private volatile float volume = 1.0F;
  private volatile @Nullable FloatSupplier volumeSource;
  private float appliedVolume = -1;

  OpenALClip(OpenALMixer mixer) {
    this.mixer = mixer;
  }

  @Override
  public void open(AudioFormat format, byte[] data) {
    checkUnopened();
    AudioBuffer uploaded = mixer.createBuffer(format, data);
    open(uploaded);
    ownsBuffer = true;
  }

  @Override
  public void open(AudioBuffer samples) {
    checkUnopened();
    if (!(samples instanceof OpenALAudioBuffer uploaded) || uploaded.mixer != mixer) {
      throw new IllegalArgumentException("Audio buffer belongs to a different mixer");
    }
    uploaded.borrow();
    buffer = uploaded;
    format = uploaded.format();
    open = true;
  }

  private void checkUnopened() {
    if (closed) throw new IllegalStateException("Clip is closed");
    if (open) throw new IllegalStateException("Clip already open");
  }

  @Override
  public void poll() {
    if (source == 0) return;
    applyVolume();
    state = alGetSourcei(source, AL_SOURCE_STATE);
    offset = alGetSourcef(source, AL_SEC_OFFSET);

    if (state == AL_STOPPED) {
      if (remainingLoops <= 0) {
        shouldClose = true;
        releaseSource();
      } else {
        if (remainingLoops != LOOP_CONTINUOUSLY) {
          remainingLoops--;
        }
        alSourcePlay(source);
      }
    }
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public void pause() {
    mixer.submit(() -> {
      if (source != 0) {
        alSourcePause(source);
        state = AL_PAUSED;
      }
    });
  }

  @Override
  public void resume() {
    if (!open || shouldClose) throw new IllegalStateException("Clip is not available for resumed playback");
    mixer.submit(() -> {
      if (source == 0) return;
      applyVolume();
      alSourcePlay(source);
      state = AL_PLAYING;
      mixer.track(this);
    });
  }

  @Override
  public void stop() {
    mixer.submit(() -> {
      remainingLoops = 0;
      releaseSource();
      offset = 0;
      state = AL_STOPPED;
      shouldClose = true;
    });
  }

  @Override
  public void loop(int count) {
    if (!open) {
      throw new IllegalStateException("Clip is not open");
    }
    if (count < 0) {
      throw new IllegalArgumentException("Loop count must be >= 0");
    }
    /*
     * For loops, we tend to handle manually.
     * OpenAL only supports infinite looping natively.
     */
    shouldClose = false;

    mixer.submit(() -> {
      if (buffer == null) {
        throw new IllegalStateException("Audio buffer is null");
      }
      remainingLoops = count == LOOP_CONTINUOUSLY ? LOOP_CONTINUOUSLY : count - 1;
      if (count == 0) {
        releaseSource();
        state = AL_STOPPED;
        shouldClose = true;
        mixer.track(this);
        return;
      }
      if (source == 0) source = mixer.acquireSource();
      if (source == 0) {
        state = AL_STOPPED;
        shouldClose = true;
        mixer.track(this);
        return;
      }
      alSourceStop(source);
      alSourcei(source, AL_BUFFER, buffer.id);
      alSourcei(source, AL_LOOPING, count == LOOP_CONTINUOUSLY ? AL_TRUE : AL_FALSE);
      alSourcei(source, AL_SOURCE_RELATIVE, AL_TRUE);
      alSourcef(source, AL_ROLLOFF_FACTOR, 0);
      alSource3f(source, AL_POSITION, 0, 0, 0);
      alSource3f(source, AL_VELOCITY, 0, 0, 0);
      alSourcef(source, AL_PITCH, Math.max(pitch, 1E-5F));
      alSourcef(source, AL_SEC_OFFSET, 0);
      offset = 0;
      appliedVolume = -1;
      applyVolume();
      alSourcePlay(source);
      state = AL_PLAYING;
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

  void applyVolume() {
    if (source == 0) return;
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
    pitch = Math.max(value, 1E-5F);
    mixer.submit(() -> {
      if (source != 0) alSourcef(source, AL_PITCH, Math.max(pitch, 1E-5F));
    });
  }

  @Override
  public float getPosition() {
    return offset;
  }

  @Override
  public void setPosition(float value) {
    offset = Math.max(value, 0.0F);
    mixer.submit(() -> {
      if (source != 0) alSourcef(source, AL_SEC_OFFSET, offset);
    });
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

    mixer.submit(() -> {
      releaseSource();
      state = AL_STOPPED;
      shouldClose = true;
      if (buffer != null) {
        buffer.release();
        if (ownsBuffer) buffer.close();
        buffer = null;
      }

      mixer.untrack(this);
    });
  }

  private void releaseSource() {
    if (source == 0) return;
    mixer.releaseSource(source);
    source = 0;
  }

  @Override
  public void __enableNativeAutoClosure() {
    autoClosure = true;
  }
}
