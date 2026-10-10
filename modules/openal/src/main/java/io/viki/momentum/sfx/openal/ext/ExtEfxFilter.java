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

package io.viki.momentum.sfx.openal.ext;

import io.viki.momentum.logging.Log;
import io.viki.momentum.logging.Logger;
import io.viki.momentum.sfx.Clip;
import io.viki.momentum.sfx.Mixer;
import io.viki.momentum.sfx.ext.FilterMode;
import io.viki.momentum.sfx.ext.Filter;
import io.viki.momentum.sfx.ext.FilterPoint;
import io.viki.momentum.sfx.ext.SfxEffect;
import io.viki.momentum.sfx.openal.OpenALClip;
import io.viki.momentum.sfx.openal.OpenALMixer;
import io.viki.momentum.sfx.openal.OpenALStreamingClip;
import io.viki.momentum.util.InternalApi;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.EXTEfx;

import java.util.Arrays;

import static org.lwjgl.openal.AL10.alSourcei;

@InternalApi
final class ExtEfxFilter implements SfxEffect.Binding {
  private static final Logger LOGGER = Log.getLogger();

  private static volatile boolean logged = false;
  private final FilterMode mode;
  private final float gain;
  private final float lowFrequencyGain;
  private final float highFrequencyGain;
  private int filter;
  private int attachedSource;
  private int globalFilter;

  ExtEfxFilter(Filter parameters) {
    mode = parameters.mode();
    gain = clamp(parameters.gain());
    FilterPoint[] points = parameters.points().toArray(FilterPoint[]::new);
    if (points.length == 0) {
      lowFrequencyGain = clamp(parameters.gainLowFrequency());
      highFrequencyGain = clamp(parameters.gainHighFrequency());
      return;
    }
    float value = Math.clamp(parameters.strength(), 0F, 1F);
    Arrays.sort(points, (first, second) -> Float.compare(first.position(), second.position()));
    lowFrequencyGain = gainAt(points, value, false);
    highFrequencyGain = gainAt(points, value, true);
  }

  private static float gainAt(FilterPoint[] points, float position, boolean high) {
    if (points.length == 0) {
      return 1F;
    }
    if (position <= points[0].position()) {
      return gain(points[0], high);
    }
    for (int index = 1; index < points.length; index++) {
      FilterPoint previous = points[index - 1];
      FilterPoint current = points[index];
      if (position <= current.position()) {
        float span = current.position() - previous.position();
        if (span <= 0F) {
          return gain(current, high);
        }
        float ratio = (position - previous.position()) / span;
        return gain(previous, high) + (gain(current, high) - gain(previous, high)) * ratio;
      }
    }
    return gain(points[points.length - 1], high);
  }

  private static float gain(FilterPoint point, boolean high) {
    return clamp(high ? point.highGain() : point.lowGain());
  }

  private static float clamp(float value) {
    return Math.clamp(value, 0F, 1F);
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

  @Override
  public void attach(Clip clip) {
    if (!AL.getCapabilities().ALC_EXT_EFX) {
      if (!logged) {
        logged = true;
        LOGGER.warn("OpenAL Ext Efx is not supported. `filter` skipped!");
      }
      return;
    }
    int source = sourceOf(clip);
    if (source == 0) {
      return;
    }
    if (attachedSource == source && filter != 0) {
      return;
    }
    if (attachedSource != 0) {
      detachSource(attachedSource);
    }
    filter = createFilter();
    if (filter == 0) {
      return;
    }
    alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filter);
    attachedSource = source;
  }

  private int createFilter() {
    int filter = EXTEfx.alGenFilters();
    if (filter == 0) {
      return 0;
    }
    switch (mode) {
      case LOWPASS -> {
        EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
        EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, gain);
        EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, highFrequencyGain);
      }
      case HIGHPASS -> {
        EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_HIGHPASS);
        EXTEfx.alFilterf(filter, EXTEfx.AL_HIGHPASS_GAIN, gain);
        EXTEfx.alFilterf(filter, EXTEfx.AL_HIGHPASS_GAINLF, lowFrequencyGain);
      }
      case BANDPASS -> {
        EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_BANDPASS);
        EXTEfx.alFilterf(filter, EXTEfx.AL_BANDPASS_GAIN, gain);
        EXTEfx.alFilterf(filter, EXTEfx.AL_BANDPASS_GAINLF, lowFrequencyGain);
        EXTEfx.alFilterf(filter, EXTEfx.AL_BANDPASS_GAINHF, highFrequencyGain);
      }
    }
    return filter;
  }

  @Override
  public void attach(Mixer mixer, int slot) {
    if (mixer instanceof OpenALMixer openALMixer && AL.getCapabilities().ALC_EXT_EFX) {
      globalFilter = createFilter();
      openALMixer.setGlobalDirectFilter(slot, globalFilter);
    }
  }

  @Override
  public void detach(Mixer mixer, int slot) {
    if (mixer instanceof OpenALMixer openALMixer && globalFilter != 0) {
      openALMixer.setGlobalDirectFilter(slot, 0);
      EXTEfx.alDeleteFilters(globalFilter);
      globalFilter = 0;
    }
  }

  @Override
  public void detach(Clip clip) {
    if (attachedSource == 0) {
      return;
    }
    detachSource(attachedSource);
    attachedSource = 0;
  }

  private void detachSource(int source) {
    alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
    EXTEfx.alDeleteFilters(filter);
    filter = 0;
  }
}
