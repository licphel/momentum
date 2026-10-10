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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
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

import io.viki.momentum.sfx.Clip;
import io.viki.momentum.sfx.ext.SfxEffect;
import io.viki.momentum.util.FloatSupplier;
import io.viki.momentum.util.InternalApi;

import static org.lwjgl.openal.AL11.AL_FALSE;
import static org.lwjgl.openal.AL11.AL_POSITION;
import static org.lwjgl.openal.AL11.AL_ROLLOFF_FACTOR;
import static org.lwjgl.openal.AL11.AL_SOURCE_RELATIVE;
import static org.lwjgl.openal.AL11.AL_TRUE;
import static org.lwjgl.openal.AL11.AL_VELOCITY;
import static org.lwjgl.openal.AL11.AL_GAIN;
import static org.lwjgl.openal.AL11.AL_PITCH;
import static org.lwjgl.openal.AL11.alSource3f;
import static org.lwjgl.openal.AL11.alSourcef;
import static org.lwjgl.openal.AL11.alSourcei;

/**
 * Common mutable state for OpenAL's buffered and streaming clips.
 *
 * <p>All OpenAL calls made by this class run on the owning mixer thread. The
 * scalar controls are volatile because clip handles can be configured by the
 * game thread before the queued backend command is consumed.
 */
@InternalApi
public final class OpenALClipState {
  private static final float CONTROL_EPSILON = 1.0E-4F;
  private static final FloatSupplier ONE = FloatSupplier.constant(1F);
  private static final FloatSupplier ZERO = FloatSupplier.constant(0F);
  private final OpenALMixer mixer;
  private final Clip owner;
  private volatile int source;
  private volatile FloatSupplier pitchSource = ONE;
  private volatile FloatSupplier volumeSource = ONE;
  private volatile FloatSupplier spatialXSource = ZERO;
  private volatile FloatSupplier spatialYSource = ZERO;
  private volatile FloatSupplier spatialZSource = ZERO;
  private float appliedVolume;
  private float appliedPitch;
  private float appliedSpatialX;
  private float appliedSpatialY;
  private float appliedSpatialZ;
  private boolean volumeApplied;
  private boolean pitchApplied;
  private boolean spatialPositionApplied;
  private volatile SfxEffect effect = SfxEffect.NONE;
  private volatile boolean spatial;
  private volatile boolean rolloffEnabled;
  private volatile float spatialX;
  private volatile float spatialY;
  private volatile float spatialZ;

  OpenALClipState(OpenALMixer mixer, Clip owner) {
    this.mixer = mixer;
    this.owner = owner;
  }

  int source() {
    return source;
  }

  void source(int source) {
    this.source = source;
    resetAppliedState();
  }

  float pitch() {
    return Math.max(pitchSource.getAsFloat(), 1E-5F);
  }

  void setPitch(FloatSupplier value) {
    pitchSource = value;
  }

  float volume() {
    return Math.max(volumeSource.getAsFloat(), 0F);
  }

  void setVolume(FloatSupplier value) {
    volumeSource = value;
  }

  void poll() {
    if (source == 0) {
      return;
    }
    refreshPosition();
    float nextPitch = pitch();
    if (!pitchApplied || Math.abs(nextPitch - appliedPitch) > CONTROL_EPSILON) {
      alSourcef(source, AL_PITCH, nextPitch);
      appliedPitch = nextPitch;
      pitchApplied = true;
    }
    if (!spatialPositionApplied || spatial && (
        Math.abs(spatialX - appliedSpatialX) > CONTROL_EPSILON
        || Math.abs(spatialY - appliedSpatialY) > CONTROL_EPSILON
        || Math.abs(spatialZ - appliedSpatialZ) > CONTROL_EPSILON)) {
      applySampledPosition();
      appliedSpatialX = spatialX;
      appliedSpatialY = spatialY;
      appliedSpatialZ = spatialZ;
      spatialPositionApplied = true;
    }
    float effective = volume();
    if (rolloffEnabled && spatial) {
      effective *= mixer.getRolloffGain(spatialX, spatialY, spatialZ);
    }
    if (!volumeApplied || Math.abs(effective - appliedVolume) > CONTROL_EPSILON) {
      alSourcef(source, AL_GAIN, effective);
      appliedVolume = effective;
      volumeApplied = true;
    }
  }

  void resetAppliedState() {
    volumeApplied = false;
    pitchApplied = false;
    spatialPositionApplied = false;
  }

  void setSpatialPosition(FloatSupplier x, FloatSupplier y, FloatSupplier z) {
    spatialXSource = x;
    spatialYSource = y;
    spatialZSource = z;
    spatial = true;
  }

  private void refreshPosition() {
    if (!spatial) {
      return;
    }
    spatialX = spatialXSource.getAsFloat();
    spatialY = spatialYSource.getAsFloat();
    spatialZ = spatialZSource.getAsFloat();
  }

  void setRolloffEnabled(boolean enabled) {
    rolloffEnabled = enabled;
  }

  boolean isRolloffEnabled() {
    return rolloffEnabled;
  }

  boolean shouldStart() {
    refreshPosition();
    return !rolloffEnabled || !spatial
        || mixer.getRolloffGain(spatialX, spatialY, spatialZ)
        > mixer.getRolloffMode().cutoffGain();
  }

  void applySpatialPosition() {
    if (source == 0) {
      return;
    }
    refreshPosition();
    applySampledPosition();
  }

  private void applySampledPosition() {
    boolean positional = spatial && mixer.isSpatialAudioEnabled();
    if (!positional) {
      // Relative origin is the listener, so this is centred regardless of
      // the listener's world position or orientation.
      alSourcei(source, AL_SOURCE_RELATIVE, AL_TRUE);
      alSourcef(source, AL_ROLLOFF_FACTOR, 0F);
      alSource3f(source, AL_POSITION, 0F, 0F, 0F);
      alSource3f(source, AL_VELOCITY, 0F, 0F, 0F);
      return;
    }
    alSourcei(source, AL_SOURCE_RELATIVE, AL_FALSE);
    alSourcef(source, AL_ROLLOFF_FACTOR, 0F);
    alSource3f(source, AL_POSITION, spatialX, spatialY, spatialZ);
    alSource3f(source, AL_VELOCITY, 0F, 0F, 0F);
  }

  void setEffect(SfxEffect value) {
    SfxEffect previous = effect;
    if (previous == value) {
      return;
    }
    effect = value;
    mixer.submit(() -> {
      previous.detach(owner);
      if (source != 0) {
        value.attach(owner);
        mixer.applyMixerEffects(source);
      }
    });
  }

  void attachEffect() {
    if (source != 0) {
      mixer.attachGlobalEffects(source);
    }
    effect.attach(owner);
    if (source != 0) {
      mixer.applyMixerEffects(source);
    }
  }

  void detachEffect() {
    effect.detach(owner);
    if (source != 0) {
      mixer.detachGlobalEffects(source);
    }
  }

  void applyMixerEffects() {
    if (source != 0) {
      mixer.clearDirectFilter(source);
      effect.detach(owner);
      effect.attach(owner);
      mixer.applyMixerEffects(source);
    }
  }

  /**
   * Returns the PCM format-independent source id used by backend extensions.
   *
   * @return AL source id
   */
  public int sourceId() {
    return source;
  }
}
