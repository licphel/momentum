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

package io.viki.momentum.gfx.opengl;

import io.viki.momentum.gfx.GraphicsException;
import io.viki.momentum.gfx.buffer.BufferObject;
import io.viki.momentum.gfx.shader.ResourceSet;
import io.viki.momentum.gfx.shader.ResourceSetLayout;
import io.viki.momentum.gfx.texture.Sampler;
import io.viki.momentum.gfx.texture.Texture;
import io.viki.momentum.util.Handle;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

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
@SuppressWarnings("all")
public final class OpenGLResourceSet implements ResourceSet {
  private static final byte TEXTURE = 1;
  private static final byte UNIFORM = 2;

  private @Nullable ResourceSetLayout layout;
  /** Entries for slots with recorded resource bindings. */
  private ResourceSlot[] slots = new ResourceSlot[2];
  private int slotCount = 0;

  OpenGLResourceSet(ResourceSetLayout layout) {
    this.layout = layout;
  }

  OpenGLResourceSet() {
  }

  @Override
  public ResourceSetLayout layout() {
    return Objects.requireNonNull(layout);
  }

  /**
   * Copies the current bindings into a command-local target.
   *
   * <p>The copy owns no GPU resources. It only copies the backend handles and
   * can therefore be reset and returned to the encoder pool after its batch
   * has executed.
   */
  void copyTo(OpenGLResourceSet target) {
    target.reset();
    target.layout = layout;
    if (target.slots.length < slotCount) {
      target.slots = Arrays.copyOf(target.slots, slotCount);
    }
    target.slotCount = slotCount;
    for (int i = 0; i < slotCount; i++) {
      ResourceSlot source = slots[i];
      ResourceSlot destination = target.slots[i];
      if (destination == null) {
        target.slots[i] = destination = new ResourceSlot(source.index);
      }
      destination.index = source.index;
      destination.type = source.type;
      destination.texture = source.texture;
      destination.sampler = source.sampler;
      destination.ubo = source.ubo;
      destination.uboSize = source.uboSize;
      destination.uboOffset = source.uboOffset;
    }
  }

  /** Clears bindings before this set is returned to the encoder pool. */
  void reset() {
    for (int i = 0; i < slotCount; i++) {
      ResourceSlot slot = slots[i];
      if (slot == null) {
        continue;
      }
      slot.type = 0;
      slot.texture = null;
      slot.sampler = null;
      slot.ubo = null;
      slot.uboSize = 0;
      slot.uboOffset = 0;
    }
    slotCount = 0;
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

  /**
   * Returns the binding entry for a descriptor slot.
   *
   * @param slot the descriptor slot to find or create
   * @return the existing entry, or a new entry when the slot has not been bound
   */
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
   * @param cache the state cache that receives these bindings
   */
  public void apply(OpenGLCache cache) {
    assert layout != null;
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
   * Checks whether this set's layout matches the pipeline's required layout.
   *
   * @param pipelineLayout the required layout, or null when no layout is available
   * @throws GraphicsException if the layouts are incompatible, including when {@code pipelineLayout} is null
   */
  void validate(@Nullable ResourceSetLayout pipelineLayout) {
    assert layout != null;
    if (!layout.matches(pipelineLayout)) {
      throw new GraphicsException("Resource set layout does not match pipeline layout");
    }
  }

  /** Holds the resources bound to one shader-visible slot. */
  private static final class ResourceSlot {
    int index;
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
