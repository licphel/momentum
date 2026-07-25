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

package net.fmhi.gfx.opengl;

import net.fmhi.gfx.GraphicsException;
import net.fmhi.gfx.buffer.BufferObject;
import net.fmhi.gfx.shader.ResourceSet;
import net.fmhi.gfx.shader.ResourceSetLayout;
import net.fmhi.gfx.texture.Sampler;
import net.fmhi.gfx.texture.Texture;
import net.fmhi.util.Handle;
import org.jspecify.annotations.Nullable;

/**
 * OpenGL resource set with a unified descriptor slot space.
 *
 * <p>Texture and uniform-buffer bindings share the same slot indices —
 * matching Vulkan's model where all descriptors live in one namespace. Binding to an already-occupied slot replaces the
 * previous binding.
 *
 * <p>Bindings are recorded on the calling thread and applied in bulk on
 * the render thread via {@link #apply(OpenGLCache)}.
 *
 * <p><b>Thread safety:</b> recording is single-threaded per instance.
 * {@link #apply(OpenGLCache)} is called on the render thread.
 */
public final class OpenGLResourceSet implements ResourceSet {
  private static final int MAX_SLOTS = 16;

  private static final byte NONE = 0;
  private static final byte TEXTURE = 1;
  private static final byte UNIFORM = 2;

  private final byte[] types = new byte[MAX_SLOTS];

  // Texture data (only valid when types[i] == TEXTURE)
  private final Handle[] textures = new Handle[MAX_SLOTS];
  private final Handle[] samplers = new Handle[MAX_SLOTS];

  // UBO data (only valid when types[i] == UNIFORM)
  private final Handle[] ubos = new Handle[MAX_SLOTS];
  private final int[] uboSizes = new int[MAX_SLOTS];
  private final int[] uboOffsets = new int[MAX_SLOTS];

  private final ResourceSetLayout layout;
  private int slotCount = 0;

  OpenGLResourceSet(OpenGLDevice ctx, ResourceSetLayout layout) {
    this.layout = layout;
  }

  @Override
  public ResourceSetLayout layout() {
    return layout;
  }

  @Override
  public void bindTexture(int slot, Texture texture, Sampler sampler) {
    types[slot] = TEXTURE;
    textures[slot] = (Handle) texture;
    samplers[slot] = (Handle) sampler;
    if (slot >= slotCount) {
      slotCount = slot + 1;
    }
  }

  @Override
  public void bindUniform(int slot, BufferObject buffer, int size, int offset) {
    types[slot] = UNIFORM;
    ubos[slot] = (OpenGLBufferObject) buffer;
    uboSizes[slot] = size;
    uboOffsets[slot] = offset;
    if (slot >= slotCount) {
      slotCount = slot + 1;
    }
  }

  @Override
  public void close() {
  }

  /**
   * Applies all recorded bindings to the GL state cache.
   *
   * <p>Must be called on the render thread. Iterates slots in order,
   * dispatching based on the recorded descriptor type. Slot indices map directly to GL binding points — no
   * rearrangement needed because the unified namespace matches OpenGL's flat binding model.
   */
  public void apply(OpenGLCache cache) {
    int texUnit = 0;
    int uboPoint = 0;
    for (int i = 0; i < slotCount; i++) {
      switch (types[i]) {
        case TEXTURE -> {
          cache.setTexture(texUnit, textures[i].handle(1), textures[i].handle());
          cache.setSampler(texUnit, samplers[i].handle());
          texUnit++;
        }
        case UNIFORM -> {
          cache.setUniformBuffer(uboPoint, ubos[i].handle(), uboOffsets[i], uboSizes[i]);
          uboPoint++;
        }
      }
    }
  }

  /**
   * Validates this set's layout against the pipeline's layout at the given slot.
   *
   * @throws GraphicsException if the layouts are incompatible
   */
  void validate(@Nullable ResourceSetLayout pipelineLayout) {
    if (!layout.matches(pipelineLayout)) {
      throw new GraphicsException("Resource set layout does not match pipeline layout");
    }
  }
}
