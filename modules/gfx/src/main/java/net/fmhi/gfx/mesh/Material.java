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

package net.fmhi.gfx.mesh;

import net.fmhi.gfx.cmd.Encoder;
import net.fmhi.gfx.pipe.Pipeline;
import net.fmhi.gfx.shader.ResourceSet;

/**
 * An immutable pair of a {@link Pipeline} and a {@link ResourceSet}.
 *
 * <p>Applying a material binds its pipeline and resource set to an encoder at the given slot.
 * Closing a material releases the resource set but does not close the pipeline.
 *
 * @param pipeline    the render pipeline
 * @param resourceSet the resource set with bound textures and uniforms
 */
public record Material(Pipeline pipeline, ResourceSet resourceSet) implements AutoCloseable {
  /**
   * Applies this material by binding the pipeline and resource set to the given encoder.
   *
   * @param encoder the encoder to bind to
   * @param slot    the resource binding slot
   */
  public void apply(Encoder encoder, int slot) {
    encoder.setRenderPipe(pipeline);
    encoder.setResource(slot, resourceSet);
  }

  /**
   * Releases the resource set. The pipeline is not closed.
   */
  @Override
  public void close() {
    resourceSet.close();
  }
}
