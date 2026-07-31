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

package net.fmhi.gfx.texture;

import org.jspecify.annotations.Nullable;

/**
 * Creates a new {@code TextureDesc} describing a GPU texture.
 *
 * <p>Use the {@link Builder} or the convenience factory {@link #of(int, int)}.
 *
 * @param width        width in texels
 * @param height       height in texels (1 for 1D)
 * @param depth        depth in texels (1 for 1D/2D)
 * @param format       pixel format
 * @param type         dimensionality
 * @param mipLevels    mipmap level count (1 = no mip chain)
 * @param initialBytes optional initial pixel data, or {@code null}
 * @param usage        usage of this texture
 * @see Texture
 */
public record TextureDesc(int width, int height, int depth, TextureFormat format, TextureType type, int mipLevels,
                          int usage, byte @Nullable [] initialBytes) {
  /**
   * Describes a 2D RGBA8 texture with 1 mip level and no initial data.
   *
   * @param width  width in texels
   * @param height height in texels
   * @return a new texture descriptor
   */
  public static TextureDesc of(int width, int height) {
    return new Builder().width(width).height(height).build();
  }

  /**
   * Builder for {@link TextureDesc} with sensible defaults — 1×1 RGBA8 2D texture, 1 mip level, sampled usage, no
   * initial data.
   */
  public static final class Builder {
    public int width = 1;
    public int height = 1;
    public int depth = 1;
    public TextureFormat format = TextureFormat.RGBA8;
    public TextureType type = TextureType.TEXTURE_2D;
    public int mipLevels = 1;
    public int usage = TextureUsage.SAMPLED;
    public byte @Nullable [] initialBytes = null;

    /**
     * Sets the width in texels.
     *
     * @param w width in texels
     * @return this builder
     */
    public Builder width(int w) {
      width = w;
      return this;
    }

    /**
     * Sets the height in texels.
     *
     * @param h height in texels
     * @return this builder
     */
    public Builder height(int h) {
      height = h;
      return this;
    }

    /**
     * Sets the depth in texels.
     *
     * @param d depth in texels (1 for 1D/2D)
     * @return this builder
     */
    public Builder depth(int d) {
      depth = d;
      return this;
    }

    /**
     * Sets the pixel format.
     *
     * @param f the pixel format
     * @return this builder
     */
    public Builder format(TextureFormat f) {
      format = f;
      return this;
    }

    /**
     * Sets the texture dimensionality.
     *
     * @param t the texture type
     * @return this builder
     */
    public Builder type(TextureType t) {
      type = t;
      return this;
    }

    /**
     * Sets the mipmap level count.
     *
     * @param m mipmap level count (1 = no mip chain)
     * @return this builder
     */
    public Builder mipLevels(int m) {
      mipLevels = m;
      return this;
    }

    /**
     * Sets the texture usage flags.
     *
     * @param u bitmask of usage flags
     * @return this builder
     */
    public Builder usage(int u) {
      usage = u;
      return this;
    }

    /**
     * Sets the initial pixel data.
     *
     * @param b the initial pixel data, or {@code null}
     * @return this builder
     */
    public Builder initialBytes(byte @Nullable [] b) {
      initialBytes = b;
      return this;
    }

    /**
     * Builds the texture descriptor.
     *
     * @return a new {@link TextureDesc} with the current builder values
     */
    public TextureDesc build() {
      return new TextureDesc(width, height, depth, format, type, mipLevels, usage, initialBytes);
    }
  }
}
