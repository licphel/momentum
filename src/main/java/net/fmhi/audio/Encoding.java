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

package net.fmhi.audio;

/**
 * Identifies the sample encoding used in a PCM audio stream.
 *
 * <p>Describes how each sample value is represented as bytes.
 *
 * @see AudioFormat
 */
public enum Encoding {
  /** Signed integer PCM. */
  PCM_SIGNED,

  /** Unsigned integer PCM. */
  PCM_UNSIGNED,

  /** IEEE 754 floating-point PCM. */
  PCM_FLOAT,

  /** ITU-T G.711 A-law companded PCM. */
  ALAW,

  /** ITU-T G.711 μ-law companded PCM. */
  ULAW,

  /** MPEG-1/2 Audio Layer III compressed audio. */
  MP3,

  /** Advanced Audio Coding compressed audio. */
  AAC,

  /** Ogg Vorbis compressed audio. */
  VORBIS,

  /** Opus compressed audio. */
  OPUS,

  /** Free Lossless Audio Codec compressed audio. */
  FLAC
}
