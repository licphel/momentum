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

import net.fmhi.fml.resource.ResourceException;
import net.fmhi.gfx.Device;
import net.fmhi.gfx.io.ImageInfo;
import net.fmhi.gfx.io.ImageInputStream;
import net.fmhi.gfx.pass.RenderTarget;
import net.fmhi.gfx.shader.ResourceSet;
import net.fmhi.math.Box3D;
import net.fmhi.util.ResourceProvider;

import java.io.IOException;
import java.io.InputStream;

/**
 * A GPU texture resource holding 1D, 2D, or 3D image data.
 *
 * <p>Textures are created from a {@link TextureDesc} that specifies dimensions,
 * format, mipmap levels, and optional initial pixel data. After creation, sub-regions can be updated with
 * {@link #submit} and the texture can be bound to a shader slot via {@link ResourceSet#bindTexture}.
 *
 * <p><b>Lifecycle:</b>
 * <ol>
 *   <li>Create with {@link Device#getTexture device.createTexture(desc)}.
 *   <li>Optionally upload pixel data with {@link #submit(byte[], Box3D)}.
 *   <li>Bind to a {@link ResourceSet} for use in draw calls.
 *   <li>Use {@link #blit} to copy regions between textures.
 *   <li>Call {@link #close()} when the texture is no longer needed.
 * </ol>
 *
 * <p><b>Formats:</b> see {@link TextureFormat} for the supported internal
 * formats. Pixel data passed to {@link #submit} must match the format declared
 * in the descriptor.
 *
 * <p><b>Thread safety:</b> creation and mutation must happen on the render
 * thread (or be queued to it). Reading {@link #desc()}, {@link #width()},
 * {@link #height()}, and {@link #depth()} is safe from any thread.
 *
 * @see TextureDesc
 * @see TextureFormat
 * @see Sampler
 */
public interface Texture extends FragileTexture, AutoCloseable {
  /**
   * Loads an RGBA8 2D texture from decoded image data.
   *
   * <p>If the image has 3 channels (RGB), an opaque alpha channel is added.
   *
   * @param device the graphics device
   * @param info   the decoded image metadata and pixels
   * @return a new RGBA8 texture
   */
  static Texture loadRGBA8(Device device, ImageInfo info) {
    byte[] rgba = fillAlphaChannel(info);
    TextureDesc desc = new TextureDesc.Builder()
        .width(info.width())
        .height(info.height())
        .format(TextureFormat.RGBA8)
        .initialBytes(rgba)
        .build();
    return device.getTexture(desc);
  }

  private static byte[] fillAlphaChannel(ImageInfo info) {
    if (info.channels() == 4) {
      return info.pixels();
    }
    int w = info.width();
    int h = info.height();
    byte[] src = info.pixels();
    byte[] dst = new byte[w * h * 4];
    int srcChannels = info.channels();
    for (int i = 0, si = 0, di = 0; i < w * h; i++, si += srcChannels, di += 4) {
      dst[di] = src[si];
      dst[di + 1] = src[si + 1];
      dst[di + 2] = src[si + 2];
      dst[di + 3] = (byte) 255;
    }
    return dst;
  }

  /**
   * Returns this texture.
   *
   * @return {@code this}
   */
  @Override
  default Texture pin() {
    return this;
  }

  /**
   * Returns the descriptor that defines this texture's dimensions, format, and type.
   *
   * @return the immutable texture descriptor
   */
  TextureDesc desc();

  /**
   * Returns the texture width in texels.
   *
   * @return width from the descriptor
   */
  default int width() {
    return desc().width();
  }

  /**
   * Returns the texture height in texels.
   *
   * @return height from the descriptor (1 for 1D textures)
   */
  default int height() {
    return desc().height();
  }

  /**
   * Returns the texture depth in texels.
   *
   * @return depth from the descriptor (1 for 1D/2D textures)
   */
  default int depth() {
    return desc().depth();
  }

  /**
   * Uploads pixel data to a sub-region of this texture.
   *
   * <p>The byte array must contain pixel data matching the texture's format
   * and large enough to fill the specified region. The region is clamped to the texture's dimensions.
   *
   * <p>If mipmaps are enabled ({@link TextureDesc#mipLevels()} &gt; 1), they are
   * regenerated after the upload.
   *
   * @param data   raw pixel bytes in the texture's format
   * @param region the target sub-region in texel coordinates (x, y, z, width, height, depth)
   */
  void submit(byte[] data, Box3D region);

  /**
   * Blits (copies) a rectangular region of this texture into another texture.
   *
   * <p>Both textures must be 2D. The copy uses nearest-neighbor filtering;
   * for filtered blits use {@link RenderTarget#blit}.
   *
   * @param target the destination texture
   * @param srcX   left edge of the source region
   * @param srcY   top edge of the source region
   * @param srcW   width of the source region
   * @param srcH   height of the source region
   * @param dstX   left edge of the destination region
   * @param dstY   top edge of the destination region
   * @param dstW   width of the destination region
   * @param dstH   height of the destination region
   */
  void blit(Texture target, int srcX, int srcY, int srcW, int srcH, int dstX, int dstY, int dstW, int dstH);

  @Override
  void close();
}
