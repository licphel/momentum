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

package net.fmhi.audio.openal;

import net.fmhi.audio.AudioFormat;
import net.fmhi.util.InternalApi;

import static org.lwjgl.openal.AL10.*;

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
   * <p>Supports 8-bit and 16-bit mono and stereo PCM formats.
   *
   * @param format the audio format to convert
   * @return OpenAL format constant (e.g. {@code AL_FORMAT_MONO16})
   * @throws IllegalArgumentException if the channel count or sample size is not supported
   */
  static int convertFormat(AudioFormat format) {
    int channels = format.channels();
    int sampleSizeInBits = format.sampleSizeInBits();

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
