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

/**
 * Texture pixel formats specifying the number of channels, bits depth, and data type.
 *
 * <p>Integer formats (e.g. {@link #RGBA8}) store unsigned normalized bytes.
 * Float formats (e.g. {@link #RGBA16F}) store half-precision or full-precision floating point. Depth formats are used
 * for shadow maps and depth attachments.
 *
 * @see TextureDesc
 */
public enum TextureFormat {
  RED8,
  RG8,
  RGB8,
  RGBA8,
  RED16F,
  RG16F,
  RGB16F,
  RGBA16F,
  RED32F,
  RG32F,
  RGB32F,
  RGBA32F,
  DEPTH16,
  DEPTH24,
  DEPTH32F,
  DEPTH24_STENCIL8
}
