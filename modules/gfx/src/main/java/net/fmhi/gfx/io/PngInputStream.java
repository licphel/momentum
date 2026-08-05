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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Decodes a PNG image into raw RGBA pixel data.
 *
 * <p>Uses {@link ImageIO} internally. The decoded data is cached in memory
 * as a byte array; pixel data is read back via {@link #read()}.
 */
public final class PngInputStream extends ImageInputStream {
  private final ImageInfo info;
  private final byte[] data;
  private int pos;

  /**
   * Creates a PngInputStream from a {@link InputStream}.
   *
   * @param source wrapped input stream
   * @throws IOException if header parsing fails or source stream reading fails
   */
  public PngInputStream(InputStream source) throws IOException {
    BufferedImage img = ImageIO.read(source);
    if (img == null) {
      throw new IOException("Failed to decode PNG");
    }
    int w = img.getWidth();
    int h = img.getHeight();

    // Convert to RGBA
    BufferedImage rgba = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    rgba.getGraphics().drawImage(img, 0, 0, null);

    data = new byte[w * h * 4];
    int[] pixels = rgba.getRGB(0, 0, w, h, null, 0, w);
    for (int i = 0; i < pixels.length; i++) {
      int argb = pixels[i];
      data[i * 4] = (byte) ((argb >> 16) & 0xFF);
      data[i * 4 + 1] = (byte) ((argb >> 8) & 0xFF);
      data[i * 4 + 2] = (byte) (argb & 0xFF);
      data[i * 4 + 3] = (byte) ((argb >> 24) & 0xFF);
    }
    info = new ImageInfo(w, h, 4, data);
  }

  @Override
  public ImageInfo info() {
    return info;
  }

  /**
   * Returns the entire pixel data as a byte array (RGBA, row-major).
   *
   * @return pixel data
   */
  public byte[] readAllBytes() {
    return data;
  }

  @Override
  public int read() {
    if (pos >= data.length) {
      return -1;
    }
    return data[pos++] & 0xFF;
  }

  @Override
  public int read(byte[] b, int off, int len) {
    if (pos >= data.length) {
      return -1;
    }
    int available = Math.min(len, data.length - pos);
    System.arraycopy(data, pos, b, off, available);
    pos += available;
    return available;
  }
}
