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

package net.momentum.gfx.opengl;

import net.momentum.gfx.GraphicsException;
import net.momentum.gfx.buffer.BufferObject;
import net.momentum.gfx.shader.ResourceSet;
import net.momentum.gfx.shader.ResourceSetLayout;
import net.momentum.gfx.texture.Sampler;
import net.momentum.gfx.texture.Texture;
import net.momentum.util.internal.Handle;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

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
  private static final byte TEXTURE = 1;
  private static final byte UNIFORM = 2;

  private final ResourceSetLayout layout;
  /**
   * Sparse list of bound slots, grown on demand. A set typically carries a handful
   * of bindings, so a linear scan on bind and a compact array beat dense
   * fixed-size arrays with mostly-empty entries (a set is created per draw batch).
   */
  private ResourceSlot[] slots = new ResourceSlot[2];
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
    ResourceSlot s = findOrCreate(slot);
    s.type = TEXTURE;
    s.texture = (Handle) texture;
    s.sampler = (Handle) sampler;
  }

  @Override
  public void bindUniform(int slot, BufferObject buffer, int size, int offset) {
    ResourceSlot s = findOrCreate(slot);
    s.type = UNIFORM;
    s.ubo = (OpenGLBufferObject) buffer;
    s.uboSize = size;
    s.uboOffset = offset;
  }

  @Override
  public void close() {
  }

  /** Returns the slot entry, updating an existing one or appending a new one. */
  private ResourceSlot findOrCreate(int slot) {
    for (int i = 0; i < slotCount; i++) {
      if (slots[i].index == slot) {
        return slots[i];
      }
    }
    if (slotCount == slots.length) {
      slots = Arrays.copyOf(slots, slots.length * 2);
    }
    ResourceSlot s = new ResourceSlot(slot);
    slots[slotCount++] = s;
    return s;
  }

  /**
   * Applies all recorded bindings to the GL state cache.
   *
   * @param cache the global cache
   */
  public void apply(OpenGLCache cache) {
    for (int i = 0; i < slotCount; i++) {
      ResourceSlot s = slots[i];
      int binding = layout.slots[s.index].binding();
      switch (s.type) {
        case TEXTURE -> {
          assert s.texture != null;
          assert s.sampler != null;
          cache.setTexture(binding, s.texture.handle(1), s.texture.handle());
          cache.setSampler(binding, s.sampler.handle());
        }
        case UNIFORM -> {
          assert s.ubo != null;
          cache.setUniformBuffer(binding, s.ubo.handle(), s.uboOffset, s.uboSize);
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

  /** One bound slot: the set keeps a sparse list of these instead of dense arrays. */
  private static final class ResourceSlot {
    final int index;
    byte type;
    @Nullable Handle texture;
    @Nullable Handle sampler;
    @Nullable Handle ubo;
    int uboSize;
    int uboOffset;

    ResourceSlot(int index) {
      this.index = index;
    }
  }
}
