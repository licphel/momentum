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

package net.fmhi.gfx.io;

/**
 * Describes decoded image data including pixel bytes.
 *
 * @param width    image width in pixels
 * @param height   image height in pixels
 * @param channels number of color channels (3 = RGB, 4 = RGBA)
 * @param pixels   raw pixel data in row-major order, length = {@code width * height * channels}
 */
public record ImageInfo(int width, int height, int channels, byte[] pixels) {
  /**
   * Creates an ImageInfo, validating all arguments.
   *
   * @param width    image width in pixels
   * @param height   image height in pixels
   * @param channels number of color channels (3 = RGB, 4 = RGBA)
   * @param pixels   raw pixel data in row-major order, length = {@code width * height * channels}
   */
  public ImageInfo {
    if (width < 0 || height < 0 || channels < 1) {
      throw new IllegalArgumentException("Invalid image dimensions: " + width + "x" + height + " channels=" + channels);
    }
    if (pixels.length != width * height * channels) {
      throw new IllegalArgumentException("Pixel array length " + pixels.length + " does not match " + width + "x" + height + "x" + channels);
    }
  }
}
