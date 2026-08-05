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

package net.fmhi.gfx.shader;

import net.fmhi.gfx.buffer.BufferObject;
import net.fmhi.gfx.cmd.Encoder;
import net.fmhi.gfx.texture.Sampler;
import net.fmhi.gfx.texture.Texture;

/**
 * A set of texture/sampler and uniform-buffer bindings for a single draw call.
 *
 * <p>A {@code ResourceSet} collects the shader-visible resources that a
 * draw call needs: textures paired with samplers, and uniform buffer ranges. Each binding is placed at a numbered slot
 * that corresponds to a shader resource declaration.
 *
 * <p>Bindings are recorded lazily on the calling thread and applied in bulk
 * on the render thread when the command executes. Re-binding a slot overwrites the previous binding.
 *
 * <p><b>Thread safety:</b> recording bindings is single-threaded per instance.
 * The set does not own the resources it references — closing a resource set does not close the textures, samplers, or
 * buffers bound to it.
 *
 * @see Encoder#setResource(int, ResourceSet)
 */
public interface ResourceSet extends AutoCloseable {
  /**
   * Returns the layout this resource set was created from.
   *
   * @return the resource set layout
   */
  ResourceSetLayout layout();

  /**
   * Binds a texture and sampler to the given shader slot.
   *
   * <p>The texture provides the image data; the sampler controls how that
   * data is filtered, wrapped, and sampled in the shader.
   *
   * @param slot    the shader binding slot index (0-based)
   * @param texture the texture to bind
   * @param sampler the sampler to use with the texture
   */
  void bindTexture(int slot, Texture texture, Sampler sampler);

  /**
   * Binds a range of a uniform buffer to the given shader slot.
   *
   * <p>Only the specified byte range (from {@code offset} for {@code size}
   * bytes) is visible to the shader. This allows packing multiple uniform blocks into a single buffer.
   *
   * @param slot   the shader binding slot index (0-based)
   * @param buffer the uniform buffer
   * @param size   byte size of the visible range
   * @param offset byte offset into the buffer
   */
  void bindUniform(int slot, BufferObject buffer, int size, int offset);

  /**
   * Binds the entire uniform buffer to the given slot (offset 0).
   *
   * <p>Equivalent to {@link #bindUniform(int, BufferObject, int, int)
   * bindUniform(slot, buffer, size, 0)}.
   *
   * @param slot   the shader binding slot index (0-based)
   * @param buffer the uniform buffer
   * @param size   byte size of the visible range
   */
  default void bindUniform(int slot, BufferObject buffer, int size) {
    bindUniform(slot, buffer, size, 0);
  }

  @Override
  void close();
}
