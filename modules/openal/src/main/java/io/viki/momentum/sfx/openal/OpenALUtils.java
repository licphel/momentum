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

import io.viki.momentum.sfx.AudioEncoding;
import io.viki.momentum.sfx.AudioFormat;
import io.viki.momentum.util.InternalApi;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.EXTFloat32.AL_FORMAT_MONO_FLOAT32;
import static org.lwjgl.openal.EXTFloat32.AL_FORMAT_STEREO_FLOAT32;

/**
 * Static helper methods for OpenAL format mapping.
 *
 * <p>All methods are stateless and package-private.
 */
@InternalApi
public final class OpenALUtils {
  private OpenALUtils() {
  }

  /**
   * Converts an {@link AudioFormat} to the corresponding OpenAL format constant.
   *
   * <p>Supports 8-bit and 16-bit integer PCM, and 32-bit floating-point PCM, in mono and stereo.
   *
   * @param format the audio format to convert
   * @return OpenAL format constant (e.g. {@code AL_FORMAT_MONO16})
   * @throws IllegalArgumentException if the channel count or sample size is not supported
   */
  static int convertFormat(AudioFormat format) {
    int channels = format.channels();
    int sampleSizeInBits = format.sampleSizeInBits();

    if (format.encoding() == AudioEncoding.PCM_FLOAT) {
      if (sampleSizeInBits != Float.SIZE) {
        throw new IllegalArgumentException("Unsupported floating-point sample size: " + sampleSizeInBits);
      }
      return switch (channels) {
        case 1 -> AL_FORMAT_MONO_FLOAT32;
        case 2 -> AL_FORMAT_STEREO_FLOAT32;
        default -> throw new IllegalArgumentException("Unsupported channel count: " + channels);
      };
    }

    if (channels == 1) {
      return switch (sampleSizeInBits) {
        case 8 -> AL_FORMAT_MONO8;
        case 16 -> AL_FORMAT_MONO16;
        default -> throw new IllegalArgumentException("Unsupported sample size: " + sampleSizeInBits);
      };
    } else if (channels == 2) {
      return switch (sampleSizeInBits) {
        case 8 -> AL_FORMAT_STEREO8;
        case 16 -> AL_FORMAT_STEREO16;
        default -> throw new IllegalArgumentException("Unsupported sample size: " + sampleSizeInBits);
      };
    } else {
      throw new IllegalArgumentException("Unsupported channel count: " + channels);
    }
  }
}
