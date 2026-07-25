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

import net.fmhi.gfx.pipe.PipelineDesc;
import org.jspecify.annotations.Nullable;

/**
 * Baked layout declaring the shader resource bindings (textures, samplers, uniform buffers) expected by a pipeline.
 *
 * <p>Call {@link #bake(Slot...)} to assign binding indices automatically in
 * declaration order.
 *
 * <p>The resulting layout is immutable and is stored in
 * {@link PipelineDesc#resourceLayouts()}.
 *
 * @see PipelineDesc.Builder#resourceLayouts
 */
public final class ResourceSetLayout {
  /**
   * The baked slots in binding order.
   */
  public final Slot[] slots;

  private ResourceSetLayout(Slot[] slots) {
    this.slots = slots;
  }

  /**
   * Bakes a resource-set layout, assigning sequential binding indices starting from 0.
   *
   * @param slots slot declarations in desired binding order
   * @return a baked layout with resolved binding indices
   */
  public static ResourceSetLayout bake(Slot... slots) {
    Slot[] baked = new Slot[slots.length];
    for (int i = 0; i < slots.length; i++) {
      Slot s = slots[i];
      baked[i] = new Slot(s.count(), s.name(), s.stages(), s.type(), i);
    }
    return new ResourceSetLayout(baked);
  }

  /**
   * Returns {@code true} if this layout is compatible with {@code other}.
   *
   * <p>Two layouts are compatible if they have the same number of slots and
   * each slot's {@link Slot#type()} and {@link Slot#binding()} match.
   *
   * @param other other layout to compare
   * @return true if every slot matches
   */
  public boolean matches(@Nullable ResourceSetLayout other) {
    if (other == null) {
      return false;
    }
    if (slots.length != other.slots.length) {
      return false;
    }

    for (int i = 0; i < slots.length; i++) {
      if (slots[i].type() != other.slots[i].type() || slots[i].binding() != other.slots[i].binding()) {
        return false;
      }
    }
    return true;
  }
}
