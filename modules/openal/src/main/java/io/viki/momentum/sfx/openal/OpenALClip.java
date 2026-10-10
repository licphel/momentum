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
import io.viki.momentum.sfx.ext.SfxEffect;
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
  private final OpenALClipState clipState;
  public volatile int state = AL_INITIAL;
  public volatile float offset = 0.0F;
  public volatile boolean shouldClose = false;
  public volatile int remainingLoops;
  public volatile boolean autoClosure;
  private @Nullable OpenALAudioBuffer buffer;
  private boolean ownsBuffer;
  private @Nullable AudioFormat format;
  private boolean open = false;
  private boolean closed;

  OpenALClip(OpenALMixer mixer) {
    this.mixer = mixer;
    clipState = new OpenALClipState(mixer, this);
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

  @SuppressWarnings("all")
  @Override
  public void poll() {
    int source = clipState.source();
    if (source == 0) {
      return;
    }
    clipState.poll();
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
      int source = clipState.source();
      if (source != 0) {
        alSourcePause(source);
        state = AL_PAUSED;
      }
    });
  }

  @Override
  public void resume() {
    if (!open || shouldClose) {
      throw new IllegalStateException("Clip is not available for resumed playback");
    }
    mixer.submit(() -> {
      int source = clipState.source();
      if (source == 0) {
        return;
      }
      clipState.poll();
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
      if (!clipState.shouldStart()) {
        state = AL_STOPPED;
        shouldClose = true;
        mixer.track(this);
        return;
      }
      if (count == 0) {
        releaseSource();
        state = AL_STOPPED;
        shouldClose = true;
        mixer.track(this);
        return;
      }
      if (clipState.source() == 0) {
        clipState.source(mixer.acquireSource());
      }
      if (clipState.source() == 0) {
        state = AL_STOPPED;
        shouldClose = true;
        mixer.track(this);
        return;
      }
      int source = clipState.source();
      alSourceStop(source);
      alSourcei(source, AL_BUFFER, buffer.id);
      alSourcei(source, AL_LOOPING, count == LOOP_CONTINUOUSLY ? AL_TRUE : AL_FALSE);
      alSourcef(source, AL_SEC_OFFSET, 0);
      offset = 0;
      clipState.resetAppliedState();
      clipState.poll();
      clipState.attachEffect();
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
    return clipState.volume();
  }

  @Override
  public void setVolume(FloatSupplier volume) {
    clipState.setVolume(volume);
  }

  @Override
  public float getPitch() {
    return clipState.pitch();
  }

  @Override
  public void setPitch(FloatSupplier value) {
    clipState.setPitch(value);
  }

  @Override
  public void setSpatialPosition(FloatSupplier x, FloatSupplier y, FloatSupplier z) {
    clipState.setSpatialPosition(x, y, z);
  }

  @Override
  public void setRolloffEnabled(boolean enabled) {
    clipState.setRolloffEnabled(enabled);
  }

  @Override
  public boolean isRolloffEnabled() {
    return clipState.isRolloffEnabled();
  }

  @Override
  public void setEffect(SfxEffect effect) {
    clipState.setEffect(effect);
  }

  @Override
  public float getPosition() {
    return offset;
  }

  @Override
  public void setPosition(float value) {
    offset = Math.max(value, 0.0F);
    mixer.submit(() -> {
      int source = clipState.source();
      if (clipState.source() != 0) {
        alSourcef(source, AL_SEC_OFFSET, offset);
      }
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
        if (ownsBuffer) {
          buffer.close();
        }
        buffer = null;
      }

      mixer.untrack(this);
    });
  }

  @Override
  public void __enableNativeAutoClosure() {
    autoClosure = true;
  }

  /**
   * Returns the current native source id for optional backend extensions.
   *
   * @return AL source id
   */
  public int source() {
    return clipState.sourceId();
  }

  /**
   * Returns the mixer that owns this clip.
   *
   * @return owning mixer
   */
  @InternalApi
  public OpenALMixer mixer() {
    return mixer;
  }

  void applySpatialPosition() {
    clipState.applySpatialPosition();
  }

  void applyMixerEffects() {
    clipState.applyMixerEffects();
  }

  private void checkUnopened() {
    if (closed) {
      throw new IllegalStateException("Clip is closed");
    }
    if (open) {
      throw new IllegalStateException("Clip already open");
    }
  }

  private void releaseSource() {
    int source = clipState.source();
    if (source == 0) {
      return;
    }
    clipState.detachEffect();
    mixer.releaseSource(source);
    clipState.source(0);
  }
}
