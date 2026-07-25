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

import net.fmhi.gfx.shader.ResourceSet;

/**
 * An immutable GPU sampler object that controls texture filtering, wrapping, and level-of-detail parameters.
 *
 * <p>Samplers are separate from textures: the same texture can be sampled
 * with different samplers (e.g. one draw call with linear filtering, another with nearest). A sampler is created once
 * from a {@link SamplerDesc} and reused across many draw calls.
 *
 * <p><b>Thread safety:</b> immutable after creation — safe to read from any
 * thread.
 *
 * @see SamplerDesc
 * @see ResourceSet#bindTexture(int, Texture, Sampler)
 */
public interface Sampler extends AutoCloseable {
  /**
   * Returns the descriptor that defines this sampler's parameters.
   *
   * @return the immutable sampler descriptor
   */
  SamplerDesc desc();

  @Override
  void close();
}
