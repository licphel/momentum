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

package io.viki.momentum.sfx.openal.ext;

import io.viki.momentum.logging.Log;
import io.viki.momentum.logging.Logger;
import io.viki.momentum.sfx.Clip;
import io.viki.momentum.sfx.Mixer;
import io.viki.momentum.sfx.ext.Echo;
import io.viki.momentum.sfx.ext.SfxEffect;
import io.viki.momentum.sfx.openal.OpenALClip;
import io.viki.momentum.sfx.openal.OpenALMixer;
import io.viki.momentum.sfx.openal.OpenALStreamingClip;
import io.viki.momentum.util.InternalApi;
import org.jspecify.annotations.Nullable;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.EXTEfx;

import static org.lwjgl.openal.AL11.alSource3i;

@InternalApi
final class ExtEfxEcho implements SfxEffect.Binding {
  private static final Logger LOGGER = Log.getLogger();
  private static volatile boolean logged;

  private final Echo parameters;
  private int effect;
  private int localSend = -1;
  private int attachedSource;
  private @Nullable OpenALMixer attachedMixer;
  private int globalEffect;
  private int globalSlot;

  ExtEfxEcho(Echo parameters) {
    this.parameters = parameters;
  }

  @Override
  public void attach(Clip clip) {
    if (!supported()) {
      return;
    }
    OpenALMixer mixer = mixerOf(clip);
    int source = sourceOf(clip);
    if (mixer == null || source == 0) {
      return;
    }
    if (attachedSource == source && localSend >= 0) {
      return;
    }
    detachLocal();
    localSend = mixer.acquireClipEffectSlot();
    if (localSend < 0) {
      return;
    }
    effect = EXTEfx.alGenEffects();
    if (effect == 0) {
      mixer.releaseClipEffectSlot(localSend);
      localSend = -1;
      return;
    }
    configure(effect, mixer.effectSlotId(localSend));
    alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER,
        mixer.effectSlotId(localSend), localSend, EXTEfx.AL_FILTER_NULL);
    attachedSource = source;
    attachedMixer = mixer;
  }

  @Override
  public void detach(Clip clip) {
    detachLocal();
  }

  @Override
  public void attach(Mixer mixer, int slot) {
    if (!supported() || !(mixer instanceof OpenALMixer)) {
      return;
    }
    detachGlobal();
    globalEffect = EXTEfx.alGenEffects();
    if (globalEffect == 0) {
      return;
    }
    globalSlot = slot;
    configure(globalEffect, slot);
  }

  @Override
  public void detach(Mixer mixer, int slot) {
    detachGlobal();
  }

  private void configure(int effectId, int slotId) {
    EXTEfx.alEffecti(effectId, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_ECHO);
    EXTEfx.alEffectf(effectId, EXTEfx.AL_ECHO_DELAY,
        Math.clamp(parameters.delay(), EXTEfx.AL_ECHO_MIN_DELAY, EXTEfx.AL_ECHO_MAX_DELAY));
    EXTEfx.alEffectf(effectId, EXTEfx.AL_ECHO_LRDELAY,
        Math.clamp(parameters.leftRightDelay(), EXTEfx.AL_ECHO_MIN_LRDELAY,
            EXTEfx.AL_ECHO_MAX_LRDELAY));
    EXTEfx.alEffectf(effectId, EXTEfx.AL_ECHO_DAMPING,
        Math.clamp(parameters.damping(), EXTEfx.AL_ECHO_MIN_DAMPING,
            EXTEfx.AL_ECHO_MAX_DAMPING));
    EXTEfx.alEffectf(effectId, EXTEfx.AL_ECHO_FEEDBACK,
        Math.clamp(parameters.feedback(), EXTEfx.AL_ECHO_MIN_FEEDBACK,
            EXTEfx.AL_ECHO_MAX_FEEDBACK));
    EXTEfx.alEffectf(effectId, EXTEfx.AL_ECHO_SPREAD,
        Math.clamp(parameters.spread(), EXTEfx.AL_ECHO_MIN_SPREAD,
            EXTEfx.AL_ECHO_MAX_SPREAD));
    EXTEfx.alAuxiliaryEffectSloti(slotId, EXTEfx.AL_EFFECTSLOT_EFFECT, effectId);
    EXTEfx.alAuxiliaryEffectSlotf(slotId, EXTEfx.AL_EFFECTSLOT_GAIN,
        Math.clamp(parameters.sendGain(), 0F, 1F));
  }

  private void detachLocal() {
    OpenALMixer mixer = attachedMixer;
    if (attachedSource == 0 || mixer == null) {
      return;
    }
    alSource3i(attachedSource, EXTEfx.AL_AUXILIARY_SEND_FILTER,
        EXTEfx.AL_EFFECTSLOT_NULL, localSend, EXTEfx.AL_FILTER_NULL);
    if (effect != 0) {
      EXTEfx.alDeleteEffects(effect);
      effect = 0;
    }
    mixer.clearEffectSlot(localSend);
    mixer.releaseClipEffectSlot(localSend);
    localSend = -1;
    attachedSource = 0;
    attachedMixer = null;
  }

  private void detachGlobal() {
    if (globalEffect == 0) {
      return;
    }
    EXTEfx.alAuxiliaryEffectSloti(globalSlot, EXTEfx.AL_EFFECTSLOT_EFFECT,
        EXTEfx.AL_EFFECT_NULL);
    EXTEfx.alDeleteEffects(globalEffect);
    globalEffect = 0;
    globalSlot = 0;
  }

  private static boolean supported() {
    if (AL.getCapabilities().ALC_EXT_EFX) {
      return true;
    }
    if (!logged) {
      logged = true;
      LOGGER.warn("OpenAL Ext Efx is not supported. `echo` skipped!");
    }
    return false;
  }

  private static int sourceOf(Clip clip) {
    if (clip instanceof OpenALClip openALClip) {
      return openALClip.source();
    }
    if (clip instanceof OpenALStreamingClip streamingClip) {
      return streamingClip.source();
    }
    return 0;
  }

  private static @Nullable OpenALMixer mixerOf(Clip clip) {
    if (clip instanceof OpenALClip openALClip) {
      return openALClip.mixer();
    }
    if (clip instanceof OpenALStreamingClip streamingClip) {
      return streamingClip.mixer();
    }
    return null;
  }
}
